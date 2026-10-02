package gregtech.common.propagation.pollution.debug;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;

import gregtech.common.propagation.pollution.emitter.BlockPollutionTestCleanupBox;

public final class PollutionDebugBlocks {

    public static Block testEmitter;
    public static Block testInfluencer;
    public static Block testCleanupSphere;
    public static Block testCleanupBox;

    private PollutionDebugBlocks() {}

    public static void register() {
        testEmitter = new BlockPollutionTestEmitter();
        testInfluencer = new BlockPollutionTestInfluencer();

        GameRegistry.registerBlock(testEmitter, "pollution_test_emitter");

        GameRegistry.registerBlock(testInfluencer, "pollution_test_influencer");

        GameRegistry.registerTileEntity(TileEntityPollutionTestEmitter.class, "GT_PollutionTestEmitter");

        GameRegistry.registerTileEntity(TileEntityPollutionTestInfluencer.class, "GT_PollutionTestInfluencer");

        testCleanupSphere = new BlockPollutionTestCleanupSphere();

        testCleanupBox = new BlockPollutionTestCleanupBox();

        GameRegistry.registerBlock(
            testCleanupSphere,
            "pollution_test_cleanup_sphere");

        GameRegistry.registerBlock(
            testCleanupBox,
            "pollution_test_cleanup_box");

        GameRegistry.registerTileEntity(
            TileEntityPollutionTestCleanupSphere.class,
            "GT_PollutionTestCleanupSphere");

        GameRegistry.registerTileEntity(
            TileEntityPollutionTestCleanupBox.class,
            "GT_PollutionTestCleanupBox");
    }
}
