package gregtech.common.pollution;

import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import gregtech.api.net.GTPacketPollutionEmitter;
import gregtech.common.propagation.PollutionBurstSource;
import gregtech.common.propagation.PollutionEmitter;
import gregtech.common.propagation.PollutionManager;
import gregtech.common.propagation.PollutionSavedData;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizon.gtnhlib.capability.Capabilities;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.GTMod;
import gregtech.api.enums.GTValues;
import gregtech.api.hazards.HazardProtection;
import gregtech.api.interfaces.ICleanroom;
import gregtech.api.interfaces.ICleanroomReceiver;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTChunkAssociatedData;
import gregtech.api.util.GTUtility;

public class Pollution {
    // Legacy chunk pollution storage. Used only for one-time migration.
    private static final LegacyStorage LEGACY_STORAGE = new LegacyStorage();
    private final World world;
    private final PollutionManager propagationManager;
    // TODO Rebalance pollution effect thresholds for the propagation model.
    private static final int SMOG_THRESHOLD = 1;
    private static final double POISON_THRESHOLD = 1.5D;
    private static final double VEGETATION_THRESHOLD = 2.0D;
    private static final double SOUR_RAIN_THRESHOLD = 3;
    // Legacy scale used by the chunk-based compatibility API.
    private static final double CLIENT_POLLUTION_SCALE = 500_000.0D;
    private final Set<EntityPlayerMP> emitterSyncedPlayers = new HashSet<>();
    private int fullResyncCursor;
    private int fullResyncAccumulator;

    private static GT_PollutionEventHandler EVENT_HANDLER;

    public Pollution(World world) {
        this.world = world;
        this.propagationManager = new PollutionManager(world.provider.dimensionId);

        if (!world.isRemote) {
            PollutionSavedData.get(world).loadInto(propagationManager);
        }

        if (EVENT_HANDLER == null) {
            EVENT_HANDLER = new GT_PollutionEventHandler();
            MinecraftForge.EVENT_BUS.register(EVENT_HANDLER);
        }
    }

