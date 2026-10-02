package gregtech.common.propagation.definition;

public final class EmitterGroupRef<K> {

    private final String definitionId;
    private final K groupKey;

    public EmitterGroupRef(String definitionId, K groupKey) {
        if (definitionId == null || definitionId.isEmpty()) {
            throw new IllegalArgumentException("Emitter definition id is empty");
        }

        if (groupKey == null) {
            throw new IllegalArgumentException("Emitter group key is null");
        }

        this.definitionId = definitionId;
        this.groupKey = groupKey;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public K getGroupKey() {
        return groupKey;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof EmitterGroupRef)) {
            return false;
        }

        EmitterGroupRef<?> other = (EmitterGroupRef<?>) obj;

        return definitionId.equals(other.definitionId)
            && groupKey.equals(other.groupKey);
    }

    @Override
    public int hashCode() {
        int result = definitionId.hashCode();
        result = 31 * result + groupKey.hashCode();
        return result;
    }
}
