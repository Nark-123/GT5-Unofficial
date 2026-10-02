package gregtech.common.propagation.pollution;

import net.minecraft.util.Vec3;

import gregtech.common.propagation.definition.InfluencerDefinition;
import gregtech.common.propagation.definition.InfluencerStateAdapter;
import gregtech.common.propagation.pollution.debug.DummyPollutionInfluencer;
import gregtech.common.propagation.pollution.state.DummyPollutionInfluencerState;

public final class PollutionInfluencerDefinitions {

    public static final String DUMMY_ID =
        "pollution_dummy_influencer";

    public static final InfluencerDefinition<
        DummyPollutionInfluencer,
        DummyPollutionInfluencerState> DUMMY =
        new InfluencerDefinition<>(
            DUMMY_ID,
            DummyPollutionInfluencer.class,
            new InfluencerStateAdapter<
                DummyPollutionInfluencer,
                DummyPollutionInfluencerState>() {

                @Override
                public DummyPollutionInfluencerState capture(
                    DummyPollutionInfluencer influencer) {

                    Vec3 pos =
                        influencer.getPosition();

                    return new DummyPollutionInfluencerState(
                        pos.xCoord,
                        pos.yCoord,
                        pos.zCoord);
                }

                @Override
                public DummyPollutionInfluencer createReplica(
                    int dimension,
                    DummyPollutionInfluencerState state) {

                    return new DummyPollutionInfluencer(
                        Vec3.createVectorHelper(
                            state.getX(),
                            state.getY(),
                            state.getZ()));
                }

                @Override
                public void applyReplica(
                    DummyPollutionInfluencer influencer,
                    DummyPollutionInfluencerState state) {

                    Vec3 pos =
                        influencer.getPosition();

                    if (Double.compare(
                        pos.xCoord,
                        state.getX()) != 0
                        || Double.compare(
                        pos.yCoord,
                        state.getY()) != 0
                        || Double.compare(
                        pos.zCoord,
                        state.getZ()) != 0) {

                        throw new IllegalStateException(
                            "Dummy pollution influencer "
                                + "cannot move");
                    }
                }
            });

    private PollutionInfluencerDefinitions() {}
}
