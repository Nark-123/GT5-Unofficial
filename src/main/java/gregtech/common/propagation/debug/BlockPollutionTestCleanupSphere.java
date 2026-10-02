package gregtech.common.propagation.debug;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class BlockPollutionTestCleanupSphere
    extends BlockContainer {

    public BlockPollutionTestCleanupSphere() {
        super(Material.iron);

        setBlockName("gt.pollution_test_cleanup_sphere");
        setBlockTextureName("minecraft:redstone_block");
        setHardness(2.0F);
        setResistance(10.0F);
    }

    @Override
    public TileEntity createNewTileEntity(
        World world,
        int meta) {

        return new TileEntityPollutionTestCleanupSphere();
    }
}
