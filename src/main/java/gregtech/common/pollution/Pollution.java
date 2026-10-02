package gregtech.common.pollution;

import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

import gregtech.api.net.GTPacketPollutionState;
import gregtech.common.propagation.runtime.EmitterRuntimeChange;
import gregtech.common.propagation.runtime.InfluencerRuntimeChange;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.gtnhlib.capability.Capabilities;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.GTMod;
import gregtech.api.enums.GTValues;
import gregtech.api.hazards.HazardProtection;
import gregtech.api.interfaces.ICleanroom;
import gregtech.api.interfaces.ICleanroomReceiver;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.net.GTPacketPollutionEmitter;
import gregtech.api.net.GTPacketPollutionQueryProfile;
import gregtech.api.util.GTChunkAssociatedData;
import gregtech.api.util.GTUtility;
import gregtech.common.propagation.pollution.emitter.PollutionEmitter;
import gregtech.common.propagation.pollution.PollutionManager;
import gregtech.common.propagation.debug.PollutionQueryProfiler;
import gregtech.common.propagation.pollution.persistence.PollutionSavedData;

public class Pollution {

    // Legacy chunk pollution storage. Used only for one-time migration.
    private static final LegacyStorage LEGACY_STORAGE = new LegacyStorage();
    private final World world;
    private final PollutionManager propagationManager;
    // Legacy scale used by the chunk-based compatibility API.
    private static final double LEGACY_POLLUTION_SCALE = 500_000.0D;
    private final Set<EntityPlayerMP> emitterSyncedPlayers = new HashSet<>();

    private static final int QUERY_PROFILE_SYNC_INTERVAL = 10;

    private static final Map<World, PollutionManager> PROPAGATION_MANAGERS =
        Collections.synchronizedMap(new IdentityHashMap<>());

    @Deprecated
    public static int mPlayerPollution;

    private static GT_PollutionEventHandler EVENT_HANDLER;

    public Pollution(World world) {
        this.world = world;

        this.propagationManager = getPropagationManager(world);

        if (EVENT_HANDLER == null) {
            EVENT_HANDLER = new GT_PollutionEventHandler();

            MinecraftForge.EVENT_BUS.register(EVENT_HANDLER);
        }

        if (!world.isRemote) {
            propagationManager.setQueryProfilingEnabled(true);
        }
    }

    private void syncQueryProfile(
        boolean includeParity) {

        PollutionQueryProfiler.Snapshot snapshot =
            propagationManager
                .consumeQueryProfile();

        int emitterCount =
            propagationManager
                .getEmitterCount();

        int influencerCount =
            propagationManager
                .getInfluencerCount();

        GTPacketPollutionQueryProfile
            profileOnlyPacket =
            includeParity
                ? null
                : new GTPacketPollutionQueryProfile(
                QUERY_PROFILE_SYNC_INTERVAL,
                snapshot,
                emitterCount,
                influencerCount);

        for (Object obj :
            world.playerEntities) {

            if (!(obj instanceof EntityPlayerMP)) {
                continue;
            }

            EntityPlayerMP player =
                (EntityPlayerMP) obj;

            if (!includeParity) {
                GTValues.NW.sendToPlayer(
                    profileOnlyPacket,
                    player);

                continue;
            }

            int x = MathHelper.floor_double(player.posX);

            int y = MathHelper.floor_double(player.posY);

            int z = MathHelper.floor_double(player.posZ);

            BlockPos pos = new BlockPos(x, y, z);

            double serverSample =
                propagationManager
                    .sampleUnprofiledForDebug(
                        pos);

            double serverReference =
                propagationManager
                    .sampleReference(
                        pos);

            GTPacketPollutionQueryProfile packet =
                new GTPacketPollutionQueryProfile(
                    QUERY_PROFILE_SYNC_INTERVAL,
                    snapshot,
                    emitterCount,
                    influencerCount,
                    x,
                    y,
                    z,
                    serverSample,
                    serverReference);

            GTValues.NW.sendToPlayer(
                packet,
                player);
        }
    }

    private GTPacketPollutionState
    createPollutionSnapshotPacket() {

        return GTPacketPollutionState.full(
            propagationManager
                .captureEmitterFullSnapshot(),
            propagationManager
                .captureInfluencerFullSnapshot());
    }

