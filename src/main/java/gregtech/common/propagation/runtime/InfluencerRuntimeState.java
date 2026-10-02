package gregtech.common.propagation.runtime;

public final class InfluencerRuntimeState {

    private final long influencerId;
    private final String definitionId;

    public InfluencerRuntimeState(
        long influencerId,
        String definitionId) {

        if (influencerId <= 0L) {
            throw new IllegalArgumentException(
                "Invalid influencer id: "
                    + influencerId);
        }

        if (definitionId == null
            || definitionId.isEmpty()) {

            throw new IllegalArgumentException(
                "Invalid influencer definition id");
        }

        this.influencerId = influencerId;
        this.definitionId = definitionId;
    }

    public long getInfluencerId() {
        return influencerId;
    }

    public String getDefinitionId() {
        return definitionId;
    }
}
