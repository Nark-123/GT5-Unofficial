package gregtech.common.propagation.pollution.state;

import gregtech.common.propagation.definition.EmitterStateSnapshot;

public final class PollutionEmitterState
    implements EmitterStateSnapshot {

    private final int cellX;
    private final int cellY;
    private final int cellZ;

    private final double pollution;
    private final double propagationRange;

    public PollutionEmitterState(
        int cellX,
        int cellY,
        int cellZ,
        double pollution,
        double propagationRange) {

        if (!Double.isFinite(pollution)
            || pollution < 0.0D) {

            throw new IllegalArgumentException(
                "Invalid pollution: " + pollution);
        }

        if (!Double.isFinite(propagationRange)
            || propagationRange <= 0.0D) {

            throw new IllegalArgumentException(
                "Invalid propagation range: "
                    + propagationRange);
        }

        this.cellX = cellX;
        this.cellY = cellY;
        this.cellZ = cellZ;

        this.pollution = pollution;
        this.propagationRange = propagationRange;
    }

    public int getCellX() {
        return cellX;
    }

    public int getCellY() {
        return cellY;
    }

    public int getCellZ() {
        return cellZ;
    }

    public double getPollution() {
        return pollution;
    }

    public double getPropagationRange() {
        return propagationRange;
    }
}
