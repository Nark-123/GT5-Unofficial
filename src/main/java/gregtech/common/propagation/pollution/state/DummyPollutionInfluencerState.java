package gregtech.common.propagation.pollution.state;

import gregtech.common.propagation.definition.InfluencerStateSnapshot;

public final class DummyPollutionInfluencerState
    implements InfluencerStateSnapshot {

    private final double x;
    private final double y;
    private final double z;

    public DummyPollutionInfluencerState(
        double x,
        double y,
        double z) {

        if (!Double.isFinite(x)
            || !Double.isFinite(y)
            || !Double.isFinite(z)) {

            throw new IllegalArgumentException(
                "Invalid influencer position");
        }

        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }
}
