package gregtech.common.propagation.pollution.emitter;

import gregtech.common.propagation.api.PropagationSource;
import gregtech.common.propagation.definition.CellGroupKey;
import gregtech.common.propagation.definition.EmitterFactory;
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
