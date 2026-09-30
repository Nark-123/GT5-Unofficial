package gregtech.common.propagation;

public interface EmitterDefinitionSelector<E extends PropagationEmitter> {

    EmitterDefinition<?, ? extends E> select(PropagationSource source);
}
