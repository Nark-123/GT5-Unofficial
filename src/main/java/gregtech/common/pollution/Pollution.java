package gregtech.common.pollution;

import static gregtech.api.objects.XSTR.XSTR_INSTANCE;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
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
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
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
import gregtech.api.net.GTPacketPollution;
import gregtech.api.util.GTChunkAssociatedData;
import gregtech.api.util.GTUtility;

// TODO this whole thing should be reworked,
// the global pollution manager should be a
// non static instance in GTProxy
// and all access to it should be non static and via
// GTProxy.proxy.pollutionManager......
public class Pollution {

    private static final Storage STORAGE = new Storage();
    /**
     * Pollution dispersion until effects start: Calculation: ((Limit * 0.01) + 2000) * (4 <- spreading rate)
     * <p>
     * SMOG(500k) 466.7 pollution/sec Poison(750k) 633,3 pollution/sec Dying Plants(1mio) 800 pollution/sec Sour
     * Rain(1.5mio) 1133.3 pollution/sec
     * <p>
     * Pollution producers (pollution/sec) Bronze Boiler(20) Lava Boiler(20) High Pressure Boiler(20) Bronze Blast
     * Furnace(50) Diesel Generator(40/80/160) Gas Turbine(20/40/80) Charcoal Pile(100)
     * <p>
     * Large Diesel Engine(320) Electric Blast Furnace(100) Implosion Compressor(2000) Large Boiler(240) Large Gas
     * Turbine(160) Multi Smelter(100) Pyrolyse Oven(400)
     * <p>
     * Machine Explosion(100,000)
     * <p>
     * Other Random Shit: lots and lots
     * <p>
     * Muffler Hatch Pollution reduction: ** inaccurate ** LV (0%), MV (30%), HV (52%), EV (66%), IV (76%), LuV (84%),
     * ZPM (89%), UV (92%), MAX (95%)
     */
    // chunks left to process in this cycle
    // a global list of all chunks with positive pollution
    private final World world;
    private final PollutionManager propagationManager;
    private static final int SMOG_THRESHOLD = 1;
    private static final double POISON_THRESHOLD = 1.5D;
    private static final double VEGETATION_THRESHOLD = 2.0D;
    private static final double SOUR_RAIN_THRESHOLD = 3;
    private static final double CLIENT_POLLUTION_SCALE = 500_000.0D;

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

