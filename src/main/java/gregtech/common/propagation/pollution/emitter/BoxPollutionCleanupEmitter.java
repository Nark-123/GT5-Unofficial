package gregtech.common.propagation.pollution.emitter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

import gregtech.common.propagation.api.PropagationInfluencer;
import gregtech.common.propagation.api.PropagationSource;
import net.minecraft.util.Vec3;

public final class BoxPollutionCleanupEmitter
    implements PollutionFieldEmitter {

    private final int dimension;
    private final Vec3 center;

    private final double halfX;
    private final double halfY;
    private final double halfZ;

    private final double propagationRange;
    private final double volume;

    private final Set<PropagationSource> sources =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private double strength;
    private boolean stateChanged;

    public BoxPollutionCleanupEmitter(
        int dimension,
        Vec3 center,
        double halfX,
        double halfY,
        double halfZ) {

        if (center == null) {
            throw new IllegalArgumentException(
                "Emitter center is null");
        }

        if (!Double.isFinite(halfX) || halfX <= 0.0D
            || !Double.isFinite(halfY) || halfY <= 0.0D
            || !Double.isFinite(halfZ) || halfZ <= 0.0D) {

            throw new IllegalArgumentException(
                "Invalid box dimensions");
        }

        this.dimension = dimension;
        this.center = center;

        this.halfX = halfX;
        this.halfY = halfY;
        this.halfZ = halfZ;

        this.propagationRange = Math.sqrt(
            halfX * halfX
                + halfY * halfY
                + halfZ * halfZ);

        this.volume =
            8.0D * halfX * halfY * halfZ;

        if (!Double.isFinite(propagationRange)
            || !Double.isFinite(volume)) {

            throw new IllegalArgumentException(
                "Box geometry overflow");
        }
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

        double dx =
            Math.abs(position.xCoord - center.xCoord);
        double dy =
            Math.abs(position.yCoord - center.yCoord);
        double dz =
            Math.abs(position.zCoord - center.zCoord);

        if (dx > halfX
            || dy > halfY
            || dz > halfZ) {

            return 0.0D;
        }

        return -strength / volume;
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

    public double getHalfX() {
        return halfX;
    }

    public double getHalfY() {
        return halfY;
    }

    public double getHalfZ() {
        return halfZ;
    }

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
