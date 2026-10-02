package gregtech.common.propagation.runtime;

import gregtech.common.propagation.definition.EmitterStateSnapshot;

public final class EmitterRuntimeChange {

    public enum Operation {
        UPSERT,
        REMOVE
    }

    private final Operation operation;
    private final long emitterId;
    private final String definitionId;
    private final long emitterRevision;
    private final EmitterStateSnapshot state;

    private EmitterRuntimeChange(
        Operation operation,
        long emitterId,
        String definitionId,
        long emitterRevision,
        EmitterStateSnapshot state) {

        if (operation == null) {
            throw new IllegalArgumentException(
                "Emitter operation is null");
        }

        if (emitterId <= 0L) {
            throw new IllegalArgumentException(
                "Invalid emitter id: "
                    + emitterId);
        }

        if (emitterRevision < 0L) {
            throw new IllegalArgumentException(
                "Invalid emitter revision: "
                    + emitterRevision);
        }

        if (operation == Operation.UPSERT) {
            if (definitionId == null
                || definitionId.isEmpty()) {

                throw new IllegalArgumentException(
                    "Invalid definition id");
            }

            if (state == null) {
                throw new IllegalArgumentException(
                    "UPSERT state is null");
            }
        } else {
            /*
             * REMOVE is identified only by runtime emitterId
             * on the wire. definitionId is optional.
             */
            if (definitionId != null
                && definitionId.isEmpty()) {

                throw new IllegalArgumentException(
                    "Invalid definition id");
            }

            if (state != null) {
                throw new IllegalArgumentException(
                    "REMOVE state must be null");
            }
        }

        this.operation = operation;
        this.emitterId = emitterId;
        this.definitionId = definitionId;
        this.emitterRevision = emitterRevision;
        this.state = state;
    }

    public static EmitterRuntimeChange upsert(
        long emitterId,
        String definitionId,
        EmitterStateSnapshot state) {

        return new EmitterRuntimeChange(
            Operation.UPSERT,
            emitterId,
            definitionId,
            0L,
            state);
    }

    public static EmitterRuntimeChange remove(
        long emitterId) {

        return new EmitterRuntimeChange(
            Operation.REMOVE,
            emitterId,
            null,
            0L,
            null);
    }

    public static EmitterRuntimeChange upsert(
        long emitterId,
        String definitionId,
        long emitterRevision,
        EmitterStateSnapshot state) {

        return new EmitterRuntimeChange(
            Operation.UPSERT,
            emitterId,
            definitionId,
            emitterRevision,
            state);
    }

    public static EmitterRuntimeChange remove(
        long emitterId,
        String definitionId,
        long emitterRevision) {

        return new EmitterRuntimeChange(
            Operation.REMOVE,
            emitterId,
            definitionId,
            emitterRevision,
            null);
    }

    public Operation getOperation() {
        return operation;
    }

    public long getEmitterId() {
        return emitterId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public long getEmitterRevision() {
        return emitterRevision;
    }

    public EmitterStateSnapshot getState() {
        return state;
    }
}