    private void syncPlayerPollution() {
        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayerMP)) continue;

            EntityPlayerMP player = (EntityPlayerMP) obj;

            int baseChunkX = (MathHelper.floor_double(player.posX) - 8) >> 4;
            int baseChunkZ = (MathHelper.floor_double(player.posZ) - 8) >> 4;

            syncChunkPollution(player, baseChunkX, baseChunkZ);
            syncChunkPollution(player, baseChunkX + 1, baseChunkZ);
            syncChunkPollution(player, baseChunkX, baseChunkZ + 1);
            syncChunkPollution(player, baseChunkX + 1, baseChunkZ + 1);
        }
    }

    private void syncChunkPollution(EntityPlayerMP player, int chunkX, int chunkZ) {
        double pollution = getPollution(world,(chunkX << 4) + 8, (int) player.posY, (chunkZ << 4) + 8);

        int clientPollution = GTUtility.safeInt(
            Math.round(pollution * CLIENT_POLLUTION_SCALE)
        );

        GTValues.NW.sendToPlayer(
            new GTPacketPollution(
                new ChunkCoordIntPair(chunkX, chunkZ),
                clientPollution
            ),
            player
        );
    }

    public static PollutionManager getPropagationManager(World world) {
        return getPollutionManager(world).propagationManager;
    }

    public static void onWorldTick(TickEvent.WorldTickEvent aEvent) { // called from proxy
        // return if pollution disabled
        if (aEvent.world.isRemote) return;
        if (!GTMod.proxy.mPollution) return;
        if (aEvent.phase == TickEvent.Phase.START) return;

        final Pollution pollutionInstance = GTMod.proxy.dimensionWisePollution.get(aEvent.world.provider.dimensionId);

        if (pollutionInstance == null) return;

        pollutionInstance.propagationManager.tick(
            (int) aEvent.world.getTotalWorldTime()
        );

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

        int x = (int) Math.floor(center.xCoord + (XSTR_INSTANCE.nextDouble() * 2.0D - 1.0D) * range);
        int z = (int) Math.floor(center.zCoord + (XSTR_INSTANCE.nextDouble() * 2.0D - 1.0D) * range);
        int y = world.getHeightValue(x, z) - 1;

        if (y < 0) {
            return;
        }

        double pollution = getPollution(world, x, y, z);

        if (pollution < VEGETATION_THRESHOLD) {
            return;
        }

        damageBlock(world, x, y, z,pollution >= SOUR_RAIN_THRESHOLD);
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
     * Also pollutes cleanroom if {@code te} is an instance of {@link ICleanroomReceiver}.
     *
     * @see #addPollution(World, int, int, int)
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
     * Add some pollution to given chunk. Can pass in negative to remove pollution. Will clamp the final pollution
     * number to 0 if it would be changed into negative.
     *
     * @param w          world to modify. do nothing if it's a client world
     * @param chunkX     chunk coordinate X, i.e. blockX >> 4
     * @param chunkZ     chunk coordinate Z, i.e. blockZ >> 4
     * @param aPollution desired delta. Positive means the pollution in chunk would go higher.
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
     * Get the pollution in specified chunk
     *
     * @param world  world to look in. can be a client world, but that limits the knowledge to what server side send us
     * @param chunkX chunk coordinate X, i.e. blockX >> 4
     * @param chunkZ chunk coordinate Z, i.e. blockZ >> 4
     * @return pollution amount. may be 0 if pollution is disabled, or if it's a client world and server did not send us
     *         info about this chunk
     */
    public static int getPollution(World world, int chunkX, int chunkZ) {
        if (!GTMod.proxy.mPollution) return 0;

        if (world.isRemote) {
            return GTMod.clientProxy().mPollutionRenderer
                .getKnownPollution(chunkX << 4, chunkZ << 4);
        }

        double pollution = getPollution(
            world,
            (chunkX << 4) + 8,
            70,
            (chunkZ << 4) + 8
        );

        return GTUtility.safeInt(
            Math.round(pollution * CLIENT_POLLUTION_SCALE)
        );
    }

    public static int getPollution(World world, int x, int y, int z) {
        return (int) getPropagationManager(world).sample(new BlockPos(x, y, z ));
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

    public static void migrate(ChunkDataEvent.Load e) {
        if (!e.getData().hasKey("GTPOLLUTION")) {
            return;
        }

        int pollution = e.getData().getInteger("GTPOLLUTION");

        e.getData().removeTag("GTPOLLUTION");

        if (pollution > 0) {
            addPollution(e.getChunk(), pollution);
        }
    }

    public static class GT_PollutionEventHandler {

        @SubscribeEvent
        public void chunkWatch(ChunkWatchEvent.Watch event) {
            if (!GTMod.proxy.mPollution) return;

            Pollution pollution =
                GTMod.proxy.dimensionWisePollution.get(event.player.worldObj.provider.dimensionId);

            if (pollution == null) return;

            pollution.syncChunkPollution(
                event.player,
                event.chunk.chunkXPos,
                event.chunk.chunkZPos
            );
        }

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
            STORAGE.loadAll(e.world);
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload e) {
            if (e.world.isRemote) return;
            GTMod.proxy.dimensionWisePollution.remove(e.world.provider.dimensionId);
        }
    }

    @ParametersAreNonnullByDefault
    private static final class Storage extends GTChunkAssociatedData<ChunkData> {

        private Storage() {
            super("Pollution", ChunkData.class, 64, (byte) 0, false);
        }

        @Override
        protected void writeElement(DataOutput output, ChunkData element, World world, int chunkX, int chunkZ)
            throws IOException {
            output.writeInt(element.migrationDirty ? 0 : element.getAmount());
            element.migrationDirty = false;
        }

        @Override
        protected ChunkData readElement(
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
                Pollution.addPollution(world, chunkX, chunkZ, pollution);

                return new ChunkData(0, true);
            }

            return new ChunkData();
        }

        @Override
        protected ChunkData createElement(World world, int chunkX, int chunkZ) {
            return new ChunkData();
        }

        @Override
        public void loadAll(World w) {
            super.loadAll(w);
        }
    }

    private static final class ChunkData implements GTChunkAssociatedData.IData {
        private boolean migrationDirty;
        public int amount;

        private ChunkData() {
            this(0, false);
        }

        private ChunkData(int amount) {
            this(amount, false);
        }

        private ChunkData(int amount, boolean migrationDirty) {
            this.amount = Math.max(0, amount);
            this.migrationDirty = migrationDirty;
        }

        /**
         * Current pollution amount.
         */
        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = Math.max(amount, 0);
        }

        public void changeAmount(int delta) {
            this.amount = Math.max(GTUtility.safeInt(amount + (long) delta, 0), 0);
        }

        @Override
        public boolean isSameAsDefault() {
            return amount == 0;
        }
    }
}
