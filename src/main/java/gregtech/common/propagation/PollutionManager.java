package gregtech.common.propagation;

import static gregtech.GTLoggers.GT_FML_LOGGER;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.Vec3;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

public class PollutionManager extends AbstractPropagationManager<PollutionFieldEmitter> {
    private final Map<Vec3, PollutionEmitter> pollutionEmitters = new TreeMap<>(CELL_COMPARATOR);
    private final Set<BlockPos> removedEmitterCells = new HashSet<>();
    private final Set<PollutionEmitter> dirtyEmitters = new HashSet<>();
    private final boolean trackRemovals;
    private final PollutionQueryProfiler queryProfiler = new PollutionQueryProfiler();
    private boolean queryProfilingEnabled;
    private static final int QUERY_PROFILE_REPORT_INTERVAL = 100;
    private int queryProfileTicks;

    private static final Comparator<Vec3> CELL_COMPARATOR = new Comparator<Vec3>() {
        @Override
        public int compare(Vec3 a, Vec3 b) {
            int x = Double.compare(a.xCoord, b.xCoord);
            if (x != 0) return x;
            int y = Double.compare(a.yCoord, b.yCoord);
            if (y != 0) return y;
            return Double.compare(a.zCoord, b.zCoord);
        }
    };

    public PollutionManager(int dimension) {
        this(dimension, true);
    }

    public PollutionManager(int dimension, boolean trackRemovals) {
        this(
            dimension,
            trackRemovals,
            new PollutionModel());
    }

    public PollutionManager(
        int dimension,
        boolean trackRemovals,
        PropagationModel<PollutionFieldEmitter> model) {

        super(dimension, model);
        this.trackRemovals = trackRemovals;
    }

    public void setQueryProfilingEnabled(boolean enabled) {
        queryProfilingEnabled = enabled;
        queryProfileTicks = 0;
        queryProfiler.reset();
    }

    public PollutionQueryProfiler.Snapshot consumeQueryProfile() {
        PollutionQueryProfiler.Snapshot snapshot = queryProfiler.snapshot();
        queryProfiler.reset();
        return snapshot;
    }

    public void registerEmitter(PollutionEmitter emitter) {
        registerStandardEmitter(emitter);
    }

    public List<PollutionEmitter> getStandardEmitters() {
        return Collections.unmodifiableList(
            new ArrayList<>(pollutionEmitters.values()));
    }

    private PollutionEmitter getStandardEmitter(
        EmitterGroupRef<?> groupRef,
        PollutionFieldEmitter emitter) {

        if (!PollutionEmitterDefinitions.STANDARD_ID.equals(
            groupRef.getDefinitionId())) {

            return null;
        }

        if (!(emitter instanceof PollutionEmitter)) {
            throw new IllegalStateException(
                "Standard pollution definition created "
                    + "non-standard emitter");
        }

        return (PollutionEmitter) emitter;
    }

    @Override
    protected void validateEmitterRegistration(
        EmitterGroupRef<?> groupRef,
        PollutionFieldEmitter emitter) {

        PollutionEmitter standard =
            getStandardEmitter(groupRef, emitter);

        if (standard == null) {
            return;
        }

        Vec3 cell = standard.getCellPosition();
        PollutionEmitter existing =
            pollutionEmitters.get(cell);

        if (existing == standard) {
            return;
        }

        if (existing != null) {
            throw new IllegalStateException(
                "Emitter cell already occupied: "
                    + cell.xCoord + ", "
                    + cell.yCoord + ", "
                    + cell.zCoord);
        }
    }

    @Override
    protected void onEmitterRegistered(
        EmitterGroupRef<?> groupRef,
        PollutionFieldEmitter emitter) {

        PollutionEmitter standard =
            getStandardEmitter(groupRef, emitter);

        if (standard == null) {
            return;
        }

        Vec3 cell = standard.getCellPosition();

        pollutionEmitters.put(cell, standard);

        if (trackRemovals) {
            removedEmitterCells.remove(
                new BlockPos(
                    (int) cell.xCoord,
                    (int) cell.yCoord,
                    (int) cell.zCoord));
        }
    }

    @Override
    protected void onEmitterUnregistered(
        EmitterGroupRef<?> groupRef,
        PollutionFieldEmitter emitter) {

        PollutionEmitter standard =
            getStandardEmitter(groupRef, emitter);

        if (standard == null) {
            return;
        }

        Vec3 cell = standard.getCellPosition();

        if (pollutionEmitters.get(cell) != standard) {
            return;
        }

        pollutionEmitters.remove(cell);
        dirtyEmitters.remove(standard);

        if (trackRemovals) {
            removedEmitterCells.add(
                new BlockPos(
                    (int) cell.xCoord,
                    (int) cell.yCoord,
                    (int) cell.zCoord));
        }
    }

