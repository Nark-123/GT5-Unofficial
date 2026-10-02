package gregtech.common.propagation.pollution.state;

import gregtech.common.propagation.definition.EmitterStateSnapshot;

public final class SphericalPollutionCleanupState
    implements EmitterStateSnapshot {

    private final int groupX;
    private final int groupY;
    private final int groupZ;

    private final double centerX;
    private final double centerY;
    private final double centerZ;

    private final double range;
    private final double strength;

    public SphericalPollutionCleanupState(
        int groupX,
        int groupY,
        int groupZ,
        double centerX,
        double centerY,
        double centerZ,
        double range,
        double strength) {

        if (!Double.isFinite(centerX)
            || !Double.isFinite(centerY)
            || !Double.isFinite(centerZ)) {

            throw new IllegalArgumentException(
                "Invalid cleanup sphere center");
        }

        if (!Double.isFinite(range)
            || range <= 0.0D) {

            throw new IllegalArgumentException(
                "Invalid cleanup sphere range: "
                    + range);
        }

        if (!Double.isFinite(strength)
            || strength < 0.0D) {

            throw new IllegalArgumentException(
                "Invalid cleanup sphere strength: "
                    + strength);
        }

        this.groupX = groupX;
        this.groupY = groupY;
        this.groupZ = groupZ;

        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;

        this.range = range;
        this.strength = strength;
    }

    public int getGroupX() {
        return groupX;
    }

    public int getGroupY() {
        return groupY;
    }

    public int getGroupZ() {
        return groupZ;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public double getRange() {
        return range;
    }

    public double getStrength() {
        return strength;
    }
}
