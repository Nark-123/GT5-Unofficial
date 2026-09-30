package gregtech.common.propagation;

public final class PollutionEmitterDefinitionSelector implements EmitterDefinitionSelector<PollutionEmitter> {

    private final EmitterDefinition<?, ? extends PollutionEmitter> standardDefinition;

    public PollutionEmitterDefinitionSelector(
        EmitterDefinition<?, ? extends PollutionEmitter> standardDefinition) {

        if (standardDefinition == null) {
            throw new IllegalArgumentException("Emitter definition is null");
        }

        this.standardDefinition = standardDefinition;
    }

    @Override
    public EmitterDefinition<?, ? extends PollutionEmitter> select(PropagationSource source) {
        return standardDefinition;
    }
}
