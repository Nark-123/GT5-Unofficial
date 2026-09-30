package gregtech.common.propagation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.util.Vec3;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

public class PollutionEmitter implements PropagationEmitter {

    // Approximately 24-hour half-life at one decay step per second.
    private static final double DEFAULT_SMOOTHING = 0.99999198;
    private static final double DEFAULT_POLLUTION_THRESHOLD = 0.05D;
    private final int dimension;
    // 16x16x16 cell coordinates, not block coordinates.
    private final Vec3 cellPosition;
    private final Vec3 center;
    private final Set<PropagationSource> sources = Collections.newSetFromMap(new IdentityHashMap<>());
    private final double smoothing;
    private double pollution;
    private final List<InfluenceVector> influencers = new ArrayList<>();
    private final Long2DoubleOpenHashMap influencerFactorCache = new Long2DoubleOpenHashMap();
    private static final double GAUSSIAN_3_SIGMA_MASS = 0.9707091135D;
    private final double basePropagationRange;
    private double rangeMultiplier = 1.0D;
    private double effectiveRange;
    private double inverseRangeSquared;
    private double gaussianNormalization;
    private double effectiveRangeSquared;
    private boolean pollutionChanged;
    private long propagationRevision;
    private double propagationRange;

    private static final double LOG2_E = 1.4426950408889634D;

    public PollutionEmitter(int dimension, Vec3 cellPosition, double propagationRange) {
        this(dimension, cellPosition, propagationRange, DEFAULT_SMOOTHING);
    }

    public PollutionEmitter(int dimension, Vec3 cellPosition, double propagationRange, double smoothing) {
        if (!Double.isFinite(propagationRange) || propagationRange <= 0.0D) {
            throw new IllegalArgumentException("Invalid propagation range: " + propagationRange);
        }

        if (!Double.isFinite(smoothing) || smoothing < 0.0D || smoothing > 1.0D) {
            throw new IllegalArgumentException("Invalid smoothing: " + smoothing);
        }

        this.dimension = dimension;
        this.cellPosition = cellPosition;
        this.smoothing = smoothing;
        this.center = getCellCenter(cellPosition);
        this.basePropagationRange = propagationRange;
        this.propagationRange = propagationRange;
        influencerFactorCache.defaultReturnValue(Double.NaN);
        recalculatePropagationRange();
    }

    public void addSource(PropagationSource source) {
        if (source == null) {
            throw new IllegalArgumentException("Source is null");
        }

        if (source.getDimension() != dimension) {
            throw new IllegalArgumentException(
                "Source dimension mismatch: " + source.getDimension() + " != " + dimension);
        }

        sources.add(source);
    }

    // Scheduled approximately once every 20 ticks per emitter.
    public void update() {
        double incomingPollution = 0.0D;

        Iterator<PropagationSource> iterator = sources.iterator();

        while (iterator.hasNext()) {
            PropagationSource source = iterator.next();
            double emission = source.consumeEmission();

            if (!Double.isFinite(emission)) {
                throw new IllegalStateException("Invalid source emission: " + emission);
            }

            if (emission != 0.0D) {
                incomingPollution += emission;

                if (!Double.isFinite(incomingPollution)) {
                    throw new IllegalStateException("Pollution overflow");
                }
            }

            if (!source.isValid()) iterator.remove();
        }

        if (incomingPollution != 0.0D) {
            setPollution(pollution + incomingPollution);
        }

        pollution *= smoothing;

        if (pollution < 1.0E-2D) pollution = 0.0D;
    }

    private static double fastExpNeg(double x) {
        double y = x * LOG2_E;

        int n = (int) y;
        double f = y - n;

        double p = ((-0.03951000D * f + 0.23059332D) * f - 0.69107581D) * f + 0.99989849D;

        long scaleBits = (long) (1023 - n) << 52;
        double scale = Double.longBitsToDouble(scaleBits);

        return scale * p;
    }

    public double getInfluence(Vec3 pos) {
        return getInfluence(pos, null);
    }

    public double getInfluence(Vec3 pos, PollutionQueryProfiler profiler) {
        if (pollution <= 0.0D) {
            return 0.0D;
        }

        double dx = center.xCoord - pos.xCoord;
        double dy = center.yCoord - pos.yCoord;
        double dz = center.zCoord - pos.zCoord;

        double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);

        if (distanceSquared > effectiveRangeSquared) {
            return 0.0D;
        }

        if (profiler != null) {
            profiler.emitterInsideRange(influencers.size());
        }

        double influence = pollution / gaussianNormalization * fastExpNeg(4.5D * distanceSquared * inverseRangeSquared);

        if (!Double.isFinite(influence)) {
            throw new IllegalStateException("Invalid pollution influence: " + influence);
        }

