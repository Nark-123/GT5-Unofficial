package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public final class BlockEmitterGrouping
    implements EmitterGrouping<BlockGroupKey> {

    @Override
    public BlockGroupKey getGroupKey(
        PropagationSource source,
        Vec3 position) {

        return new BlockGroupKey(
            getCoordinate(position.xCoord),
            getCoordinate(position.yCoord),
            getCoordinate(position.zCoord));
    }

    private int getCoordinate(double coordinate) {
        double value = Math.floor(coordinate);

        if (value < Integer.MIN_VALUE
            || value > Integer.MAX_VALUE) {

            throw new IllegalArgumentException(
                "Block coordinate out of range: " + coordinate);
        }

        return (int) value;
    }
}
