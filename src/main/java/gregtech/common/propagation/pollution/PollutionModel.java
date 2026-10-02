package gregtech.common.propagation.pollution;

import gregtech.common.propagation.definition.InfluencerDefinitionSelector;
import gregtech.common.propagation.pollution.emitter.PollutionFieldEmitter;
import gregtech.common.propagation.api.PropagationModel;
import gregtech.common.propagation.definition.EmitterDefinitionSelector;

public final class PollutionModel
    implements PropagationModel<PollutionFieldEmitter> {

    private final PollutionEmitterDefinitionSelector
        sourceDefinitionSelector;

    private final InfluencerDefinitionSelector
        influencerDefinitionSelector =
        new PollutionInfluencerDefinitionSelector();

    public PollutionModel() {
        sourceDefinitionSelector =
            new PollutionEmitterDefinitionSelector();

        sourceDefinitionSelector.register(
            PollutionEmitterDefinitions.standard());

        sourceDefinitionSelector.register(
            PollutionEmitterDefinitions.cleanupSphere());

        sourceDefinitionSelector.register(
            PollutionEmitterDefinitions.cleanupBox());
    }

    @Override
    public EmitterDefinitionSelector<PollutionFieldEmitter> getSourceDefinitionSelector() {
        return sourceDefinitionSelector;
    }

    @Override
    public InfluencerDefinitionSelector getInfluencerDefinitionSelector() {
        return influencerDefinitionSelector;
    }
}
