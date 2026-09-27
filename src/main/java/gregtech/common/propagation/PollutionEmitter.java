package gregtech.common.propagation;

import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PollutionEmitter {
    // Approximately 24-hour half-life at one decay step per second.
    private static final double DEFAULT_SMOOTHING = 0.99999198;
    private static final double DEFAULT_POLLUTION_THRESHOLD = 0.05D;
    private final int dimension;
    // 16x16x16 cell coordinates, not block coordinates.
    private final Vec3 cellPosition;
    private final Vec3 center;
    private final List<PropagationSource> sources = new ArrayList<>();
    private final double smoothing;
    private double pollution;
    private final List<PropagationInfluencer> influencers = new ArrayList<>();
    private static final double GAUSSIAN_3_SIGMA_MASS = 0.9707091135D;
    private final double basePropagationRange;
    private double rangeMultiplier = 1.0D;
    private double effectiveRange;
    private double inverseRangeSquared;
    private double gaussianNormalization;

    public PollutionEmitter(int dimension, Vec3 cellPosition, double propagationRange) {
        this(
            dimension,
            cellPosition,
            propagationRange,
            DEFAULT_SMOOTHING
        );
    }

    public PollutionEmitter(
        int dimension,
        Vec3 cellPosition,
        double propagationRange,
        double smoothing
    ) {
        this.dimension = dimension;
        this.cellPosition = cellPosition;
        this.smoothing = smoothing;
        this.center = getCellCenter(cellPosition);
        this.basePropagationRange = propagationRange;
        recalculatePropagationRange();
    }

    public void addSource(PropagationSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source cannot be null");
        }

        if (source.getDimension() != dimension) {
            throw new IllegalArgumentException(
                "Source belongs to another dimension"
            );
        }

        Vec3 sourceCell = getCellPosition(source.getPosition());

        if (sourceCell.xCoord != cellPosition.xCoord
            || sourceCell.yCoord != cellPosition.yCoord
            || sourceCell.zCoord != cellPosition.zCoord) {

            throw new IllegalArgumentException(
                "Source belongs to another pollution cell"
            );
        }

        if (!sources.contains(source)) {
            sources.add(source);
        }
    }

    public void removeSource(PropagationSource source) {
        sources.remove(source);
    }

    // Scheduled approximately once every 20 ticks per emitter.
    public boolean update() {
        double incomingPollution = 0.0D;

        Iterator<PropagationSource> iterator = sources.iterator();

        while (iterator.hasNext()) {
            PropagationSource source = iterator.next();
            double emission = source.consumeEmission();

            if (emission != 0.0D) incomingPollution += emission;
            if (!source.isValid()) iterator.remove();
        }

        double oldPollution = pollution;

        if (incomingPollution != 0.0D) {
             pollution = Math.max(0.0D, pollution + incomingPollution);
        }

        boolean changed = pollution != oldPollution;
        pollution *= smoothing;

        if (pollution < 1.0E-9D) pollution = 0.0D;

        return changed;
    }

    public double getInfluence(Vec3 pos) {
        if (pollution <= 0.0D) {
            return 0.0D;
        }

        double distance = center.distanceTo(pos);

        if (distance > effectiveRange) {
            return 0.0D;
        }

        double distanceSquared = distance * distance;

        double influence = pollution / gaussianNormalization * Math.exp(-4.5D * distanceSquared * inverseRangeSquared);

        InfluenceVector vector =
            new InfluenceVector(center, pos);

        for (PropagationInfluencer influencer : influencers) {
            influence *= influencer.influence(pos, vector);
        }

        return influence;
    }

    public boolean isValid() {
        if (pollution > 0.0D) {
            return true;
        }

        for (PropagationSource source : sources) {
            if (source.isValid()) {
                return true;
            }
        }

        return false;
    }

    public void addInfluencer(PropagationInfluencer influencer) {
        if (!influencers.contains(influencer)) {
            influencers.add(influencer);
        }
    }

    public void removeInfluencer(PropagationInfluencer influencer) {
        influencers.remove(influencer);
    }

    private Vec3 getCellCenter(Vec3 cellPosition) {

        return Vec3.createVectorHelper(
            cellPosition.xCoord * 16.0D + 8.0D,
            cellPosition.yCoord * 16.0D + 8.0D,
            cellPosition.zCoord * 16.0D + 8.0D
        );
    }

    private void recalculatePropagationRange() {
        effectiveRange = basePropagationRange * rangeMultiplier;
        double rangeSquared = effectiveRange * effectiveRange;
        inverseRangeSquared = 1.0D / rangeSquared;

        gaussianNormalization =
            Math.pow(2.0D * Math.PI, 1.5D)
                * effectiveRange
                * rangeSquared
                / 27.0D
                * GAUSSIAN_3_SIGMA_MASS;
    }

    private Vec3 getCellPosition(Vec3 position) {
        return Vec3.createVectorHelper(
            ((int) Math.floor(position.xCoord)) >> 4,
            ((int) Math.floor(position.yCoord)) >> 4,
            ((int) Math.floor(position.zCoord)) >> 4
        );
    }

    public boolean flushPendingEmissions() {
        double incomingPollution = 0.0D;
        Iterator<PropagationSource> iterator = sources.iterator();

        while (iterator.hasNext()) {
            PropagationSource source = iterator.next();
            double emission = source.consumeEmission();

            if (emission != 0.0D) incomingPollution += emission;
            if (!source.isValid()) iterator.remove();
        }

        if (incomingPollution == 0.0D) return false;

        double oldPollution = pollution;
        pollution = Math.max(0.0D, pollution + incomingPollution);
        return pollution != oldPollution;
    }

    public void setRangeMultiplier(double multiplier) {
        if (multiplier <= 0.0D) {
            throw new IllegalArgumentException(
                "Range multiplier must be > 0"
            );
        }

        if (rangeMultiplier == multiplier) {
            return;
        }

        rangeMultiplier = multiplier;
        recalculatePropagationRange();
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

    public List<PropagationInfluencer> getInfluencers() {
        return influencers;
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

    public int getDimension() {
        return dimension;
    }

    public double getPollution() {
        return pollution;
    }

    public void setPollution(double pollution) {
        this.pollution = Math.max(0.0D, pollution);
    }

    public Vec3 getCenter() {
        return center;
    }
}
