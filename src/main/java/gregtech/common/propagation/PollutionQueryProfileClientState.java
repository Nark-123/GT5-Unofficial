package gregtech.common.propagation;

import java.util.HashMap;
import java.util.Map;

public final class PollutionQueryProfileClientState {

    private static final Map<Integer, Data> STATES = new HashMap<>();

    private PollutionQueryProfileClientState() {}

    public static void update(int dimension, int ticks, long queries, long queryNanos, long emitterCandidates,
        long emittersInsideRange, long influencerCalls, long influencerNoops, int maxEmitterCandidates,
        int maxInfluencersPerEmitter, int emitterCount, int influencerCount) {
        STATES.put(
            dimension,
            new Data(
                ticks,
                queries,
                queryNanos,
                emitterCandidates,
                emittersInsideRange,
                influencerCalls,
                influencerNoops,
                maxEmitterCandidates,
                maxInfluencersPerEmitter,
                emitterCount,
                influencerCount));
    }

    public static Data get(int dimension) {
        return STATES.get(dimension);
    }

    public static void clear(int dimension) {
        STATES.remove(dimension);
    }

    public static final class Data {

        public final int ticks;
        public final long queries;
        public final long queryNanos;
        public final long emitterCandidates;
        public final long emittersInsideRange;
        public final long influencerCalls;
        public final long influencerNoops;

        public final int maxEmitterCandidates;
        public final int maxInfluencersPerEmitter;
        public final int emitterCount;
        public final int influencerCount;

        private Data(int ticks, long queries, long queryNanos, long emitterCandidates, long emittersInsideRange,
            long influencerCalls, long influencerNoops, int maxEmitterCandidates, int maxInfluencersPerEmitter,
            int emitterCount, int influencerCount) {
            this.ticks = ticks;
            this.queries = queries;
            this.queryNanos = queryNanos;
            this.emitterCandidates = emitterCandidates;
            this.emittersInsideRange = emittersInsideRange;
            this.influencerCalls = influencerCalls;
            this.influencerNoops = influencerNoops;
            this.maxEmitterCandidates = maxEmitterCandidates;
            this.maxInfluencersPerEmitter = maxInfluencersPerEmitter;
            this.emitterCount = emitterCount;
            this.influencerCount = influencerCount;
        }

        public double getQueriesPerTick() {
            return ticks == 0 ? 0.0D : (double) queries / ticks;
        }

        public double getQueryMillisPerTick() {
            return ticks == 0 ? 0.0D : queryNanos / 1_000_000.0D / ticks;
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

        public double getInfluencerNoopPercent() {
            return influencerCalls == 0 ? 0.0D : 100.0D * influencerNoops / influencerCalls;
        }
    }
}