    private void yncPlayerPollution() {
        emitterSyncedPlayers.retainAll(world.playerEntities);

        List<PollutionEmitter> dirtyEmitters = propagationManager.consumeDirtyEmitters();
        Set<BlockPos> removedCells = propagationManager.consumeRemovedEmitterCells();

        int updateCount = dirtyEmitters.size() + removedCells.size();
        GTPacketPollutionEmitter updatePacket = null;

        if (updateCount > 0) {
            int[] cellX = new int[updateCount];
            int[] cellY = new int[updateCount];
            int[] cellZ = new int[updateCount];
            double[] pollution = new double[updateCount];
            int index = 0;

            for (PollutionEmitter emitter : dirtyEmitters) {
                Vec3 cell = emitter.getCellPosition();

                cellX[index] = (int) cell.xCoord;
                cellY[index] = (int) cell.yCoord;
                cellZ[index] = (int) cell.zCoord;
                pollution[index++] = emitter.getPollution();
            }

            for (BlockPos cell : removedCells) {
                cellX[index] = cell.x;
                cellY[index] = cell.y;
                cellZ[index] = cell.z;
                pollution[index++] = 0.0D;
            }

            updatePacket = new GTPacketPollutionEmitter(
                false,
                cellX,
                cellY,
                cellZ,
                pollution
            );
        }

        int syncedCount = 0;

        for (Object obj : world.playerEntities) {
            if (obj instanceof EntityPlayerMP && emitterSyncedPlayers.contains(obj)) {
                syncedCount++;
            }
        }

        int fullResyncCount = 0;

        // Spread periodic full resyncs across players to avoid network spikes.
        if (syncedCount == 0) {
            fullResyncCursor = 0;
            fullResyncAccumulator = 0;
        } else {
            fullResyncAccumulator += syncedCount;
            fullResyncCount = fullResyncAccumulator / 60;
            fullResyncAccumulator %= 60;

            if (fullResyncCursor >= syncedCount) fullResyncCursor = 0;
        }

        GTPacketPollutionEmitter fullPacket = null;
        int syncedIndex = 0;

        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayerMP)) continue;

            EntityPlayerMP player = (EntityPlayerMP) obj;
            boolean newPlayer = emitterSyncedPlayers.add(player);
            boolean fullResync = false;

            if (!newPlayer) {
                if (fullResyncCount > 0) {
                    int distance = syncedIndex - fullResyncCursor;
                    if (distance < 0) distance += syncedCount;

                    fullResync = distance < fullResyncCount;
                }

                syncedIndex++;
            }

            if (newPlayer || fullResync) {
                if (fullPacket == null) fullPacket = createPollutionSnapshotPacket();
                GTValues.NW.sendToPlayer(fullPacket, player);
            } else if (updatePacket != null) {
                GTValues.NW.sendToPlayer(updatePacket, player);
            }
        }

        if (syncedCount > 0) {
            fullResyncCursor = (fullResyncCursor + fullResyncCount) % syncedCount;
        }
    }

    private GTPacketPollutionEmitter createPollutionSnapshotPacket() {
        List<PollutionEmitter> emitters = propagationManager.getEmitters();

        int count = 0;

        for (PollutionEmitter emitter : emitters) {
            if (emitter.getPollution() > 0.0D) count++;
        }

        int[] cellX = new int[count];
        int[] cellY = new int[count];
        int[] cellZ = new int[count];
        double[] pollution = new double[count];
        int index = 0;

        for (PollutionEmitter emitter : emitters) {
            if (emitter.getPollution() <= 0.0D) continue;

            Vec3 cell = emitter.getCellPosition();

            cellX[index] = (int) cell.xCoord;
            cellY[index] = (int) cell.yCoord;
            cellZ[index] = (int) cell.zCoord;
            pollution[index++] = emitter.getPollution();
        }

        return new GTPacketPollutionEmitter(
            true,
            cellX,
            cellY,
            cellZ,
            pollution
        );
    }

    private void syncPlayerPollution() {
        emitterSyncedPlayers.retainAll(world.playerEntities);

        List<PollutionEmitter> dirtyEmitters = propagationManager.consumeDirtyEmitters();
        Set<BlockPos> removedCells = propagationManager.consumeRemovedEmitterCells();

        int updateCount = dirtyEmitters.size() + removedCells.size();
        GTPacketPollutionEmitter updatePacket = null;

        if (updateCount > 0) {
            int[] cellX = new int[updateCount];
            int[] cellY = new int[updateCount];
            int[] cellZ = new int[updateCount];
            double[] pollution = new double[updateCount];
            int index = 0;

            for (PollutionEmitter emitter : dirtyEmitters) {
                Vec3 cell = emitter.getCellPosition();

                cellX[index] = (int) cell.xCoord;
                cellY[index] = (int) cell.yCoord;
                cellZ[index] = (int) cell.zCoord;
                pollution[index++] = emitter.getPollution();
            }

            for (BlockPos cell : removedCells) {
                cellX[index] = cell.x;
                cellY[index] = cell.y;
                cellZ[index] = cell.z;
                pollution[index++] = 0.0D;
            }

            updatePacket = new GTPacketPollutionEmitter(
                false, cellX, cellY, cellZ, pollution
            );
        }

        int syncedCount = 0;

        for (Object obj : world.playerEntities) {
            if (obj instanceof EntityPlayerMP && emitterSyncedPlayers.contains(obj)) {
                syncedCount++;
            }
        }

        int fullResyncCount = 0;

        if (syncedCount == 0) {
            fullResyncCursor = 0;
            fullResyncAccumulator = 0;
        } else {
            fullResyncAccumulator += syncedCount;
            fullResyncCount = fullResyncAccumulator / 60;
            fullResyncAccumulator %= 60;

            if (fullResyncCursor >= syncedCount) fullResyncCursor = 0;
        }

        GTPacketPollutionEmitter fullPacket = null;
        int syncedIndex = 0;

        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayerMP)) continue;

            EntityPlayerMP player = (EntityPlayerMP) obj;
            boolean newPlayer = emitterSyncedPlayers.add(player);
            boolean fullResync = false;

            if (!newPlayer) {
                if (fullResyncCount > 0) {
                    int distance = syncedIndex - fullResyncCursor;
                    if (distance < 0) distance += syncedCount;

                    fullResync = distance < fullResyncCount;
                }

                syncedIndex++;
            }

            if (newPlayer || fullResync) {
                if (fullPacket == null) fullPacket = createPollutionSnapshotPacket();
                GTValues.NW.sendToPlayer(fullPacket, player);
            } else if (updatePacket != null) {
                GTValues.NW.sendToPlayer(updatePacket, player);
            }
        }

        if (syncedCount > 0) {
            fullResyncCursor = (fullResyncCursor + fullResyncCount) % syncedCount;
        }
    }

    public static PollutionManager getPropagationManager(World world) {
        return getPollutionManager(world).propagationManager;
    }

    public static void onWorldTick(TickEvent.WorldTickEvent aEvent) {
        if (!GTMod.proxy.mPollution) return;
        if (aEvent.world.isRemote) return;
        if (aEvent.phase == TickEvent.Phase.START) return;

        Pollution pollutionInstance =
            GTMod.proxy.dimensionWisePollution.get(aEvent.world.provider.dimensionId);

        if (pollutionInstance == null) {
            pollutionInstance = getPollutionManager(aEvent.world);
            LEGACY_STORAGE.migrateAll(aEvent.world);
        }

        pollutionInstance.propagationManager.tick();

        if (aEvent.world.getTotalWorldTime() % 20 == 0) {
            PollutionSavedData.get(aEvent.world).markDirty();
            pollutionInstance.tickVegetation();
            pollutionInstance.syncPlayerPollution();
        }
    }

    public static BlockMatcher standardBlocks;
    public static BlockMatcher liquidBlocks;
    public static BlockMatcher doublePlants;
    public static BlockMatcher crossedSquares;
    public static BlockMatcher blockVine;

    public static void onPostInitClient() {
        if (PollutionConfig.pollution) {
            standardBlocks = new BlockMatcher();
            liquidBlocks = new BlockMatcher();
            doublePlants = new BlockMatcher();
            crossedSquares = new BlockMatcher();
            blockVine = new BlockMatcher();
            standardBlocks.updateClassList(PollutionConfig.renderStandardBlock);
            liquidBlocks.updateClassList(PollutionConfig.renderBlockLiquid);
            doublePlants.updateClassList(PollutionConfig.renderBlockDoublePlant);
            crossedSquares.updateClassList(PollutionConfig.renderCrossedSquares);
            blockVine.updateClassList(PollutionConfig.renderblockVine);
            MinecraftForge.EVENT_BUS.register(standardBlocks);
            MinecraftForge.EVENT_BUS.register(liquidBlocks);
            MinecraftForge.EVENT_BUS.register(doublePlants);
            MinecraftForge.EVENT_BUS.register(crossedSquares);
            MinecraftForge.EVENT_BUS.register(blockVine);
            MinecraftForge.EVENT_BUS.register(new PollutionTooltip());
        }
    }

    private void tickVegetation() {
        List<PollutionEmitter> emitters = propagationManager.getEmitters();

        if (emitters.isEmpty()) {
            return;
        }

        PollutionEmitter emitter = emitters.get(XSTR_INSTANCE.nextInt(emitters.size()));
        Vec3 center = emitter.getPosition();
        double range = emitter.getPropagationRange();

        for (int attempt = 0; attempt < 16; attempt++) {
            int x = (int) Math.floor(center.xCoord + (XSTR_INSTANCE.nextDouble() * 2.0D - 1.0D) * range);
            int z = (int) Math.floor(center.zCoord + (XSTR_INSTANCE.nextDouble() * 2.0D - 1.0D) * range);
            int y = world.getHeightValue(x, z) - 1;

            if (y < 0) {
                continue;
            }

            double pollution = getPollution(world, x, y, z);

            if (pollution < VEGETATION_THRESHOLD) {
                continue;
            }

            damageBlock(world, x, y, z,pollution >= SOUR_RAIN_THRESHOLD);
        }
    }

    private static void damageBlock(World world, int x, int y, int z, boolean sourRain) {
        if (world.isRemote) return;
        Block tBlock = world.getBlock(x, y, z);
        int tMeta = world.getBlockMetadata(x, y, z);
        if (tBlock == Blocks.air || tBlock == Blocks.stone || tBlock == Blocks.sand || tBlock == Blocks.deadbush)
            return;

        if (tBlock == Blocks.leaves || tBlock == Blocks.leaves2 || tBlock.getMaterial() == Material.leaves)
            world.setBlockToAir(x, y, z);
        if (tBlock == Blocks.reeds) {
            tBlock.dropBlockAsItem(world, x, y, z, tMeta, 0);
            world.setBlockToAir(x, y, z);
        }
        if (tBlock == Blocks.tallgrass) world.setBlock(x, y, z, Blocks.deadbush);
        if (tBlock == Blocks.vine) {
            tBlock.dropBlockAsItem(world, x, y, z, tMeta, 0);
            world.setBlockToAir(x, y, z);
        }
        if (tBlock == Blocks.waterlily || tBlock == Blocks.wheat
            || tBlock == Blocks.cactus
            || tBlock.getMaterial() == Material.cactus
            || tBlock == Blocks.melon_block
            || tBlock == Blocks.melon_stem) {
            tBlock.dropBlockAsItem(world, x, y, z, tMeta, 0);
            world.setBlockToAir(x, y, z);
        }
        if (tBlock == Blocks.red_flower || tBlock == Blocks.yellow_flower
            || tBlock == Blocks.carrots
            || tBlock == Blocks.potatoes
            || tBlock == Blocks.pumpkin
            || tBlock == Blocks.pumpkin_stem) {
            tBlock.dropBlockAsItem(world, x, y, z, tMeta, 0);
            world.setBlockToAir(x, y, z);
        }
        if (tBlock == Blocks.sapling || tBlock.getMaterial() == Material.plants)
            world.setBlock(x, y, z, Blocks.deadbush);
        if (tBlock == Blocks.cocoa) {
            tBlock.dropBlockAsItem(world, x, y, z, tMeta, 0);
            world.setBlockToAir(x, y, z);
        }
        if (tBlock == Blocks.mossy_cobblestone) world.setBlock(x, y, z, Blocks.cobblestone);
        if (tBlock == Blocks.grass || tBlock.getMaterial() == Material.grass) world.setBlock(x, y, z, Blocks.dirt);
        if (tBlock == Blocks.farmland || tBlock == Blocks.dirt) {
            world.setBlock(x, y, z, Blocks.sand);
        }

        if (sourRain && world.isRaining()
            && (tBlock == Blocks.gravel || tBlock == Blocks.cobblestone)
            && world.getBlock(x, y + 1, z) == Blocks.air
            && world.canBlockSeeTheSky(x, y, z)) {
            if (tBlock == Blocks.cobblestone) {
                world.setBlock(x, y, z, Blocks.gravel);
            } else {
                world.setBlock(x, y, z, Blocks.sand);
            }
        }
    }

    private static Pollution getPollutionManager(World world) {
        return GTMod.proxy.dimensionWisePollution
            .computeIfAbsent(world.provider.dimensionId, i -> new Pollution(world));
    }

    /** @see #addPollution(TileEntity, int) */
    public static void addPollution(IGregTechTileEntity te, int aPollution) {
        addPollution((TileEntity) te, aPollution);
    }

    /**
     * Applies pollution at the tile position.
     * Positive pollution also pollutes an attached valid cleanroom, if present.
     */
    public static void addPollution(TileEntity te, int aPollution) {
        if (!GTMod.proxy.mPollution || aPollution == 0 || te.getWorldObj().isRemote) {
            return;
        }

        if (aPollution > 0) {
            polluteCleanroom(te);
        }

        World world = te.getWorldObj();

        getPropagationManager(world).registerSource(
            new PollutionBurstSource(
                world.provider.dimensionId,
                Vec3.createVectorHelper(
                    te.xCoord,
                    te.yCoord,
                    te.zCoord
                ),
                aPollution
            )
        );
    }

    /** @see #addPollution(World, int, int, int) */
    public static void addPollution(Chunk ch, int aPollution) {
        addPollution(ch.worldObj, ch.xPosition, ch.zPosition, aPollution);
    }

    /**
     * Legacy chunk-based API. Applies a one-shot pollution change at the chunk center.
     * Negative values reduce existing pollution and are clamped at zero.
     */
    public static void addPollution(World w, int chunkX, int chunkZ, int aPollution) {
        if (!GTMod.proxy.mPollution || aPollution == 0 || w.isRemote) return;

        Vec3 position = Vec3.createVectorHelper(
            (chunkX << 4) + 8,
            70,
            (chunkZ << 4) + 8
        );

        getPropagationManager(w).registerSource(
            new PollutionBurstSource(
                w.provider.dimensionId,
                position,
                aPollution
            )
        );
    }

    /** @see #getPollution(World, int, int) */
    public static int getPollution(IGregTechTileEntity te) {
        return getPollution(te.getWorld(), te.getXCoord() >> 4, te.getZCoord() >> 4);
    }

    /** @see #getPollution(World, int, int) */
    public static int getPollution(Chunk ch) {
        return getPollution(ch.worldObj, ch.xPosition, ch.zPosition);
    }

    public static void polluteCleanroom(TileEntity te) {
        if (!GTMod.proxy.mPollution || te.getWorldObj().isRemote) return;

        ICleanroomReceiver receiver =
            Capabilities.getCapability(te, ICleanroomReceiver.class);

        if (receiver == null) return;

        ICleanroom cleanroom = receiver.getCleanroom();

        if (cleanroom != null && cleanroom.isValidCleanroom()) {
            cleanroom.pollute();
        }
    }

    /**
     * Legacy chunk-based API.
     * Samples pollution at the chunk center at Y=70 and returns it in the legacy integer scale.
     */
    public static int getPollution(World world, int chunkX, int chunkZ) {
        if (!GTMod.proxy.mPollution) return 0;

        double pollution = getPollution(
            world,
            (chunkX << 4) + 8,
            70,
            (chunkZ << 4) + 8
        );

        return GTUtility.safeInt(Math.round(pollution * CLIENT_POLLUTION_SCALE));
    }

    public static double getPollution(World world, int x, int y, int z) {
        if (!GTMod.proxy.mPollution) return 0.0D;

        if (world.isRemote) {
            return GTMod.clientProxy()
                .getClientPollutionManager(world.provider.dimensionId)
                .sample(new BlockPos(x, y, z));
        }

        return getPropagationManager(world).sample(new BlockPos(x, y, z));
    }

    public static boolean hasPollution(Chunk ch) {
        if (!GTMod.proxy.mPollution) return false;

        if (ch.worldObj.isRemote) {
            return getPollution(ch) > 0;
        }

        return getPollution(
            ch.worldObj,
            (ch.xPosition << 4) + 8,
            70,
            (ch.zPosition << 4) + 8
        ) >= 1.0D;
    }

    // Migrates the legacy GTPOLLUTION chunk NBT tag into the propagation system.
    public static void migrate(ChunkDataEvent.Load e) {
        if (!e.getData().hasKey("GTPOLLUTION")) {
            return;
        }

        int pollution = e.getData().getInteger("GTPOLLUTION");

        e.getData().removeTag("GTPOLLUTION");
        e.getChunk().setChunkModified();

        if (pollution > 0) {
            Chunk chunk = e.getChunk();

            getPropagationManager(chunk.worldObj).addPollution(
                (chunk.xPosition << 4) + 8,
                70,
                (chunk.zPosition << 4) + 8,
                pollution
            );
        }
    }

    public static class GT_PollutionEventHandler {

        @SubscribeEvent
        public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
            EntityLivingBase entity = event.entityLiving;
            World world = entity.worldObj;

            if (world.isRemote) return;

            if (!GTMod.proxy.mPollution) return;

            if (entity.ticksExisted % 20 != 0) return;

            double pollution = getPollution(
                world,
                (int) entity.posX,
                (int) entity.posY,
                (int) entity.posZ);

            if (pollution >= SMOG_THRESHOLD) {
                if (entity instanceof EntityPlayerMP
                    && ((EntityPlayerMP) entity).capabilities.isCreativeMode) {
                    return;
                }

                if (HazardProtection.isWearingFullGasHazmat(entity)) {
                    return;
                }

                switch (XSTR_INSTANCE.nextInt(3)) {
                    case 0:
                        entity.addPotionEffect(
                            new PotionEffect(Potion.weakness.id, 40, 0));
                        break;

                    case 1:
                        entity.addPotionEffect(
                            new PotionEffect(Potion.moveSlowdown.id, 40, 0));
                        break;

                    case 2:
                        entity.addPotionEffect(
                            new PotionEffect(Potion.digSlowdown.id, 40, 0));
                        break;
                }
            }

            if (pollution >= POISON_THRESHOLD) {
                switch (XSTR_INSTANCE.nextInt(4)) {
                    case 0:
                        entity.addPotionEffect(
                            new PotionEffect(Potion.confusion.id, 40, 0));
                        break;

                    case 1:
                        entity.addPotionEffect(
                            new PotionEffect(Potion.poison.id, 40, 0));
                        break;

                    case 2:
                        entity.addPotionEffect(
                            new PotionEffect(Potion.blindness.id, 40, 0));
                        break;

                    case 3:
                        entity.addPotionEffect(
                            new PotionEffect(Potion.hunger.id, 40, 0));
                        break;
                }
            }
        }

        @SubscribeEvent
        public void onWorldLoad(WorldEvent.Load e) {
            if (e.world.isRemote) return;

            getPollutionManager(e.world);

            PollutionSavedData data = PollutionSavedData.get(e.world);

            if (!data.isLegacyStorageMigrated()) {
                LEGACY_STORAGE.migrateAll(e.world);
                data.setLegacyStorageMigrated();
            }
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload e) {
            if (e.world.isRemote) return;
            GTMod.proxy.dimensionWisePollution.remove(e.world.provider.dimensionId);
        }
    }

    @ParametersAreNonnullByDefault
    private static final class LegacyStorage extends GTChunkAssociatedData<LegacyChunkData> {

        private LegacyStorage() {
            super("Pollution", LegacyChunkData.class, 64, (byte) 0, false);
        }

        @Override
        protected void writeElement(DataOutput output, LegacyChunkData element,
                                    World world, int chunkX, int chunkZ) throws IOException {
            output.writeInt(0);
        }

        @Override
        protected LegacyChunkData readElement(
            DataInput input,
            int version,
            World world,
            int chunkX,
            int chunkZ
        ) throws IOException {
            if (version != 0) {
                throw new IOException("Region file corrupted");
            }

            int pollution = input.readInt();

            if (pollution > 0) {
                getPropagationManager(world).addPollution(
                    (chunkX << 4) + 8,
                    70,
                    (chunkZ << 4) + 8,
                    pollution
                );
            }

            return new LegacyChunkData();
        }

        @Override
        protected LegacyChunkData createElement(World world, int chunkX, int chunkZ) {
            return new LegacyChunkData();
        }

        public void migrateAll(World w) {
            super.loadAll(w);
        }
    }

    private static final class LegacyChunkData implements GTChunkAssociatedData.IData {

        @Override
        public boolean isSameAsDefault() {
            return true;
        }
    }
}
