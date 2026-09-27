package gregtech.common.propagation;

public final class PollutionQueryProfiler {

    private long queries;
    private long queryNanos;
    private long emitterCandidates;
    private long emittersInsideRange;
    private long influencerCalls;
    private long influencerNoops;

    private int maxEmitterCandidates;
    private int maxInfluencersPerEmitter;

    public void beginQuery(int candidates) {
        queries++;
        emitterCandidates += candidates;
        maxEmitterCandidates = Math.max(maxEmitterCandidates, candidates);
    }

    public void emitterInsideRange(int influencers) {
        emittersInsideRange++;
        maxInfluencersPerEmitter = Math.max(maxInfluencersPerEmitter, influencers);
    }

    public void influencerCall(double multiplier) {
        influencerCalls++;

        if (multiplier == 1.0D) {
            influencerNoops++;
        }
    }

    public void endQuery(long nanos) {
        queryNanos += nanos;
    }

    public Snapshot snapshot() {
        return new Snapshot(
            queries,
            queryNanos,
            emitterCandidates,
            emittersInsideRange,
            influencerCalls,
            influencerNoops,
            maxEmitterCandidates,
            maxInfluencersPerEmitter
        );
    }

    public void reset() {
        queries = 0;
        queryNanos = 0;
        emitterCandidates = 0;
        emittersInsideRange = 0;
        influencerCalls = 0;
        influencerNoops = 0;
        maxEmitterCandidates = 0;
        maxInfluencersPerEmitter = 0;
    }

    public static final class Snapshot {

        public final long queries;
        public final long queryNanos;
        public final long emitterCandidates;
        public final long emittersInsideRange;
        public final long influencerCalls;
        public final long influencerNoops;

        public final int maxEmitterCandidates;
        public final int maxInfluencersPerEmitter;

        public Snapshot(
            long queries,
            long queryNanos,
            long emitterCandidates,
            long emittersInsideRange,
            long influencerCalls,
            long influencerNoops,
            int maxEmitterCandidates,
            int maxInfluencersPerEmitter
        ) {
            this.queries = queries;
            this.queryNanos = queryNanos;
            this.emitterCandidates = emitterCandidates;
            this.emittersInsideRange = emittersInsideRange;
            this.influencerCalls = influencerCalls;
            this.influencerNoops = influencerNoops;
            this.maxEmitterCandidates = maxEmitterCandidates;
            this.maxInfluencersPerEmitter = maxInfluencersPerEmitter;
        }

        public double getQueryMillis() {
            return queryNanos / 1_000_000.0D;
        }

        public double getAverageQueryMicros() {
            return queries == 0 ? 0.0D : queryNanos / 1_000.0D / queries;
        }

        public double getAverageEmitterCandidates() {
            return queries == 0 ? 0.0D : (double) emitterCandidates / queries;
        }

        public double getAverageEmittersInsideRange() {
            return queries == 0 ? 0.0D : (double) emittersInsideRange / queries;
        }

        public double getAverageInfluencerCalls() {
            return queries == 0 ? 0.0D : (double) influencerCalls / queries;
        }

        public double getInfluencerNoopRatio() {
            return influencerCalls == 0 ? 0.0D : (double) influencerNoops / influencerCalls;
        }

        public double getInfluencerNoopPercent() {
            return getInfluencerNoopRatio() * 100.0D;
        }
    }
}
