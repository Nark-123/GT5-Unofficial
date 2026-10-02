package gregtech.common.propagation.runtime;

public final class EmitterRuntimeState {

    private final long emitterId;

    private long emitterRevision;
    private long observedPropagationRevision;

    public EmitterRuntimeState(
        long emitterId,
        long emitterRevision,
        long propagationRevision) {

        if (emitterId <= 0L) {
            throw new IllegalArgumentException(
                "Invalid emitter id: " + emitterId);
        }

        if (emitterRevision < 0L) {
            throw new IllegalArgumentException(
                "Invalid emitter revision: " + emitterRevision);
        }

        this.emitterId = emitterId;
        this.emitterRevision = emitterRevision;
        this.observedPropagationRevision =
            propagationRevision;
    }

    public long getEmitterId() {
        return emitterId;
    }

    public long getEmitterRevision() {
        return emitterRevision;
    }

    public void setEmitterRevision(long revision) {
        if (revision < 0L) {
            throw new IllegalArgumentException(
                "Invalid emitter revision: " + revision);
        }

        emitterRevision = revision;
    }

    public void incrementEmitterRevision() {
        if (emitterRevision == Long.MAX_VALUE) {
            throw new IllegalStateException(
                "Emitter revision overflow");
        }

        emitterRevision++;
    }

    public boolean observePropagationRevision(
        long revision) {

        if (observedPropagationRevision == revision) {
            return false;
        }

        observedPropagationRevision = revision;
        return true;
    }
}
