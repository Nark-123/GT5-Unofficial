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

public class PropagationSpatialIndex {

    private static final int BATCH_SIZE = 64;

    private final Map<PollutionEmitter, PropagationBatchBounds> emitterBounds = new IdentityHashMap<>();
    private final Map<PollutionEmitter, Long> emitterRevisions = new IdentityHashMap<>();

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

    private final Map<Vec3, List<PollutionEmitter>> batches = new TreeMap<>(BATCH_COMPARATOR);

    public void add(PollutionEmitter emitter) {
        if (emitterBounds.containsKey(emitter)) {
            return;
        }

        PropagationBatchBounds bounds = getRequiredBounds(emitter);

        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    getOrCreate(Vec3.createVectorHelper(x, y, z)).add(emitter);
                }
            }
        }

        emitterBounds.put(emitter, bounds);
        emitterRevisions.put(emitter, emitter.getPropagationRevision());
    }

    public boolean ensureCoverage(PollutionEmitter emitter) {
        long revision = emitter.getPropagationRevision();
        Long indexedRevision = emitterRevisions.get(emitter);

        if (indexedRevision != null && indexedRevision == revision) return false;

        emitterRevisions.put(emitter, revision);

        PropagationBatchBounds current = emitterBounds.get(emitter);

        if (current == null) {
            add(emitter);
            return true;
        }

        PropagationBatchBounds required = getRequiredBounds(emitter);

        if (current.contains(required)) return false;

        PropagationBatchBounds expanded = current.expandToInclude(required);

        for (int x = expanded.minX; x <= expanded.maxX; x++) {
            for (int y = expanded.minY; y <= expanded.maxY; y++) {
                for (int z = expanded.minZ; z <= expanded.maxZ; z++) {
                    if (current.contains(x, y, z)) continue;

                    getOrCreate(Vec3.createVectorHelper(x, y, z)).add(emitter);
                }
            }
        }

        emitterBounds.put(emitter, expanded);
        return true;
    }

    private PropagationBatchBounds getRequiredBounds(PollutionEmitter emitter) {
        return getRequiredBounds(emitter.getPosition(), emitter.getPropagationRange());
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

    public void remove(PollutionEmitter emitter) {
        PropagationBatchBounds bounds = emitterBounds.remove(emitter);

        if (bounds == null) {
            return;
        }

        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    Vec3 batch = Vec3.createVectorHelper(x, y, z);
                    List<PollutionEmitter> list = batches.get(batch);

                    if (list == null) {
                        continue;
                    }

                    list.remove(emitter);

                    if (list.isEmpty()) {
                        batches.remove(batch);
                    }
                }
            }
        }

        emitterRevisions.remove(emitter);
    }

    public Set<PollutionEmitter> get(Vec3 position, double range) {
        PropagationBatchBounds bounds = getRequiredBounds(position, range);
        Set<PollutionEmitter> result = Collections.newSetFromMap(new IdentityHashMap<>());

        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    List<PollutionEmitter> entries = batches.get(Vec3.createVectorHelper(x, y, z));

                    if (entries != null) {
                        result.addAll(entries);
                    }
                }
            }
        }

        return result;
    }

    public List<PollutionEmitter> get(Vec3 position) {
        List<PollutionEmitter> result = batches.get(getBatchPosition(position));

        if (result == null) {
            return Collections.emptyList();
        }

        return result;
    }

    private List<PollutionEmitter> getOrCreate(Vec3 position) {
        return batches.computeIfAbsent(position, k -> new ArrayList<>());
    }

    private Vec3 getBatchPosition(Vec3 pos) {
        return Vec3.createVectorHelper(
            getBatchCoordinate(pos.xCoord),
            getBatchCoordinate(pos.yCoord),
            getBatchCoordinate(pos.zCoord));
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

    private static final class BatchBounds {

        final int minX, minY, minZ;
        final int maxX, maxY, maxZ;

        BatchBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        boolean contains(BatchBounds other) {
            return other.minX >= minX && other.minY >= minY
                && other.minZ >= minZ
                && other.maxX <= maxX
                && other.maxY <= maxY
                && other.maxZ <= maxZ;
        }

        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        BatchBounds expandToInclude(BatchBounds other) {
            return new BatchBounds(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY),
                Math.max(maxZ, other.maxZ));
        }
    }
}
