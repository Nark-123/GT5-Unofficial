package gregtech.common.propagation;

import static gregtech.GTLoggers.GT_FML_LOGGER;

import java.util.ArrayList;
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

public class PollutionManager implements PropagationManager {

    private final List<PollutionEmitter> emitters = new ArrayList<>();
    private final List<PropagationInfluencer> influencers = new ArrayList<>();
    private final PropagationSpatialIndex spatialIndex = new PropagationSpatialIndex();
    private final PropagationInfluencerSpatialIndex influencerSpatialIndex = new PropagationInfluencerSpatialIndex();
    private final Map<Vec3, PollutionEmitter> pollutionEmitters = new TreeMap<>(CELL_COMPARATOR);
    private static final int UPDATE_INTERVAL = 20;
    private int updateCursor;
    private int influencerUpdateCursor;
    private final int dimension;
    private int emitterUpdateAccumulator;
    private int influencerUpdateAccumulator;
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
        this.dimension = dimension;
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

    @Override
    public void registerSource(PropagationSource source) {
        if (source.getDimension() != dimension) {
            throw new IllegalArgumentException("Source belongs to another dimension");
        }

        Vec3 cellPosition = getCellPosition(source.getPosition());
        PollutionEmitter emitter = pollutionEmitters.get(cellPosition);

        if (emitter == null) {
            emitter = new PollutionEmitter(dimension, cellPosition, 256);
            registerEmitter(emitter);
        }

        emitter.addSource(source);
    }

    @Override
    public void unregisterSource(PropagationSource source) {
        if (source.getDimension() != dimension) return;

        Vec3 cellPosition = getCellPosition(source.getPosition());
        PollutionEmitter emitter = pollutionEmitters.get(cellPosition);

        if (emitter == null) return;

        if (emitter.removeSource(source)) {
            dirtyEmitters.add(emitter);
        }
    }

    public void registerEmitter(PollutionEmitter emitter) {
        if (emitter.getDimension() != dimension) {
            throw new IllegalArgumentException("Emitter belongs to another dimension");
        }

        PollutionEmitter existing = pollutionEmitters.get(emitter.getCellPosition());

        if (existing == emitter) return;

        //I guess this may produce an incorrect result
        if (existing != null) {
            unregisterEmitter(existing);
        }

        pollutionEmitters.put(emitter.getCellPosition(), emitter);
        emitters.add(emitter);
        spatialIndex.add(emitter);
        updateInfluencerCandidates(emitter);

        if (trackRemovals) {
            Vec3 cell = emitter.getCellPosition();
            removedEmitterCells.remove(new BlockPos((int) cell.xCoord, (int) cell.yCoord, (int) cell.zCoord));
        }
    }

    public void unregisterEmitter(PollutionEmitter emitter) {
        emitters.remove(emitter);
        spatialIndex.remove(emitter);
        dirtyEmitters.remove(emitter);

        Vec3 cell = emitter.getCellPosition();

        if (pollutionEmitters.get(cell) != emitter) {
            return;
        }

        pollutionEmitters.remove(cell);

        if (trackRemovals) {
            removedEmitterCells.add(new BlockPos((int) cell.xCoord, (int) cell.yCoord, (int) cell.zCoord));
        }
    }

    public Set<BlockPos> consumeRemovedEmitterCells() {
        if (removedEmitterCells.isEmpty()) return Collections.emptySet();

        Set<BlockPos> removed = new HashSet<>(removedEmitterCells);
        removedEmitterCells.clear();
        return removed;
    }

    public List<PollutionEmitter> getEmitters() {
        return Collections.unmodifiableList(emitters);
    }

    public List<PollutionEmitter> consumeDirtyEmitters() {
        if (dirtyEmitters.isEmpty()) return Collections.emptyList();

        List<PollutionEmitter> dirty = new ArrayList<>(dirtyEmitters);
        dirtyEmitters.clear();
        return dirty;
    }

    @Override
    public void registerInfluencer(PropagationInfluencer influencer) {
        if (influencers.contains(influencer)) return;

        influencers.add(influencer);
        influencerSpatialIndex.add(influencer);

        double range = influencerSpatialIndex.getCoverageRange(influencer);

        for (PollutionEmitter emitter : spatialIndex.get(influencer.getPosition(), range)) {
            emitter.addInfluencer(influencer);
        }
    }

    @Override
    public void unregisterInfluencer(PropagationInfluencer influencer) {
        if (!influencers.remove(influencer)) return;

        double range = influencerSpatialIndex.getCoverageRange(influencer);

        for (PollutionEmitter emitter : spatialIndex.get(influencer.getPosition(), range)) {
            emitter.removeInfluencer(influencer);
        }

        influencerSpatialIndex.remove(influencer);
    }

