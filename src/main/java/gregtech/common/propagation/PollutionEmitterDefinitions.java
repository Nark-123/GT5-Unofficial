package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public final class PollutionEmitterDefinitions {

    public static final String STANDARD_ID = "pollution";

    private PollutionEmitterDefinitions() {}

    public static EmitterDefinition<CellGroupKey, PollutionEmitter> standard() {
        return new EmitterDefinition<>(
            STANDARD_ID,
            new CellEmitterGrouping(),
            new PollutionEmitterFactory());
    }
}
