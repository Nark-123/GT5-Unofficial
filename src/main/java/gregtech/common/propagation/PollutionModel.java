package gregtech.common.propagation;

public final class PollutionModel
    implements PropagationModel<PollutionFieldEmitter> {

    private final PollutionEmitterDefinitionSelector
        sourceDefinitionSelector;

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
    public EmitterDefinitionSelector<PollutionFieldEmitter>
    getSourceDefinitionSelector() {

        return sourceDefinitionSelector;
    }
}
