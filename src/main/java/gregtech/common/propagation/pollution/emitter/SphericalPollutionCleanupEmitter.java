package gregtech.common.propagation.pollution.emitter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

import gregtech.common.propagation.api.PropagationInfluencer;
import gregtech.common.propagation.api.PropagationSource;
import net.minecraft.util.Vec3;

public final class SphericalPollutionCleanupEmitter
    implements PollutionFieldEmitter {

    private static final double GAUSSIAN_3_SIGMA_MASS =
        0.9707091135D;

    private static final double LOG2_E =
        1.4426950408889634D;

    private final int dimension;
    private final Vec3 center;
    private final double propagationRange;
    private final double propagationRangeSquared;
    private final double inverseRangeSquared;
    private final double gaussianNormalization;

    private final Set<PropagationSource> sources =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private double strength;
    private boolean stateChanged;

    public SphericalPollutionCleanupEmitter(
        int dimension,
        Vec3 center,
        double propagationRange) {

        if (center == null) {
            throw new IllegalArgumentException(
                "Emitter center is null");
        }

        if (!Double.isFinite(propagationRange)
            || propagationRange <= 0.0D) {

            throw new IllegalArgumentException(
                "Invalid propagation range: "
                    + propagationRange);
        }

        this.dimension = dimension;
        this.center = center;
        this.propagationRange = propagationRange;
        this.propagationRangeSquared =
            propagationRange * propagationRange;
        this.inverseRangeSquared =
            1.0D / propagationRangeSquared;

        this.gaussianNormalization =
            Math.pow(2.0D * Math.PI, 1.5D)
                * propagationRange
                * propagationRangeSquared
                / 27.0D
                * GAUSSIAN_3_SIGMA_MASS;
    }

    @Override
    public void addSource(PropagationSource source) {
        if (source == null) {
            throw new IllegalArgumentException(
                "Source is null");
        }

        if (source.getDimension() != dimension) {
            throw new IllegalArgumentException(
                "Source dimension mismatch");
        }

        sources.add(source);
    }

    @Override
    public void update() {
        double nextStrength = 0.0D;

        Iterator<PropagationSource> iterator =
            sources.iterator();

        while (iterator.hasNext()) {
            PropagationSource source = iterator.next();

            double sourceStrength =
                source.consumeEmission();

            if (!Double.isFinite(sourceStrength)
                || sourceStrength < 0.0D) {

                throw new IllegalStateException(
                    "Invalid cleanup strength: "
                        + sourceStrength);
            }

            nextStrength += sourceStrength;

            if (!Double.isFinite(nextStrength)) {
                throw new IllegalStateException(
                    "Cleanup strength overflow");
            }

            if (!source.isValid()) {
                iterator.remove();
            }
        }

        if (strength != nextStrength) {
            strength = nextStrength;
            stateChanged = true;
        }
    }

    @Override
    public double getInfluence(Vec3 position) {
        if (strength <= 0.0D) {
            return 0.0D;
        }

        double dx = center.xCoord - position.xCoord;
        double dy = center.yCoord - position.yCoord;
        double dz = center.zCoord - position.zCoord;

        double distanceSquared =
            dx * dx + dy * dy + dz * dz;

        if (distanceSquared > propagationRangeSquared) {
            return 0.0D;
        }

        double influence =
            -strength
                / gaussianNormalization
                * fastExpNeg(
                4.5D
                    * distanceSquared
                    * inverseRangeSquared);

        if (!Double.isFinite(influence)) {
            throw new IllegalStateException(
                "Invalid cleanup influence: "
                    + influence);
        }

        return influence;
    }

    private static double fastExpNeg(double x) {
        double y = x * LOG2_E;

        int n = (int) y;
        double f = y - n;

        double p =
            ((-0.03951000D * f + 0.23059332D) * f
                - 0.69107581D) * f
                + 0.99989849D;

        long scaleBits = (long) (1023 - n) << 52;
        double scale =
            Double.longBitsToDouble(scaleBits);

        return scale * p;
    }

    public void setReplicaStrength(double strength) {
        if (!Double.isFinite(strength)
            || strength < 0.0D) {

            throw new IllegalArgumentException(
                "Invalid cleanup strength: "
                    + strength);
        }

        this.strength = strength;
    }

    @Override
    public boolean isValid() {
        return !sources.isEmpty();
    }

    @Override
    public boolean consumeStateChanged() {
        boolean changed = stateChanged;
        stateChanged = false;
        return changed;
    }

    @Override
    public boolean acceptsInfluencer(
        PropagationInfluencer influencer) {

        return false;
    }

    @Override
    public void addInfluencer(
        PropagationInfluencer influencer) {}

    @Override
    public void removeInfluencer(
        PropagationInfluencer influencer) {}

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public Vec3 getPosition() {
        return center;
    }

    @Override
    public double getPropagationRange() {
        return propagationRange;
    }

    @Override
    public long getPropagationRevision() {
        return 0L;
    }

    public double getStrength() {
        return strength;
    }
}
