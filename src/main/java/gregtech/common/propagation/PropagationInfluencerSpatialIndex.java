package gregtech.common.propagation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.util.Vec3;

public class PropagationInfluencerSpatialIndex {

    private static final int BATCH_SIZE = 64;

    private static final Comparator<Vec3> BATCH_COMPARATOR = new Comparator<Vec3>() {

        @Override
        public int compare(Vec3 a, Vec3 b) {
            int x = Double.compare(a.xCoord, b.xCoord);
            if (x != 0) return x;

            int y = Double.compare(a.yCoord, b.yCoord);
            if (y != 0) return y;

            return Double.compare(a.zCoord, b.zCoord);
        }
    };

    private final Map<Vec3, List<PropagationInfluencer>> batches = new TreeMap<>(BATCH_COMPARATOR);
    private final Map<PropagationInfluencer, PropagationBatchBounds> influencerBounds = new IdentityHashMap<>();

    public void add(PropagationInfluencer influencer) {
        if (influencerBounds.containsKey(influencer)) return;

        PropagationBatchBounds bounds = getRequiredBounds(influencer);

        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    getOrCreate(Vec3.createVectorHelper(x, y, z)).add(influencer);
                }
            }
        }

        influencerBounds.put(influencer, bounds);
    }

    public Set<PropagationInfluencer> get(Vec3 position, double range) {
        PropagationBatchBounds bounds = getRequiredBounds(position, range);
        Set<PropagationInfluencer> result = Collections.newSetFromMap(new IdentityHashMap<>());

        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    List<PropagationInfluencer> entries = batches.get(Vec3.createVectorHelper(x, y, z));

                    if (entries != null) {
                        result.addAll(entries);
                    }
                }
            }
        }

        return result;
    }

    public void remove(PropagationInfluencer influencer) {
        PropagationBatchBounds bounds = influencerBounds.remove(influencer);

        if (bounds == null) return;

        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    Vec3 batch = Vec3.createVectorHelper(x, y, z);
                    List<PropagationInfluencer> entries = batches.get(batch);

                    if (entries == null) continue;

                    entries.remove(influencer);

                    if (entries.isEmpty()) {
                        batches.remove(batch);
                    }
                }
            }
        }
    }

    public double getCoverageRange(PropagationInfluencer influencer) {
        return influencer.getRange() + BATCH_SIZE;
    }

    public double getCoverageRange(double range) {
        return range + BATCH_SIZE;
    }

    private PropagationBatchBounds getRequiredBounds(PropagationInfluencer influencer) {
        Vec3 pos = influencer.getPosition();
        double range = influencer.getRange();

        return getRequiredBounds(pos, range);
    }

    private PropagationBatchBounds getRequiredBounds(Vec3 position, double range) {
        return new PropagationBatchBounds(
            getBatchCoordinate(position.xCoord - range),
            getBatchCoordinate(position.yCoord - range),
            getBatchCoordinate(position.zCoord - range),
            getBatchCoordinate(position.xCoord + range),
            getBatchCoordinate(position.yCoord + range),
            getBatchCoordinate(position.zCoord + range));
    }

    private List<PropagationInfluencer> getOrCreate(Vec3 position) {
        return batches.computeIfAbsent(position, key -> new ArrayList<>());
    }

    private int getBatchCoordinate(double coordinate) {
        if (!Double.isFinite(coordinate)) {
            throw new IllegalArgumentException("Invalid batch coordinate: " + coordinate);
        }

        double batch = Math.floor(coordinate / BATCH_SIZE);

        if (batch < Integer.MIN_VALUE || batch > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Batch coordinate out of range: " + coordinate);
        }

        return (int) batch;
    }
}
