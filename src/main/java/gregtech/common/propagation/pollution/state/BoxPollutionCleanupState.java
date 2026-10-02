package gregtech.common.propagation.pollution.state;

import gregtech.common.propagation.definition.EmitterStateSnapshot;

public final class BoxPollutionCleanupState
    implements EmitterStateSnapshot {

    private final int groupX;
    private final int groupY;
    private final int groupZ;

    private final double centerX;
    private final double centerY;
    private final double centerZ;

    private final double halfX;
    private final double halfY;
    private final double halfZ;

    private final double strength;

    public BoxPollutionCleanupState(
        int groupX,
        int groupY,
        int groupZ,
        double centerX,
        double centerY,
        double centerZ,
        double halfX,
        double halfY,
        double halfZ,
        double strength) {

        if (!Double.isFinite(centerX)
            || !Double.isFinite(centerY)
            || !Double.isFinite(centerZ)) {

            throw new IllegalArgumentException(
                "Invalid cleanup box center");
        }

        if (!Double.isFinite(halfX)
            || halfX <= 0.0D
            || !Double.isFinite(halfY)
            || halfY <= 0.0D
            || !Double.isFinite(halfZ)
            || halfZ <= 0.0D) {

            throw new IllegalArgumentException(
                "Invalid cleanup box half extents");
        }

        if (!Double.isFinite(strength)
            || strength < 0.0D) {

            throw new IllegalArgumentException(
                "Invalid cleanup box strength: "
                    + strength);
        }

        this.groupX = groupX;
        this.groupY = groupY;
        this.groupZ = groupZ;

        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;

        this.halfX = halfX;
        this.halfY = halfY;
        this.halfZ = halfZ;

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

    public double getHalfX() {
        return halfX;
    }

    public double getHalfY() {
        return halfY;
    }

    public double getHalfZ() {
        return halfZ;
    }

    public double getStrength() {
        return strength;
    }
}
