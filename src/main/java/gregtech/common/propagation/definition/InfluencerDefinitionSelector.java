package gregtech.common.propagation.definition;

import gregtech.common.propagation.api.PropagationInfluencer;

public interface InfluencerDefinitionSelector {

    InfluencerDefinition<?, ?> getDefinition(
        String definitionId);

    default InfluencerDefinition<?, ?> select(
        PropagationInfluencer influencer) {

        if (influencer == null) {
            throw new IllegalArgumentException(
                "Influencer is null");
        }

        return getDefinition(
            influencer.getInfluencerDefinitionId());
    }
}
