package gregtech.common.propagation.definition;

import gregtech.common.propagation.api.PropagationEmitter;

public interface EmitterStateAdapter<
    K,
    E extends PropagationEmitter,
    S extends EmitterStateSnapshot> {

    S capture(K groupKey, E emitter);

    K getGroupKey(S state);

    E createReplica(
        int dimension,
        K groupKey,
        S state);

    void applyReplica(
        E emitter,
        S state);
}
