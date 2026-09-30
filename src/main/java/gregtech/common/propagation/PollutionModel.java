package gregtech.common.propagation;

public final class PollutionModel implements PropagationModel<PollutionEmitter> {

    private final EmitterDefinitionSelector<PollutionEmitter> sourceDefinitionSelector;

    public PollutionModel() {
        sourceDefinitionSelector =
            new PollutionEmitterDefinitionSelector(
                PollutionEmitterDefinitions.standard());
    }

    @Override
    public EmitterDefinitionSelector<PollutionEmitter> getSourceDefinitionSelector() {
        return sourceDefinitionSelector;
    }
}
