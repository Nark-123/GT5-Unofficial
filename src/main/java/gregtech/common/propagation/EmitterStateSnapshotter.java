package gregtech.common.propagation;

import gregtech.common.propagation.api.PropagationEmitter;
import gregtech.common.propagation.definition.EmitterStateSnapshot;

public interface EmitterStateSnapshotter<
    E extends PropagationEmitter> {

    EmitterStateSnapshot capture(E emitter);
}
