package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public final class BoxPollutionCleanupEmitterFactory
    implements EmitterFactory<
    BlockGroupKey,
    BoxPollutionCleanupEmitter> {

    @Override
    public BoxPollutionCleanupEmitter create(
        int dimension,
        BlockGroupKey groupKey,
        PropagationSource source,
        Vec3 position) {

        if (!(source instanceof BoxPollutionCleanupGeometry)) {
            throw new IllegalArgumentException(
                "Box cleanup definition requires box geometry");
        }

        BoxPollutionCleanupGeometry geometry =
            (BoxPollutionCleanupGeometry) source;

        return new BoxPollutionCleanupEmitter(
            dimension,
            geometry.getFieldCenter(),
            geometry.getHalfX(),
            geometry.getHalfY(),
            geometry.getHalfZ());
    }
}