        for (InfluenceVector vector : influencers) {
            double multiplier = vector.source.influence(pos, vector);

            if (!Double.isFinite(multiplier) || multiplier < 0.0D) {
                throw new IllegalStateException("Invalid influencer multiplier: " + multiplier);
            }

            if (profiler != null) {
                profiler.influencerCall(multiplier);
            }

            influence *= multiplier;

            if (!Double.isFinite(influence)) {
                throw new IllegalStateException("Pollution influence overflow");
            }
        }

        return influence;
    }

    public boolean isValid() {
        return !isEmpty();
    }

    public void addInfluencer(PropagationInfluencer influencer) {
        for (InfluenceVector vector : influencers) {
            if (vector.source == influencer) return;
        }

        influencers.add(new InfluenceVector(center, influencer));
    }

    public void removeInfluencer(PropagationInfluencer influencer) {
        Iterator<InfluenceVector> iterator = influencers.iterator();

        while (iterator.hasNext()) {
            if (iterator.next().source == influencer) {
                iterator.remove();
                return;
            }
        }
    }

    private Vec3 getCellCenter(Vec3 cellPosition) {

        return Vec3.createVectorHelper(
            cellPosition.xCoord * 16.0D + 8.0D,
            cellPosition.yCoord * 16.0D + 8.0D,
            cellPosition.zCoord * 16.0D + 8.0D);
    }

    private void recalculatePropagationRange() {
        effectiveRange = propagationRange * rangeMultiplier;
        effectiveRangeSquared = effectiveRange * effectiveRange;
        inverseRangeSquared = 1.0D / effectiveRangeSquared;

        gaussianNormalization = Math.pow(2.0D * Math.PI, 1.5D) * effectiveRange
            * effectiveRangeSquared
            / 27.0D
            * GAUSSIAN_3_SIGMA_MASS;
    }

    private void setPropagationRange(double range) {
        if (!Double.isFinite(range) || range <= 0.0D) {
            throw new IllegalArgumentException("Invalid propagation range: " + range);
        }

        range = Math.max(basePropagationRange, range);

        if (!Double.isFinite(range * rangeMultiplier)) {
            throw new IllegalArgumentException("Effective range overflow");
        }

        if (propagationRange == range) return;

        propagationRange = range;
        recalculatePropagationRange();
        propagationRevision++;
    }

    private Vec3 getCellPosition(Vec3 position) {
        return Vec3.createVectorHelper(
            getCellCoordinate(position.xCoord),
            getCellCoordinate(position.yCoord),
            getCellCoordinate(position.zCoord));
    }

    private int getCellCoordinate(double coordinate) {
        double cell = Math.floor(coordinate / 16.0D);

        if (cell < Integer.MIN_VALUE || cell > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Cell coordinate out of range: " + coordinate);
        }

        return (int) cell;
    }

    public void setRangeMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0.0D) {
            throw new IllegalArgumentException("Invalid range multiplier: " + multiplier);
        }

        if (!Double.isFinite(propagationRange * multiplier)) {
            throw new IllegalArgumentException("Effective range overflow");
        }

        if (rangeMultiplier == multiplier) return;

        rangeMultiplier = multiplier;
        recalculatePropagationRange();
        propagationRevision++;
    }

    public void setPollution(double pollution) {
        if (!Double.isFinite(pollution)) {
            throw new IllegalArgumentException("Invalid pollution: " + pollution);
        }

        double newPollution = Math.max(0.0D, pollution);

        if (this.pollution == newPollution) {
            return;
        }

        this.pollution = newPollution;
        pollutionChanged = true;
    }

    @Override
    public boolean consumeStateChanged() {
        boolean changed = pollutionChanged;
        pollutionChanged = false;
        return changed;
    }

    @Override
    public boolean acceptsInfluencer(PropagationInfluencer influencer) {
        return true;
    }

    public Vec3 getPosition() {
        return center;
    }

    public Vec3 getCellPosition() {
        return cellPosition;
    }

    public double getPropagationRange() {
        return effectiveRange;
    }

    public PropagationType getType() {
        return PropagationType.POLLUTION;
    }

    public boolean isEmpty() {
        return sources.isEmpty() && pollution <= 0.0D;
    }

    public boolean hasSources() {
        return !sources.isEmpty();
    }

    public long getPropagationRevision() {
        return propagationRevision;
    }

    public int getDimension() {
        return dimension;
    }

    public double getBasePropagationRange() {
        return basePropagationRange;
    }

    public double getPhysicalPropagationRange() {
        return propagationRange;
    }

    public double getPollution() {
        return pollution;
    }

    public Vec3 getCenter() {
        return center;
    }
}
