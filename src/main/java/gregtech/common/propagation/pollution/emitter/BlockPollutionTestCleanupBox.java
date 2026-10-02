package gregtech.common.propagation.pollution.emitter;

import gregtech.common.propagation.pollution.debug.TileEntityPollutionTestCleanupBox;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class BlockPollutionTestCleanupBox
    extends BlockContainer {

    public BlockPollutionTestCleanupBox() {
        super(Material.iron);

        setBlockName("gt.pollution_test_cleanup_box");
        setBlockTextureName("minecraft:gold_block");
        setHardness(2.0F);
        setResistance(10.0F);
    }

    @Override
    public TileEntity createNewTileEntity(
        World world,
        int meta) {

        return new TileEntityPollutionTestCleanupBox();
    }
}
