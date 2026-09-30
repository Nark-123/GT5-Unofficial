package gregtech.common.propagation;

public final class EmitterDefinition<K, E extends PropagationEmitter> {

    private final String id;
    private final EmitterGrouping<K> grouping;
    private final EmitterFactory<K, E> factory;

    public EmitterDefinition(
        String id,
        EmitterGrouping<K> grouping,
        EmitterFactory<K, E> factory) {

        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Emitter definition id is empty");
        }

        if (grouping == null) {
            throw new IllegalArgumentException("Emitter grouping is null");
        }

        if (factory == null) {
            throw new IllegalArgumentException("Emitter factory is null");
        }

        this.id = id;
        this.grouping = grouping;
        this.factory = factory;
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
}
