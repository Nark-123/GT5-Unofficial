package gregtech.common.propagation.definition;

import gregtech.common.propagation.api.PropagationInfluencer;

public interface InfluencerStateAdapter<
    I extends PropagationInfluencer,
    S extends InfluencerStateSnapshot> {

    S capture(I influencer);

    I createReplica(
        int dimension,
        S state);

    void applyReplica(
        I influencer,
        S state);
}