    @Override
    protected void onEmitterStateChanged(
        PollutionFieldEmitter emitter) {

        EmitterGroupRef<?> groupRef =
            getEmitterGroupRef(emitter);

        if (groupRef == null) {
            throw new IllegalStateException(
                "Registered emitter has no group binding");
        }

        PollutionEmitter standard =
            getStandardEmitter(groupRef, emitter);

        if (standard != null) {
            dirtyEmitters.add(standard);
        }
    }

    public List<PollutionEmitter> consumeDirtyEmitters() {
        if (dirtyEmitters.isEmpty()) return Collections.emptyList();

        List<PollutionEmitter> dirty = new ArrayList<>(dirtyEmitters);
        dirtyEmitters.clear();
        return dirty;
    }

    public Set<BlockPos> consumeRemovedEmitterCells() {
        if (removedEmitterCells.isEmpty()) {
            return Collections.emptySet();
        }

        Set<BlockPos> removed =
            new HashSet<>(removedEmitterCells);

        removedEmitterCells.clear();
        return removed;
    }

    @Override
    protected double calculateSample(Vec3 position) {
        if (!queryProfilingEnabled) {
            double result = 0.0D;

            for (PollutionFieldEmitter emitter : spatialIndex.get(position)) {

                result += emitter.getInfluence(position);
            }

            return result;
        }

        long start = System.nanoTime();

        List<PollutionFieldEmitter> candidates = spatialIndex.get(position);

        queryProfiler.beginQuery(candidates.size());

        double result = 0.0D;

        for (PollutionFieldEmitter emitter : candidates) {
            result += emitter.getInfluence(
                position,
                queryProfiler);
        }

        queryProfiler.endQuery(System.nanoTime() - start);

        return result;
    }

    @Override
    protected void onTick() {
        if (queryProfilingEnabled) {
            tickQueryProfiler();
        }
    }

    private void tickQueryProfiler() {
        queryProfileTicks++;

        if (queryProfileTicks < QUERY_PROFILE_REPORT_INTERVAL) {
            return;
        }

        queryProfileTicks = 0;

        PollutionQueryProfiler.Snapshot snapshot = consumeQueryProfile();

        if (snapshot.queries == 0) {
            return;
        }

        GT_FML_LOGGER.info(
            "PollutionQueryProfiler dim={} queries={} time={}ms avg={}us/q candidates={} inside={} influencers={} noops={}% maxCandidates={} maxInfluencers={}",
            dimension,
            snapshot.queries,
            snapshot.getQueryMillis(),
            snapshot.getAverageQueryMicros(),
            snapshot.getAverageEmitterCandidates(),
            snapshot.getAverageEmittersInsideRange(),
            snapshot.getAverageInfluencerCalls(),
            snapshot.getInfluencerNoopPercent(),
            snapshot.maxEmitterCandidates,
            snapshot.maxInfluencersPerEmitter);
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList emitterList = new NBTTagList();

        for (PollutionEmitter emitter : pollutionEmitters.values()) {

            double pollution = emitter.getPollution();

            if (pollution <= 0.0D) {
                continue;
            }

            Vec3 cell = emitter.getCellPosition();

            NBTTagCompound emitterTag = new NBTTagCompound();
            emitterTag.setInteger("CellX", (int) cell.xCoord);
            emitterTag.setInteger("CellY", (int) cell.yCoord);
            emitterTag.setInteger("CellZ", (int) cell.zCoord);
            emitterTag.setDouble("Pollution", pollution);

            emitterList.appendTag(emitterTag);
        }

        nbt.setTag("Emitters", emitterList);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList emitterList = nbt.getTagList("Emitters", 10);

        for (int i = 0; i < emitterList.tagCount(); i++) {
            NBTTagCompound emitterTag = emitterList.getCompoundTagAt(i);

            Vec3 cellPosition = Vec3.createVectorHelper(
                emitterTag.getInteger("CellX"),
                emitterTag.getInteger("CellY"),
                emitterTag.getInteger("CellZ"));

            double pollution = emitterTag.getDouble("Pollution");

            if (pollution <= 0.0D) {
                continue;
            }

            PollutionEmitter emitter = new PollutionEmitter(dimension, cellPosition, 256);

            emitter.setPollution(pollution);

            registerStandardEmitter(emitter);
        }
    }

