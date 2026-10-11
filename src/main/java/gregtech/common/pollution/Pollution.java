package gregtech.common.pollution;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizon.gtnhlib.capability.Capabilities;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.GTMod;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.ICleanroom;
import gregtech.api.interfaces.ICleanroomReceiver;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.net.GTPacketPollution;
import gregtech.api.pollution.IPollutionEngine;
import gregtech.api.pollution.PollutionEngineRegistry;

public class Pollution {
    private static final int POLLUTIONPACKET_MINVALUE = 1000;
    private static GT_PollutionEventHandler EVENT_HANDLER;

    private final IPollutionEngine engine;
    private final PollutionEffects effects = new PollutionEffects();

    public static int mPlayerPollution;
    public static BlockMatcher standardBlocks;
    public static BlockMatcher liquidBlocks;
    public static BlockMatcher doublePlants;
    public static BlockMatcher crossedSquares;
    public static BlockMatcher blockVine;

    public Pollution(World world) {
        this.engine = PollutionEngineRegistry.create(world, PollutionEngineRegistry.activeEngineId(), NoopPollutionEngine::new);

        if (EVENT_HANDLER == null) {
            EVENT_HANDLER = new GT_PollutionEventHandler();
            MinecraftForge.EVENT_BUS.register(EVENT_HANDLER);
        }
    }

    private static Pollution getPollutionManager(World world) {
        return GTMod.proxy.dimensionWisePollution.computeIfAbsent(world.provider.dimensionId, i -> new Pollution(world));
    }

    private static AxisAlignedBB chunkArea(int chunkX, int chunkZ) {
        int x = chunkX << 4;
        int z = chunkZ << 4;
        return AxisAlignedBB.getBoundingBox(x, 0, z, x + 16, 256, z + 16);
    }

    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!GTMod.proxy.mPollution || event.world.isRemote || event.phase != TickEvent.Phase.END) return;

        Pollution pollution = getPollutionManager(event.world);
        pollution.engine.tick();

        if (event.world.getTotalWorldTime() % 20 == 0 && !"none".equals(pollution.engine.id())) {
            pollution.effects.tick(event.world, pollution.engine);
        }
    }

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

    public static void addPollution(IGregTechTileEntity te, int amount) {
        addPollution((TileEntity) te, amount);
    }

    public static void addPollution(TileEntity te, int amount) {
        World world = te.getWorldObj();
        if (!GTMod.proxy.mPollution || amount == 0 || world.isRemote) return;

        if (amount > 0) {
            ICleanroomReceiver receiver = Capabilities.getCapability(te, ICleanroomReceiver.class);
            if (receiver != null) {
                ICleanroom cleanroom = receiver.getCleanroom();
                if (cleanroom != null && cleanroom.isValidCleanroom()) cleanroom.pollute();
            }

            getPollutionManager(world).engine.change(te.xCoord, te.yCoord, te.zCoord, amount);
        } else {
            getPollutionManager(world).engine.change(te.xCoord, te.yCoord, te.zCoord, amount);
        }
    }

    public static void addPollution(Chunk chunk, int amount) {
        addPollution(chunk.worldObj, chunk.xPosition, chunk.zPosition, amount);
    }

    public static void addPollution(World world, int chunkX, int chunkZ, int amount) {
        if (!GTMod.proxy.mPollution || amount == 0 || world.isRemote) return;

        IPollutionEngine engine = getPollutionManager(world).engine;

        if (amount > 0) {
            if (!world.getChunkProvider().chunkExists(chunkX, chunkZ)) return;
            int x = (chunkX << 4) + 8;
            int z = (chunkZ << 4) + 8;
            engine.change(x, world.getHeightValue(x, z), z, amount);
        } else {
            int x = (chunkX << 4) + 8;
            int z = (chunkZ << 4) + 8;
            engine.change(x, world.getHeightValue(x, z), z, amount);;
        }
    }

    public static int getPollution(IGregTechTileEntity te) {
        return getPollution(te.getWorld(), te.getXCoord() >> 4, te.getZCoord() >> 4);
    }

    public static int getPollution(Chunk chunk) {
        return getPollution(chunk.worldObj, chunk.xPosition, chunk.zPosition);
    }

    public static int getPollution(World world, int chunkX, int chunkZ) {
        if (!GTMod.proxy.mPollution) return 0;
        if (world.isRemote) {
            return GTMod.clientProxy().mPollutionRenderer.getKnownPollution(chunkX << 4, chunkZ << 4);
        }

        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;

        return getPollutionManager(world).engine.get(x, Math.min(255, world.getHeightValue(x, z)), z);
    }

    public static boolean hasPollution(Chunk chunk) {
        return GTMod.proxy.mPollution && getPollution(chunk) > 0;
    }

    public static void migrate(ChunkDataEvent.Load event) {}

    public static class GT_PollutionEventHandler {

        @SubscribeEvent
        public void chunkWatch(ChunkWatchEvent.Watch event) {
            if (!GTMod.proxy.mPollution) return;

            int amount = getPollution(event.player.worldObj, event.chunk.chunkXPos, event.chunk.chunkZPos);
            if (amount > POLLUTIONPACKET_MINVALUE) {
                GTValues.NW.sendToPlayer(new GTPacketPollution(event.chunk, amount), event.player);
            }
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload event) {
            if (event.world.isRemote) return;

            Pollution pollution = GTMod.proxy.dimensionWisePollution.remove(event.world.provider.dimensionId);
            if (pollution != null) pollution.engine.close();
        }
    }
}
