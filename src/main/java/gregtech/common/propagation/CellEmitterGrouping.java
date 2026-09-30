package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public final class CellEmitterGrouping implements EmitterGrouping<CellGroupKey> {

    private static final double CELL_SIZE = 16.0D;

    @Override
    public CellGroupKey getGroupKey(PropagationSource source, Vec3 position) {
        return new CellGroupKey(
            getCellCoordinate(position.xCoord),
            getCellCoordinate(position.yCoord),
            getCellCoordinate(position.zCoord));
    }

    private int getCellCoordinate(double coordinate) {
        double cell = Math.floor(coordinate / CELL_SIZE);

        if (cell < Integer.MIN_VALUE || cell > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Cell coordinate out of range: " + coordinate);
        }

        return (int) cell;
    }
}
