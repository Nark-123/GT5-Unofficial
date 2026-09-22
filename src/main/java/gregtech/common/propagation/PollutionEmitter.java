package gregtech.common.propagation;

import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PollutionEmitter{

    private static final double DEFAULT_SMOOTHING = 0.25D;
    private static final double DEFAULT_POLLUTION_THRESHOLD = 0.05D;
    private static final double DEFAULT_CENTER_THRESHOLD = 0.25D;

    private final int dimension;

    // Pollution grid cell position
    private final Vec3 cellPosition;

    private final List<PropagationSource> sources = new ArrayList<>();

    private final double smoothing;
    private final double pollutionThreshold;
    private final double centerThresholdSquared;

    private long lastCheckTick = -1L;
    private double pollution;

    // Current pollution center
    private Vec3 center = Vec3.createVectorHelper(0, 0, 0);

    // Last published state
    private double publishedPollution;
    private Vec3 publishedCenter = Vec3.createVectorHelper(0, 0, 0);

    private boolean published;

    private final double propagationRange;
    private final List<PropagationInfluencer> influencers = new ArrayList<>();

    public PollutionEmitter(int dimension, Vec3 cellPosition, double propagationRange) {
        this(
            dimension,
            cellPosition,
            propagationRange,
            DEFAULT_SMOOTHING,
            DEFAULT_POLLUTION_THRESHOLD,
            DEFAULT_CENTER_THRESHOLD
        );
    }

    public PollutionEmitter(int dimension, Vec3 cellPosition, double propagationRange,
                            double smoothing, double pollutionThreshold, double centerThreshold) {
        this.dimension = dimension;
        this.cellPosition = cellPosition;
        this.propagationRange = propagationRange;
        this.smoothing = smoothing;
        this.pollutionThreshold = pollutionThreshold;
        this.centerThresholdSquared = centerThreshold * centerThreshold;
        this.center = getCellCenter(cellPosition);
        this.publishedCenter = center;
    }

    public void addSource(PropagationSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source cannot be null");
        }

        if (source.getDimension() != dimension) {
            throw new IllegalArgumentException("Source belongs to another dimension");
        }

        Vec3 sourceCell = getCellPosition(source.getPosition());

        if (sourceCell.xCoord != cellPosition.xCoord
            || sourceCell.yCoord != cellPosition.yCoord
            || sourceCell.zCoord != cellPosition.zCoord) {
            throw new IllegalArgumentException("Source belongs to another pollution cell");
        }

        if (!sources.contains(source)) {
            sources.add(source);
        }
    }

    public void removeSource(PropagationSource source) {
        sources.remove(source);
    }

    public boolean update(long currentTick) {
        long elapsedTicks = currentTick - lastCheckTick;

        if (elapsedTicks <= 0L) {
            return false;
        }

        lastCheckTick = currentTick;

        double totalWeight = 0.0D;
        double weightedX = 0.0D;
        double weightedY = 0.0D;
        double weightedZ = 0.0D;

        Iterator<PropagationSource> iterator = sources.iterator();

        while (iterator.hasNext()) {
            PropagationSource source = iterator.next();

            if (!source.isValid()) {
                iterator.remove();
                continue;
            }

            double produced = source.consumeEmission();
            double averageEmission = produced / elapsedTicks;
            double oldEffective = source.getEffectiveEmission();
            double newEffective = oldEffective + smoothing * (averageEmission - oldEffective);

            if (newEffective < 1.0E-9D) {
                newEffective = 0.0D;
            }

            source.setEffectiveEmission(newEffective);

            if (newEffective <= 0.0D) {
                continue;
            }

            Vec3 pos = source.getPosition();

            totalWeight += newEffective;
            weightedX += newEffective * pos.xCoord;
            weightedY += newEffective * pos.yCoord;
            weightedZ += newEffective * pos.zCoord;
        }

        pollution = totalWeight;

        if (totalWeight > 0.0D) {
            center = Vec3.createVectorHelper(
                weightedX / totalWeight,
                weightedY / totalWeight,
                weightedZ / totalWeight
            );
        }
        boolean publish = shouldPublish();

        if (publish) {
            publishedPollution = pollution;
            publishedCenter = center;
            published = true;
        }

        return publish;
    }

    private boolean shouldPublish() {
        if (!published) {
            return pollution > 0.0D;
        }

        if (pollution == 0.0D && publishedPollution != 0.0D) {
            return true;
        }

        double denominator = Math.max(
            Math.abs(pollution),
            Math.abs(publishedPollution)
        );

        double relativeChange = denominator == 0.0D
            ? 0.0D
            : Math.abs(pollution - publishedPollution) / denominator;

        if (relativeChange >= pollutionThreshold) {
            return true;
        }

        double dx = center.xCoord - publishedCenter.xCoord;
        double dy = center.yCoord - publishedCenter.yCoord;
        double dz = center.zCoord - publishedCenter.zCoord;

        double centerShiftSquared = dx * dx + dy * dy + dz * dz;

        return centerShiftSquared >= centerThresholdSquared;
    }


    public double getInfluence(Vec3 pos) {
        Vec3 emitterPos = getPosition();

        if (emitterPos.distanceTo(pos) > propagationRange) {
            return 0.0D;
        }

        double influence = pollution;

        if (influence <= 0.0D) {
            return 0.0D;
        }

        InfluenceVector vector = new InfluenceVector(emitterPos, pos);

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

    private Vec3 getCellCenter(Vec3 cellPosition) {
        return Vec3.createVectorHelper(
            cellPosition.xCoord * 16.0D + 8.0D,
            cellPosition.yCoord * 16.0D + 8.0D,
            cellPosition.zCoord * 16.0D + 8.0D
        );
    }

    public Vec3 getPosition() {
        return center;
    }

    public double getEmissionRate() {
        return pollution;
    }

    public double getPropagationRange() {
        return propagationRange;
    }

    public List<PropagationInfluencer> getInfluencers() {
        return influencers;
    }

    public void removeInfluencer(PropagationInfluencer influencer) {
        influencers.remove(influencer);
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

    public int getSupplierCount() {
        return sources.size();
    }

    public int getDimension() {
        return dimension;
    }

    public Vec3 getCellPosition() {
        return cellPosition;
    }

    private Vec3 getCellPosition(Vec3 position) {
        return Vec3.createVectorHelper(
            ((int) Math.floor(position.xCoord)) >> 4,
            ((int) Math.floor(position.yCoord)) >> 4,
            ((int) Math.floor(position.zCoord)) >> 4
        );
    }

    public double getPollution() {
        return pollution;
    }

    public Vec3 getCenter() {
        return center;
    }

    public long getLastCheckTick() {
        return lastCheckTick;
    }
}
