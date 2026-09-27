package gregtech.common.propagation.debug;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockPollutionTestInfluencer extends BlockContainer {

    public BlockPollutionTestInfluencer() {
        super(Material.iron);

        setBlockName("gt.pollution_test_influencer");
        setBlockTextureName("minecraft:diamond_block");
        setCreativeTab(CreativeTabs.tabBlock);
        setHardness(2.0F);
        setResistance(10.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityPollutionTestInfluencer();
    }
}
