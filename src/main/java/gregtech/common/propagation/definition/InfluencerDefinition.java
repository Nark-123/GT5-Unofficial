package gregtech.common.propagation.definition;

import gregtech.common.propagation.api.PropagationInfluencer;

public final class InfluencerDefinition<
    I extends PropagationInfluencer,
    S extends InfluencerStateSnapshot> {

    private final String id;
    private final Class<I> influencerType;
    private final InfluencerStateAdapter<I, S>
        stateAdapter;

    public InfluencerDefinition(
        String id,
        Class<I> influencerType,
        InfluencerStateAdapter<I, S> stateAdapter) {

        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException(
                "Influencer definition id is empty");
        }

        if (influencerType == null) {
            throw new IllegalArgumentException(
                "Influencer type is null");
        }

        if (stateAdapter == null) {
            throw new IllegalArgumentException(
                "Influencer state adapter is null");
        }

        this.id = id;
        this.influencerType = influencerType;
        this.stateAdapter = stateAdapter;
    }

    public String getId() {
        return id;
    }

    public Class<I> getInfluencerType() {
        return influencerType;
    }

    public InfluencerStateAdapter<I, S>
    getStateAdapter() {

        return stateAdapter;
    }
}