    @Override
    public float sample(BlockPos pos) {
        if (!queryProfilingEnabled) {
            Vec3 vec = Vec3.createVectorHelper(pos.x, pos.y, pos.z);
            double result = 0.0D;

            for (PollutionEmitter emitter : spatialIndex.get(vec)) {
                result += emitter.getInfluence(vec);
            }

            return (float) result;
        }

        long start = System.nanoTime();

        Vec3 vec = Vec3.createVectorHelper(pos.x, pos.y, pos.z);
        List<PollutionEmitter> candidates = spatialIndex.get(vec);

        queryProfiler.beginQuery(candidates.size());

        double result = 0.0D;

        for (PollutionEmitter emitter : candidates) {
            result += emitter.getInfluenceProfiled(vec, queryProfiler);
        }

        queryProfiler.endQuery(System.nanoTime() - start);

        return (float) result;
    }

    @Override
    public void tick() {
        tickEmitters();
        tickInfluencers();

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

    public int getEmitterCount() {
        return emitters.size();
    }

    public int getInfluencerCount() {
        return influencers.size();
    }

    private void tickEmitters() {
        if (emitters.isEmpty()) {
            updateCursor = 0;
            emitterUpdateAccumulator = 0;
            return;
        }

        emitterUpdateAccumulator += emitters.size();

        int updates = emitterUpdateAccumulator / UPDATE_INTERVAL;
        emitterUpdateAccumulator %= UPDATE_INTERVAL;

        for (int i = 0; i < updates && !emitters.isEmpty(); i++) {
            if (updateCursor >= emitters.size()) updateCursor = 0;

            PollutionEmitter emitter = emitters.get(updateCursor);

            boolean changed = emitter.update();

            if (emitter.consumePollutionChanged()) {
                changed = true;
            }

            if (changed) {
                dirtyEmitters.add(emitter);
            }

            if (!emitter.isValid()) {
                unregisterEmitter(emitter);
                continue;
            }

            if (spatialIndex.ensureCoverage(emitter)) {
                updateInfluencerCandidates(emitter);
            }

            updateCursor++;
        }
    }

    private void updateInfluencerCandidates(PollutionEmitter emitter) {
        double range = influencerSpatialIndex.getCoverageRange(emitter.getPropagationRange());

        for (PropagationInfluencer influencer : influencerSpatialIndex.get(emitter.getPosition(), range)) {
            emitter.addInfluencer(influencer);
        }
    }

    private void tickInfluencers() {
        if (influencers.isEmpty()) {
            influencerUpdateCursor = 0;
            influencerUpdateAccumulator = 0;
            return;
        }

        influencerUpdateAccumulator += influencers.size();

        int updates = influencerUpdateAccumulator / UPDATE_INTERVAL;
        influencerUpdateAccumulator %= UPDATE_INTERVAL;

        for (int i = 0; i < updates && !influencers.isEmpty(); i++) {
            if (influencerUpdateCursor >= influencers.size()) {
                influencerUpdateCursor = 0;
            }

            PropagationInfluencer influencer = influencers.get(influencerUpdateCursor);

            if (!influencer.isValid()) {
                unregisterInfluencer(influencer);
                continue;
            }

            influencerUpdateCursor++;
        }
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList emitterList = new NBTTagList();

        for (PollutionEmitter emitter : emitters) {
            if (emitter.flushPendingEmissions()) dirtyEmitters.add(emitter);

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

            registerEmitter(emitter);
        }
    }

    public void applyEmitterSnapshot(int[] cellX, int[] cellY, int[] cellZ, double[] pollution) {
        Set<Vec3> received = new TreeSet<>(CELL_COMPARATOR);

        for (int i = 0; i < pollution.length; i++) {
            if (pollution[i] <= 0.0D) continue;

            Vec3 cell = Vec3.createVectorHelper(cellX[i], cellY[i], cellZ[i]);
            received.add(cell);

            PollutionEmitter emitter = pollutionEmitters.get(cell);

            if (emitter == null) {
                emitter = new PollutionEmitter(dimension, cell, 256);
                pollutionEmitters.put(cell, emitter);
                registerEmitter(emitter);
            }

            emitter.setPollution(pollution[i]);
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

    public void setEmitterPollution(int cellX, int cellY, int cellZ, double pollution) {
        Vec3 cellPosition = Vec3.createVectorHelper(cellX, cellY, cellZ);
        PollutionEmitter emitter = pollutionEmitters.get(cellPosition);

        if (pollution <= 0.0D) {
            if (emitter != null) unregisterEmitter(emitter);
            return;
        }

        if (emitter == null) {
            emitter = new PollutionEmitter(dimension, cellPosition, 256);
            registerEmitter(emitter);
        }

        emitter.setPollution(pollution);
        dirtyEmitters.remove(emitter);
    }

    public void addPollution(int x, int y, int z, double pollution) {
        Vec3 cell = getCellPosition(Vec3.createVectorHelper(x, y, z));
        PollutionEmitter emitter = pollutionEmitters.get(cell);

        if (emitter == null) {
            emitter = new PollutionEmitter(dimension, cell, 256);
            registerEmitter(emitter);
        }

        emitter.setPollution(Math.max(0.0D, emitter.getPollution() + pollution));
        dirtyEmitters.add(emitter);
    }

    private Vec3 getCellPosition(Vec3 position) {
        return Vec3.createVectorHelper(
            ((int) Math.floor(position.xCoord)) >> 4,
            ((int) Math.floor(position.yCoord)) >> 4,
            ((int) Math.floor(position.zCoord)) >> 4);
    }
}
