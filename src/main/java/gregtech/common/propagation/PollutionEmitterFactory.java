package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public final class PollutionEmitterFactory implements EmitterFactory<CellGroupKey, PollutionEmitter> {

    private static final double PROPAGATION_RANGE = 256.0D;

    @Override
    public PollutionEmitter create(
        int dimension,
        CellGroupKey groupKey,
        PropagationSource source,
        Vec3 position) {

        Vec3 cell = Vec3.createVectorHelper(
            groupKey.getX(),
            groupKey.getY(),
            groupKey.getZ());

        return new PollutionEmitter(dimension, cell, PROPAGATION_RANGE);
    }
}
