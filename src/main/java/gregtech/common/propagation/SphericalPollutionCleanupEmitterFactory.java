package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public final class SphericalPollutionCleanupEmitterFactory
    implements EmitterFactory<
    BlockGroupKey,
    SphericalPollutionCleanupEmitter> {

    private static final double PROPAGATION_RANGE =
        32.0D;

    @Override
    public SphericalPollutionCleanupEmitter create(
        int dimension,
        BlockGroupKey groupKey,
        PropagationSource source,
        Vec3 position) {

        Vec3 center = Vec3.createVectorHelper(
            groupKey.getX() + 0.5D,
            groupKey.getY() + 0.5D,
            groupKey.getZ() + 0.5D);

        return new SphericalPollutionCleanupEmitter(
            dimension,
            center,
            PROPAGATION_RANGE);
    }
}
