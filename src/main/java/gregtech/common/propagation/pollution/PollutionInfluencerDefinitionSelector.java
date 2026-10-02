package gregtech.common.propagation.pollution;

import java.util.HashMap;
import java.util.Map;

import gregtech.common.propagation.definition.InfluencerDefinition;
import gregtech.common.propagation.definition.InfluencerDefinitionSelector;

public final class
PollutionInfluencerDefinitionSelector
    implements InfluencerDefinitionSelector {

    private final Map<
        String,
        InfluencerDefinition<?, ?>> definitions =
        new HashMap<>();

    public PollutionInfluencerDefinitionSelector() {
        register(
            PollutionInfluencerDefinitions.DUMMY);
    }

    private void register(
        InfluencerDefinition<?, ?> definition) {

        InfluencerDefinition<?, ?> previous =
            definitions.put(
                definition.getId(),
                definition);

        if (previous != null) {
            throw new IllegalStateException(
                "Duplicate influencer definition: "
                    + definition.getId());
        }
    }

    @Override
    public InfluencerDefinition<?, ?> getDefinition(
        String definitionId) {

        return definitions.get(
            definitionId);
    }
}