    private void syncPlayerPollution() {
        emitterSyncedPlayers.retainAll(
            world.playerEntities);

        List<EmitterRuntimeChange>
            emitterChanges =
            propagationManager
                .consumeEmitterRuntimeChanges();

        List<InfluencerRuntimeChange>
            influencerChanges =
            propagationManager
                .consumeInfluencerRuntimeChanges();

        boolean hasChanges =
            !emitterChanges.isEmpty()
                || !influencerChanges.isEmpty();

        GTPacketPollutionState deltaPacket =
            hasChanges
                ? GTPacketPollutionState.delta(
                emitterChanges,
                influencerChanges)
                : null;

        GTPacketPollutionState fullPacket =
            null;

        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayerMP)) {
                continue;
            }

            EntityPlayerMP player =
                (EntityPlayerMP) obj;

            boolean newPlayer =
                emitterSyncedPlayers.add(
                    player);

            if (newPlayer) {
                if (fullPacket == null) {
                    fullPacket =
                        createPollutionSnapshotPacket();
                }

                GTValues.NW.sendToPlayer(
                    fullPacket,
                    player);

                continue;
            }

            if (deltaPacket != null) {
                GTValues.NW.sendToPlayer(
                    deltaPacket,
                    player);
            }
        }
    }

    public static PollutionManager getPropagationManager(
        World world) {

        if (world == null) {
            throw new IllegalArgumentException(
                "World is null");
        }

        synchronized (PROPAGATION_MANAGERS) {
            PollutionManager existing =
                PROPAGATION_MANAGERS.get(world);

            if (existing != null) {
                return existing;
            }

            int dimension =
                world.provider.dimensionId;

            PollutionManager manager =
                world.isRemote
                    ? PollutionManager.createReplica(
                    dimension)
                    : new PollutionManager(
                    dimension);

            /*
             * Publish before initialization so a re-entrant lookup
             * for the same World resolves to this same instance.
             */
            PROPAGATION_MANAGERS.put(
                world,
                manager);

            try {
                if (!world.isRemote) {
                    PollutionSavedData.get(world)
                        .loadInto(manager);
                }

                return manager;

            } catch (RuntimeException e) {
                /*
                 * Do not leave a partially initialized manager
                 * in the world registry.
                 */
                if (PROPAGATION_MANAGERS.get(world)
                    == manager) {

                    PROPAGATION_MANAGERS.remove(
                        world);
                }

                throw e;
            }
        }
    }

    public static void onWorldTick(TickEvent.WorldTickEvent aEvent) {
        if (!GTMod.proxy.mPollution) return;
        if (aEvent.world.isRemote) return;
        if (aEvent.phase == TickEvent.Phase.START) return;

        Pollution pollutionInstance = GTMod.proxy.dimensionWisePollution.get(aEvent.world.provider.dimensionId);

        if (pollutionInstance == null) {
            pollutionInstance = getPollutionManager(aEvent.world);
            migrateLegacyStorage(aEvent.world);
        }

        pollutionInstance.propagationManager.tick();

        long worldTime = aEvent.world.getTotalWorldTime();

        boolean replicationTick = worldTime % QUERY_PROFILE_SYNC_INTERVAL == 0L;

        if (replicationTick) {
            PollutionSavedData.get(aEvent.world).markDirty();
            pollutionInstance.tickVegetation();
            pollutionInstance.syncPlayerPollution();
        }

        if (worldTime % QUERY_PROFILE_SYNC_INTERVAL == 0L) {
            pollutionInstance.syncQueryProfile(replicationTick);
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
        List<PollutionEmitter> emitters = propagationManager.getStandardEmitters();

        if (emitters.isEmpty()) {
            return;
        }

        PollutionEmitter emitter = emitters.get(XSTR_INSTANCE.nextInt(emitters.size()));
        Vec3 center = emitter.getPosition();
        double range = emitter.getPropagationRange();

        double vegetationThreshold =
            fromLegacyPollution(GTMod.proxy.mPollutionVegetationLimit);

        double sourRainThreshold =
            fromLegacyPollution(GTMod.proxy.mPollutionSourRainLimit);

        for (int attempt = 0; attempt < 16; attempt++) {
            int x = (int) Math.floor(center.xCoord + (XSTR_INSTANCE.nextDouble() * 2.0D - 1.0D) * range);
            int z = (int) Math.floor(center.zCoord + (XSTR_INSTANCE.nextDouble() * 2.0D - 1.0D) * range);
            int y = world.getHeightValue(x, z) - 1;

            if (y < 0) {
                continue;
            }

            double pollution = getLegacyPollution(world, x, y, z);

            if (pollution <= vegetationThreshold) {
                continue;
            }

            damageBlock(world, x, y, z, pollution > sourRainThreshold);
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

        getPropagationManager(world)
            .addPollution(
                te.xCoord,
                te.yCoord,
                te.zCoord,
                fromLegacyPollution(aPollution));
    }

    /** @see #addPollution(World, int, int, int) */
    public static void addPollution(Chunk ch, int aPollution) {
        addPollution(ch.worldObj, ch.xPosition, ch.zPosition, aPollution);
    }

    /**
     * Legacy chunk-based API. Applies a one-shot pollution change at the chunk center.
     * Negative values reduce existing pollution and are clamped at zero.
     */
    public static void addPollution(
        World world,
        int chunkX,
        int chunkZ,
        int pollution) {

        if (!GTMod.proxy.mPollution || pollution == 0 || world.isRemote) {
            return;
        }

        getPropagationManager(world)
            .addPollution(
                (chunkX << 4) + 8,
                70,
                (chunkZ << 4) + 8,
                fromLegacyPollution(pollution));
    }

    /** @see #getLegacyPollution(World, int, int) */
    @Deprecated
    public static int getLegacyPollution(IGregTechTileEntity te) {
        return getLegacyPollution(te.getWorld(), te.getXCoord() >> 4, te.getZCoord() >> 4);
    }

    /** @see #getLegacyPollution(World, int, int) */
    @Deprecated
    public static int getLegacyPollution(Chunk ch) {
        return getLegacyPollution(ch.worldObj, ch.xPosition, ch.zPosition);
    }

    public static void polluteCleanroom(TileEntity te) {
        if (!GTMod.proxy.mPollution || te.getWorldObj().isRemote) return;

        ICleanroomReceiver receiver = Capabilities.getCapability(te, ICleanroomReceiver.class);

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
    @Deprecated
    public static int getLegacyPollution(World world, int chunkX, int chunkZ) {
        if (!GTMod.proxy.mPollution) return 0;

        double pollution = getLegacyPollution(world, (chunkX << 4) + 8, 70, (chunkZ << 4) + 8);

        return toLegacyPollution(pollution);
    }

    private static void migrateLegacyStorage(World world) {
        PollutionSavedData data = PollutionSavedData.get(world);
        if (data.isLegacyStorageMigrated()) return;

        LEGACY_STORAGE.migrateAll(world);
        data.setLegacyStorageMigrated();
    }

    public static double fromLegacyPollution(int pollution) {
        return pollution / LEGACY_POLLUTION_SCALE;
    }

    private static int toLegacyPollution(double pollution) {
        return GTUtility.safeInt(Math.round(pollution * LEGACY_POLLUTION_SCALE));
    }

    public static double getLegacyPollution(World world, int x, int y, int z) {

        if (!GTMod.proxy.mPollution) {
            return 0.0D;
        }

        return getPropagationManager(world)
            .sample(new BlockPos(x, y, z));
    }

    public static boolean hasPollution(Chunk ch) {
        if (!GTMod.proxy.mPollution) return false;
        return getLegacyPollution(ch) > 0;
    }

    // Migrates the legacy GTPOLLUTION chunk NBT tag into the propagation system.
    public static void migrate(ChunkDataEvent.Load e) {
        if (!e.getData()
            .hasKey("GTPOLLUTION")) {
            return;
        }

        int pollution = e.getData()
            .getInteger("GTPOLLUTION");

        e.getData()
            .removeTag("GTPOLLUTION");
        e.getChunk()
            .setChunkModified();

        if (pollution > 0) {
            Chunk chunk = e.getChunk();

            getPropagationManager(chunk.worldObj)
                .addPollution(
                    (chunk.xPosition << 4) + 8,
                    70,
                    (chunk.zPosition << 4) + 8,
                    fromLegacyPollution(pollution));
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

            double pollution = getLegacyPollution(world, (int) entity.posX, (int) entity.posY, (int) entity.posZ);

            double smogThreshold =
                fromLegacyPollution(GTMod.proxy.mPollutionSmogLimit);

            double poisonThreshold =
                fromLegacyPollution(GTMod.proxy.mPollutionPoisonLimit);

            if (pollution > smogThreshold) {
                if (entity instanceof EntityPlayerMP && ((EntityPlayerMP) entity).capabilities.isCreativeMode) {
                    return;
                }

                if (HazardProtection.isWearingFullGasHazmat(entity)) {
                    return;
                }

                switch (XSTR_INSTANCE.nextInt(3)) {
                    case 0:
                        entity.addPotionEffect(new PotionEffect(Potion.weakness.id, 40, 0));
                        break;

                    case 1:
                        entity.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 0));
                        break;

                    case 2:
                        entity.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, 40, 0));
                        break;
                }
            }

            if (pollution > poisonThreshold) {
                switch (XSTR_INSTANCE.nextInt(4)) {
                    case 0:
                        entity.addPotionEffect(new PotionEffect(Potion.confusion.id, 40, 0));
                        break;

                    case 1:
                        entity.addPotionEffect(new PotionEffect(Potion.poison.id, 40, 0));
                        break;

                    case 2:
                        entity.addPotionEffect(new PotionEffect(Potion.blindness.id, 40, 0));
                        break;

                    case 3:
                        entity.addPotionEffect(new PotionEffect(Potion.hunger.id, 40, 0));
                        break;
                }
            }
        }

        @SubscribeEvent
        public void onWorldLoad(WorldEvent.Load e) {
            if (e.world.isRemote) return;

            getPollutionManager(e.world);
            migrateLegacyStorage(e.world);
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
        protected void writeElement(DataOutput output, LegacyChunkData element, World world, int chunkX, int chunkZ)
            throws IOException {
            output.writeInt(0);
        }

        @Override
        protected LegacyChunkData readElement(DataInput input, int version, World world, int chunkX, int chunkZ)
            throws IOException {
            if (version != 0) {
                throw new IOException("Region file corrupted");
            }

            int pollution = input.readInt();

            if (pollution <= 0) {
                return new LegacyChunkData();
            }

            getPropagationManager(world)
                .addPollution(
                    (chunkX << 4) + 8,
                    70,
                    (chunkZ << 4) + 8,
                    fromLegacyPollution(pollution));

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
