package gregtech.common.propagation;

public interface PropagationModel<E extends PropagationEmitter> {

    EmitterDefinitionSelector<E> getSourceDefinitionSelector();
}
