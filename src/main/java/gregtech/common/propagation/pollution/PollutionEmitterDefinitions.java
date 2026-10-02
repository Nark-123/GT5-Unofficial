package gregtech.common.propagation.pollution;

import net.minecraft.util.Vec3;

import gregtech.common.propagation.definition.BlockEmitterGrouping;
import gregtech.common.propagation.definition.BlockGroupKey;
import gregtech.common.propagation.definition.CellEmitterGrouping;
import gregtech.common.propagation.definition.CellGroupKey;
import gregtech.common.propagation.definition.EmitterDefinition;
import gregtech.common.propagation.definition.EmitterStateAdapter;
import gregtech.common.propagation.pollution.emitter.BoxPollutionCleanupEmitter;
import gregtech.common.propagation.pollution.emitter.BoxPollutionCleanupEmitterFactory;
import gregtech.common.propagation.pollution.emitter.PollutionEmitter;
import gregtech.common.propagation.pollution.emitter.PollutionEmitterFactory;
import gregtech.common.propagation.pollution.emitter.SphericalPollutionCleanupEmitter;
import gregtech.common.propagation.pollution.emitter.SphericalPollutionCleanupEmitterFactory;
import gregtech.common.propagation.pollution.state.BoxPollutionCleanupState;
import gregtech.common.propagation.pollution.state.PollutionEmitterState;
import gregtech.common.propagation.pollution.state.SphericalPollutionCleanupState;

public final class PollutionEmitterDefinitions {

    public static final String STANDARD_ID =
        "pollution";

    public static final String CLEANUP_SPHERE_ID =
        "pollution_cleanup_sphere";

    public static final String CLEANUP_BOX_ID =
        "pollution_cleanup_box";

    private PollutionEmitterDefinitions() {}

    private static final EmitterStateAdapter<CellGroupKey, PollutionEmitter, PollutionEmitterState>
        STANDARD_STATE_ADAPTER =
        new EmitterStateAdapter<
            CellGroupKey,
            PollutionEmitter,
            PollutionEmitterState>() {

            @Override
            public PollutionEmitterState capture(
                CellGroupKey groupKey,
                PollutionEmitter emitter) {

                return new PollutionEmitterState(
                    groupKey.getX(),
                    groupKey.getY(),
                    groupKey.getZ(),
                    emitter.getPollution(),
                    emitter.getPhysicalPropagationRange());
            }

            @Override
            public CellGroupKey getGroupKey(
                PollutionEmitterState state) {

                return new CellGroupKey(
                    state.getCellX(),
                    state.getCellY(),
                    state.getCellZ());
            }

            @Override
            public PollutionEmitter createReplica(
                int dimension,
                CellGroupKey groupKey,
                PollutionEmitterState state) {

                return new PollutionEmitter(
                    dimension,
                    Vec3.createVectorHelper(
                        groupKey.getX(),
                        groupKey.getY(),
                        groupKey.getZ()),
                    state.getPropagationRange());
            }

            @Override
            public void applyReplica(
                PollutionEmitter emitter,
                PollutionEmitterState state) {

                if (Double.compare(
                    emitter.getPropagationRange(),
                    state.getPropagationRange()) != 0) {

                    emitter.setPropagationRange(
                        state.getPropagationRange());
                }

                emitter.setPollution(
                    state.getPollution());
            }
        };