    @Override
    protected void onEmitterRegistrationFailed(
        EmitterGroupRef<?> groupRef,
        PollutionFieldEmitter emitter) {

        PollutionEmitter standard =
            getStandardEmitter(groupRef, emitter);

        if (standard == null) {
            return;
        }

        Vec3 cell = standard.getCellPosition();

        if (pollutionEmitters.get(cell) == standard) {
            pollutionEmitters.remove(cell);
        }

        dirtyEmitters.remove(standard);
    }

    public void applyEmitterSnapshot(int[] cellX, int[] cellY, int[] cellZ, double[] pollution) {
        if (cellX.length != pollution.length
            || cellY.length != pollution.length
            || cellZ.length != pollution.length) {

            throw new IllegalArgumentException("Emitter snapshot length mismatch");
        }

        for (double value : pollution) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Invalid emitter pollution: " + value);
            }
        }

        Set<Vec3> received = new TreeSet<>(CELL_COMPARATOR);

        for (int i = 0; i < pollution.length; i++) {

            Vec3 cell = Vec3.createVectorHelper(cellX[i], cellY[i], cellZ[i]);
            received.add(cell);

            PollutionEmitter emitter = pollutionEmitters.get(cell);

            if (emitter == null) {
                emitter = new PollutionEmitter(dimension, cell, 256);
                emitter.setPollution(pollution[i]);
            } else {
                emitter.setPollution(pollution[i]);
            }

            registerStandardEmitter(emitter);

            dirtyEmitters.remove(emitter);
        }

        List<PollutionEmitter> removed = new ArrayList<>();

        for (Map.Entry<Vec3, PollutionEmitter> entry : pollutionEmitters.entrySet()) {
            if (!received.contains(entry.getKey())) {
                removed.add(entry.getValue());
            }
        }

        for (PollutionEmitter emitter : removed) {
            unregisterEmitter(emitter);
        }
    }

    private void registerStandardEmitter(PollutionEmitter emitter) {
        Vec3 cell = emitter.getCellPosition();

        CellGroupKey groupKey = new CellGroupKey(
            (int) cell.xCoord,
            (int) cell.yCoord,
            (int) cell.zCoord);

        EmitterGroupRef<CellGroupKey> groupRef = new EmitterGroupRef<>(
            PollutionEmitterDefinitions.STANDARD_ID,
            groupKey);

        registerGroupedEmitter(groupRef, emitter);
    }

    public void setEmitterPollution(int cellX, int cellY, int cellZ, double pollution) {
        if (!Double.isFinite(pollution)) {
            throw new IllegalArgumentException("Invalid emitter pollution: " + pollution);
        }

        Vec3 cellPosition = Vec3.createVectorHelper(cellX, cellY, cellZ);
        PollutionEmitter emitter = pollutionEmitters.get(cellPosition);

        if (pollution <= 0.0D) {
            if (emitter != null) {
                unregisterEmitter(emitter);
            }

            return;
        }

        if (emitter == null) {
            emitter = new PollutionEmitter(dimension, cellPosition,256);

            emitter.setPollution(pollution);
            registerStandardEmitter(emitter);
        } else {
            emitter.setPollution(pollution);
        }
    }

    public void addPollution(int x, int y, int z, double pollution) {
        if (!Double.isFinite(pollution)) {
            throw new IllegalArgumentException("Invalid pollution: " + pollution);
        }

        if (pollution == 0.0D) {
            return;
        }

        Vec3 cell = getCellPosition(
            Vec3.createVectorHelper(x, y, z));

        PollutionEmitter emitter = pollutionEmitters.get(cell);

        if (emitter == null) {
            if (pollution < 0.0D) {
                return;
            }

            emitter = new PollutionEmitter(
                dimension,
                cell,
                256);

            emitter.setPollution(pollution);
            registerStandardEmitter(emitter);
        } else {
            double newPollution =
                emitter.getPollution() + pollution;

            if (!Double.isFinite(newPollution)) {
                throw new IllegalArgumentException(
                    "Pollution overflow");
            }

            emitter.setPollution(newPollution);
        }

        dirtyEmitters.add(emitter);
    }

    public void unregisterEmitter(PollutionEmitter emitter) {
        if (emitter == null) {
            return;
        }

        Vec3 cell = emitter.getCellPosition();

        if (pollutionEmitters.get(cell) != emitter) {
            return;
        }

        unregisterEmitterManaged(emitter);
    }

    private static void validatePosition(Vec3 position, String type) {
        if (position == null || !Double.isFinite(position.xCoord)
            || !Double.isFinite(position.yCoord)
            || !Double.isFinite(position.zCoord)) {

            throw new IllegalArgumentException("Invalid " + type + " position");
        }
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
}
