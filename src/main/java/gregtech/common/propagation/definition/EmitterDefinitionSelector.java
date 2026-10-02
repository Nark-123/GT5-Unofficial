package gregtech.common.propagation.definition;

import gregtech.common.propagation.api.PropagationEmitter;
import gregtech.common.propagation.api.PropagationSource;

public interface EmitterDefinitionSelector<
    E extends PropagationEmitter> {

    EmitterDefinition<?, ? extends E, ?>
    select(PropagationSource source);

    EmitterDefinition<?, ? extends E, ?>
    getDefinition(String definitionId);
}
