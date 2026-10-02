package gregtech.common.propagation.pollution.network;

import gregtech.common.propagation.network.InfluencerStateCodec;
import gregtech.common.propagation.network.InfluencerStateCodecRegistry;
import gregtech.common.propagation.pollution.PollutionInfluencerDefinitions;
import gregtech.common.propagation.pollution.state.DummyPollutionInfluencerState;
import io.netty.buffer.ByteBuf;

public final class PollutionInfluencerStateCodecs {

    private static final InfluencerStateCodecRegistry
        REGISTRY =
        new InfluencerStateCodecRegistry();

    static {
        REGISTRY.register(
            PollutionInfluencerDefinitions.DUMMY_ID,
            new InfluencerStateCodec<
                DummyPollutionInfluencerState>() {

                @Override
                public Class<DummyPollutionInfluencerState>
                getStateType() {

                    return DummyPollutionInfluencerState.class;
                }

                @Override
                public void encode(
                    DummyPollutionInfluencerState state,
                    ByteBuf out) {

                    out.writeDouble(
                        state.getX());

                    out.writeDouble(
                        state.getY());

                    out.writeDouble(
                        state.getZ());
                }

                @Override
                public DummyPollutionInfluencerState decode(
                    ByteBuf in) {

                    return new DummyPollutionInfluencerState(
                        in.readDouble(),
                        in.readDouble(),
                        in.readDouble());
                }
            });
    }

    private PollutionInfluencerStateCodecs() {}

    public static InfluencerStateCodecRegistry
    registry() {

        return REGISTRY;
    }
}
