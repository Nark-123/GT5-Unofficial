package gregtech.common.propagation.definition;

import gregtech.common.propagation.api.PropagationEmitter;

public final class EmitterDefinition<
    K,
    E extends PropagationEmitter,
    S extends EmitterStateSnapshot> {

    private final String id;
    private final EmitterGrouping<K> grouping;
    private final EmitterFactory<K, E> factory;
    private final EmitterStateAdapter<K, E, S> stateAdapter;

    public EmitterDefinition(
        String id,
        EmitterGrouping<K> grouping,
        EmitterFactory<K, E> factory,
        EmitterStateAdapter<K, E, S> stateAdapter) {

        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException(
                "Emitter definition id is empty");
        }

        if (grouping == null) {
            throw new IllegalArgumentException(
                "Emitter grouping is null");
        }

        if (factory == null) {
            throw new IllegalArgumentException(
                "Emitter factory is null");
        }

        if (stateAdapter == null) {
            throw new IllegalArgumentException(
                "Emitter state adapter is null");
        }

        this.id = id;
        this.grouping = grouping;
        this.factory = factory;
        this.stateAdapter = stateAdapter;
    }

    public String getId() {
        return id;
    }

    public EmitterGrouping<K> getGrouping() {
        return grouping;
    }

    public EmitterFactory<K, E> getFactory() {
        return factory;
    }

    public EmitterStateAdapter<K, E, S> getStateAdapter() {
        return stateAdapter;
    }
}
