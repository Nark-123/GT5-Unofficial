package gregtech.common.propagation;

import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PropagationSpatialIndex {

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

    private final Map<Vec3, List<PollutionEmitter>> batches = new TreeMap<>(BATCH_COMPARATOR);


    public void add(PollutionEmitter emitter) {
        Vec3 pos = emitter.getPosition();
        double range = emitter.getPropagationRange();

        Vec3 min = getBatchPosition(Vec3.createVectorHelper(
            pos.xCoord - range,
            pos.yCoord - range,
            pos.zCoord - range
        ));

        Vec3 max = getBatchPosition(Vec3.createVectorHelper(
            pos.xCoord + range,
            pos.yCoord + range,
            pos.zCoord + range
        ));

        for (int x = (int) min.xCoord; x <= (int) max.xCoord; x++) {
            for (int y = (int) min.yCoord; y <= (int) max.yCoord; y++) {
                for (int z = (int) min.zCoord; z <= (int) max.zCoord; z++) {
                    getOrCreate(Vec3.createVectorHelper(x, y, z)).add(emitter);
                }
            }
        }
    }


    public void remove(PollutionEmitter emitter) {
        for (java.util.Iterator<Map.Entry<Vec3, List<PollutionEmitter>>> iterator = batches.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<Vec3, List<PollutionEmitter>> entry = iterator.next();

            entry.getValue().remove(emitter);

            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }


    public List<PollutionEmitter> get(Vec3 position) {
        List<PollutionEmitter> result = batches.get(getBatchPosition(position));

        if (result == null) {
            return java.util.Collections.emptyList();
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
            getBatchCoordinate(pos.zCoord)
        );
    }


    private int getBatchCoordinate(double coordinate) {
        return (int) Math.floor(coordinate / BATCH_SIZE);
    }
}
