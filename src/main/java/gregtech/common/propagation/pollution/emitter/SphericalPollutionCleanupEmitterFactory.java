package gregtech.common.propagation.pollution.emitter;

import gregtech.common.propagation.api.PropagationSource;
import gregtech.common.propagation.definition.BlockGroupKey;
import gregtech.common.propagation.definition.EmitterFactory;
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