    private static final EmitterStateAdapter<
        BlockGroupKey,
        SphericalPollutionCleanupEmitter,
        SphericalPollutionCleanupState>
        CLEANUP_SPHERE_STATE_ADAPTER =
        new EmitterStateAdapter<
            BlockGroupKey,
            SphericalPollutionCleanupEmitter,
            SphericalPollutionCleanupState>() {

            @Override
            public SphericalPollutionCleanupState capture(
                BlockGroupKey groupKey,
                SphericalPollutionCleanupEmitter emitter) {

                Vec3 center = emitter.getPosition();

                return new SphericalPollutionCleanupState(
                    groupKey.getX(),
                    groupKey.getY(),
                    groupKey.getZ(),
                    center.xCoord,
                    center.yCoord,
                    center.zCoord,
                    emitter.getPropagationRange(),
                    emitter.getStrength());
            }

            @Override
            public BlockGroupKey getGroupKey(
                SphericalPollutionCleanupState state) {

                return new BlockGroupKey(
                    state.getGroupX(),
                    state.getGroupY(),
                    state.getGroupZ());
            }

            @Override
            public SphericalPollutionCleanupEmitter createReplica(
                int dimension,
                BlockGroupKey groupKey,
                SphericalPollutionCleanupState state) {

                SphericalPollutionCleanupEmitter emitter =
                    new SphericalPollutionCleanupEmitter(
                        dimension,
                        Vec3.createVectorHelper(
                            state.getCenterX(),
                            state.getCenterY(),
                            state.getCenterZ()),
                        state.getRange());

                emitter.setReplicaStrength(
                    state.getStrength());

                return emitter;
            }

            @Override
            public void applyReplica(
                SphericalPollutionCleanupEmitter emitter,
                SphericalPollutionCleanupState state) {

                emitter.setReplicaStrength(
                    state.getStrength());
            }
        };

    private static final EmitterStateAdapter<
        BlockGroupKey,
        BoxPollutionCleanupEmitter,
        BoxPollutionCleanupState>
        CLEANUP_BOX_STATE_ADAPTER =
        new EmitterStateAdapter<
            BlockGroupKey,
            BoxPollutionCleanupEmitter,
            BoxPollutionCleanupState>() {

            @Override
            public BoxPollutionCleanupState capture(
                BlockGroupKey groupKey,
                BoxPollutionCleanupEmitter emitter) {

                Vec3 center = emitter.getPosition();

                return new BoxPollutionCleanupState(
                    groupKey.getX(),
                    groupKey.getY(),
                    groupKey.getZ(),
                    center.xCoord,
                    center.yCoord,
                    center.zCoord,
                    emitter.getHalfX(),
                    emitter.getHalfY(),
                    emitter.getHalfZ(),
                    emitter.getStrength());
            }

            @Override
            public BlockGroupKey getGroupKey(
                BoxPollutionCleanupState state) {

                return new BlockGroupKey(
                    state.getGroupX(),
                    state.getGroupY(),
                    state.getGroupZ());
            }

            @Override
            public BoxPollutionCleanupEmitter createReplica(
                int dimension,
                BlockGroupKey groupKey,
                BoxPollutionCleanupState state) {

                BoxPollutionCleanupEmitter emitter =
                    new BoxPollutionCleanupEmitter(
                        dimension,
                        Vec3.createVectorHelper(
                            state.getCenterX(),
                            state.getCenterY(),
                            state.getCenterZ()),
                        state.getHalfX(),
                        state.getHalfY(),
                        state.getHalfZ());

                emitter.setReplicaStrength(
                    state.getStrength());

                return emitter;
            }

            @Override
            public void applyReplica(
                BoxPollutionCleanupEmitter emitter,
                BoxPollutionCleanupState state) {

                emitter.setReplicaStrength(
                    state.getStrength());
            }
        };

    public static EmitterDefinition<CellGroupKey, PollutionEmitter, PollutionEmitterState> standard() {
        return new EmitterDefinition<>(
            STANDARD_ID,
            new CellEmitterGrouping(),
            new PollutionEmitterFactory(),
            STANDARD_STATE_ADAPTER);
    }

    public static EmitterDefinition<
        BlockGroupKey,
        SphericalPollutionCleanupEmitter,
        SphericalPollutionCleanupState>
    cleanupSphere() {

        return new EmitterDefinition<>(
            CLEANUP_SPHERE_ID,
            new BlockEmitterGrouping(),
            new SphericalPollutionCleanupEmitterFactory(),
            CLEANUP_SPHERE_STATE_ADAPTER);
    }

    public static EmitterDefinition<
        BlockGroupKey,
        BoxPollutionCleanupEmitter,
        BoxPollutionCleanupState>
    cleanupBox() {

        return new EmitterDefinition<>(
            CLEANUP_BOX_ID,
            new BlockEmitterGrouping(),
            new BoxPollutionCleanupEmitterFactory(),
            CLEANUP_BOX_STATE_ADAPTER);
    }
}
