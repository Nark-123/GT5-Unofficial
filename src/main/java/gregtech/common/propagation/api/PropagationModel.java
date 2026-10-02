package gregtech.common.propagation.api;

import gregtech.common.propagation.definition.EmitterDefinitionSelector;
import gregtech.common.propagation.definition.InfluencerDefinitionSelector;

public interface PropagationModel<E extends PropagationEmitter> {

    EmitterDefinitionSelector<E> getSourceDefinitionSelector();

    InfluencerDefinitionSelector getInfluencerDefinitionSelector();
}
