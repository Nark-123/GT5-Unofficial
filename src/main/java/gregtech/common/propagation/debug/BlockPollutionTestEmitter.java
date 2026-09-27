package gregtech.common.propagation.debug;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockPollutionTestEmitter extends BlockContainer {

    public BlockPollutionTestEmitter() {
        super(Material.iron);

        setBlockName("gt.pollution_test_emitter");
        setBlockTextureName("minecraft:iron_block");
        setHardness(2.0F);
        setResistance(10.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityPollutionTestEmitter();
    }
}
