package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public final class PollutionEmitterDefinitions {

    public static final String STANDARD_ID =
        "pollution";

    public static final String CLEANUP_SPHERE_ID =
        "pollution_cleanup_sphere";

    public static final String CLEANUP_BOX_ID =
        "pollution_cleanup_box";

    private PollutionEmitterDefinitions() {}

    public static EmitterDefinition<CellGroupKey, PollutionEmitter> standard() {
        return new EmitterDefinition<>(
            STANDARD_ID,
            new CellEmitterGrouping(),
            new PollutionEmitterFactory());
    }

    public static EmitterDefinition<BlockGroupKey, SphericalPollutionCleanupEmitter> cleanupSphere() {
        return new EmitterDefinition<>(
            CLEANUP_SPHERE_ID,
            new BlockEmitterGrouping(),
            new SphericalPollutionCleanupEmitterFactory());
    }

    public static EmitterDefinition<BlockGroupKey, BoxPollutionCleanupEmitter> cleanupBox() {

        return new EmitterDefinition<>(
            CLEANUP_BOX_ID,
            new BlockEmitterGrouping(),
            new BoxPollutionCleanupEmitterFactory());
    }
}
