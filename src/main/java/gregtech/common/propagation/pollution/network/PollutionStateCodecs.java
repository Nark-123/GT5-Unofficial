package gregtech.common.propagation.pollution.network;

import com.google.common.io.ByteArrayDataInput;

import gregtech.common.propagation.network.EmitterStateCodec;
import gregtech.common.propagation.network.EmitterStateCodecRegistry;
import gregtech.common.propagation.pollution.PollutionEmitterDefinitions;
import gregtech.common.propagation.pollution.state.BoxPollutionCleanupState;
import gregtech.common.propagation.pollution.state.PollutionEmitterState;
import gregtech.common.propagation.pollution.state.SphericalPollutionCleanupState;
import io.netty.buffer.ByteBuf;

public final class PollutionStateCodecs {

    private static final EmitterStateCodecRegistry
        REGISTRY =
        new EmitterStateCodecRegistry();

    static {
        REGISTRY.register(
            PollutionEmitterDefinitions.STANDARD_ID,
            new PollutionEmitterCodec());

        REGISTRY.register(
            PollutionEmitterDefinitions.CLEANUP_SPHERE_ID,
            new SphereCleanupCodec());

        REGISTRY.register(
            PollutionEmitterDefinitions.CLEANUP_BOX_ID,
            new BoxCleanupCodec());
    }

    private PollutionStateCodecs() {}

    public static EmitterStateCodecRegistry registry() {
        return REGISTRY;
    }

    private static final class PollutionEmitterCodec
        implements EmitterStateCodec<PollutionEmitterState> {

        @Override
        public Class<PollutionEmitterState>
        getStateType() {

            return PollutionEmitterState.class;
        }

        @Override
        public void encode(
            PollutionEmitterState state,
            ByteBuf out) {

            out.writeInt(state.getCellX());
            out.writeInt(state.getCellY());
            out.writeInt(state.getCellZ());

            out.writeDouble(
                state.getPollution());

            out.writeDouble(
                state.getPropagationRange());
        }

        @Override
        public PollutionEmitterState decode(ByteBuf in) {

            return new PollutionEmitterState(
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readDouble(),
                in.readDouble());
        }
    }

    private static final class SphereCleanupCodec
        implements EmitterStateCodec<
        SphericalPollutionCleanupState> {

        @Override
        public Class<SphericalPollutionCleanupState>
        getStateType() {

            return SphericalPollutionCleanupState.class;
        }

        @Override
        public void encode(
            SphericalPollutionCleanupState state,
            ByteBuf out) {

            out.writeInt(state.getGroupX());
            out.writeInt(state.getGroupY());
            out.writeInt(state.getGroupZ());

            out.writeDouble(state.getCenterX());
            out.writeDouble(state.getCenterY());
            out.writeDouble(state.getCenterZ());

            out.writeDouble(state.getRange());
            out.writeDouble(state.getStrength());
        }

        @Override
        public SphericalPollutionCleanupState decode(ByteBuf in) {

            return new SphericalPollutionCleanupState(
                in.readInt(),
                in.readInt(),
                in.readInt(),

                in.readDouble(),
                in.readDouble(),
                in.readDouble(),

                in.readDouble(),
                in.readDouble());
        }
    }

    private static final class BoxCleanupCodec
        implements EmitterStateCodec<
        BoxPollutionCleanupState> {

        @Override
        public Class<BoxPollutionCleanupState>
        getStateType() {

            return BoxPollutionCleanupState.class;
        }

        @Override
        public void encode(
            BoxPollutionCleanupState state,
            ByteBuf out) {

            out.writeInt(state.getGroupX());
            out.writeInt(state.getGroupY());
            out.writeInt(state.getGroupZ());

            out.writeDouble(state.getCenterX());
            out.writeDouble(state.getCenterY());
            out.writeDouble(state.getCenterZ());

            out.writeDouble(state.getHalfX());
            out.writeDouble(state.getHalfY());
            out.writeDouble(state.getHalfZ());

            out.writeDouble(state.getStrength());
        }

        @Override
        public BoxPollutionCleanupState decode(ByteBuf in) {

            return new BoxPollutionCleanupState(
                in.readInt(),
                in.readInt(),
                in.readInt(),

                in.readDouble(),
                in.readDouble(),
                in.readDouble(),

                in.readDouble(),
                in.readDouble(),
                in.readDouble(),

                in.readDouble());
        }
    }
}
