package gregtech.common.propagation.runtime;

import gregtech.common.propagation.definition.InfluencerStateSnapshot;

public final class InfluencerRuntimeChange {

    public enum Operation {
        UPSERT,
        REMOVE
    }

    private final Operation operation;
    private final long influencerId;
    private final String definitionId;
    private final InfluencerStateSnapshot state;

    private InfluencerRuntimeChange(
        Operation operation,
        long influencerId,
        String definitionId,
        InfluencerStateSnapshot state) {

        if (operation == null) {
            throw new IllegalArgumentException(
                "Influencer operation is null");
        }

        if (influencerId <= 0L) {
            throw new IllegalArgumentException(
                "Invalid influencer id: "
                    + influencerId);
        }

        if (operation == Operation.UPSERT) {
            if (definitionId == null
                || definitionId.isEmpty()) {

                throw new IllegalArgumentException(
                    "Invalid influencer definition id");
            }

            if (state == null) {
                throw new IllegalArgumentException(
                    "UPSERT state is null");
            }
        } else if (state != null) {
            throw new IllegalArgumentException(
                "REMOVE state must be null");
        }

        this.operation = operation;
        this.influencerId = influencerId;
        this.definitionId = definitionId;
        this.state = state;
    }

    public static InfluencerRuntimeChange upsert(
        long influencerId,
        String definitionId,
        InfluencerStateSnapshot state) {

        return new InfluencerRuntimeChange(
            Operation.UPSERT,
            influencerId,
            definitionId,
            state);
    }

    public static InfluencerRuntimeChange remove(
        long influencerId) {

        return new InfluencerRuntimeChange(
            Operation.REMOVE,
            influencerId,
            null,
            null);
    }

    public static InfluencerRuntimeChange remove(
        long influencerId,
        String definitionId) {

        return new InfluencerRuntimeChange(
            Operation.REMOVE,
            influencerId,
            definitionId,
            null);
    }

    public Operation getOperation() {
        return operation;
    }

    public long getInfluencerId() {
        return influencerId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public InfluencerStateSnapshot getState() {
        return state;
    }
}
