package gregtech.common.propagation.debug;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;

public final class PollutionDebugBlocks {

    public static Block testEmitter;
    public static Block testInfluencer;

    private PollutionDebugBlocks() {}

    public static void register() {
        testEmitter = new BlockPollutionTestEmitter();
        testInfluencer = new BlockPollutionTestInfluencer();

        GameRegistry.registerBlock(testEmitter, "pollution_test_emitter");

        GameRegistry.registerBlock(testInfluencer, "pollution_test_influencer");

        GameRegistry.registerTileEntity(TileEntityPollutionTestEmitter.class, "GT_PollutionTestEmitter");

        GameRegistry.registerTileEntity(TileEntityPollutionTestInfluencer.class, "GT_PollutionTestInfluencer");
    }
}
