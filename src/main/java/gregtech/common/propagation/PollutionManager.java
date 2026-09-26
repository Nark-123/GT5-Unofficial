package gregtech.common.propagation;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.*;

public class PollutionManager implements PropagationManager {

    private final List<PollutionEmitter> emitters = new ArrayList<>();
    private final List<PropagationInfluencer> influencers = new ArrayList<>();
    private final PropagationSpatialIndex spatialIndex = new PropagationSpatialIndex();
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

    @Override
    public void registerSource(PropagationSource source) {
        if (source.getDimension() != dimension) {
            throw new IllegalArgumentException("Source belongs to another dimension");
        }

        Vec3 cellPosition = getCellPosition(source.getPosition());
        PollutionEmitter emitter = pollutionEmitters.get(cellPosition);

        if (emitter == null) {
            emitter = new PollutionEmitter(dimension, cellPosition, 256);
            pollutionEmitters.put(cellPosition, emitter);
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

        emitter.removeSource(source);
    }

    public void registerEmitter(PollutionEmitter emitter) {
        if (emitters.contains(emitter)) return;

        emitters.add(emitter);
        spatialIndex.add(emitter);

        if (trackRemovals) {
            Vec3 cell = emitter.getCellPosition();
            removedEmitterCells.remove(new BlockPos(
                (int) cell.xCoord,
                (int) cell.yCoord,
                (int) cell.zCoord
            ));
        }

        for (PropagationInfluencer influencer : influencers) {
            if (canInfluence(emitter, influencer)) {
                emitter.addInfluencer(influencer);
            }
        }
    }

    public void unregisterEmitter(PollutionEmitter emitter) {
        emitters.remove(emitter);
        spatialIndex.remove(emitter);
        pollutionEmitters.remove(emitter.getCellPosition());
        dirtyEmitters.remove(emitter);

        if (trackRemovals) {
            Vec3 cell = emitter.getCellPosition();
            removedEmitterCells.add(new BlockPos(
                (int) cell.xCoord,
                (int) cell.yCoord,
                (int) cell.zCoord
            ));
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
        if (influencers.contains(influencer)) {
            return;
        };

        influencers.add(influencer);

        for (PollutionEmitter emitter : emitters) {
            if (canInfluence(emitter, influencer)) {
                emitter.addInfluencer(influencer);
            }
        }
    }

    @Override
    public void unregisterInfluencer(PropagationInfluencer influencer) {
        if (!influencers.remove(influencer)) return;

        for (PollutionEmitter emitter : emitters) {
            emitter.removeInfluencer(influencer);
        }
    }

    @Override
    public float sample(BlockPos pos) {
        Vec3 vec = Vec3.createVectorHelper(pos.x, pos.y, pos.z);
        double result = 0.0D;

        for (PollutionEmitter emitter : spatialIndex.get(vec)) {
            result += emitter.getInfluence(vec);
        }

        return (float) result;
    }

    @Override
    public void tick() {
        tickEmitters();
        tickInfluencers();
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

            if (!emitter.isValid()) {
                unregisterEmitter(emitter);
                continue;
            }

            if (emitter.update()) dirtyEmitters.add(emitter);
            updateCursor++;
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

            PropagationInfluencer influencer =
                influencers.get(influencerUpdateCursor);

            if (!influencer.isValid()) {
                unregisterInfluencer(influencer);
                continue;
            }

            for (PollutionEmitter emitter : emitters) {
                if (canInfluence(emitter, influencer)) {
                    emitter.addInfluencer(influencer);
                } else {
                    emitter.removeInfluencer(influencer);
                }
            }

            influencerUpdateCursor++;
        }
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList emitterList = new NBTTagList();

        for (PollutionEmitter emitter : emitters) {
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
                emitterTag.getInteger("CellZ")
            );

            double pollution = emitterTag.getDouble("Pollution");

            if (pollution <= 0.0D) {
                continue;
            }

            PollutionEmitter emitter =
                new PollutionEmitter(dimension, cellPosition, 256);

            emitter.setPollution(pollution);

            pollutionEmitters.put(cellPosition, emitter);
            registerEmitter(emitter);
        }
    }

    public void applyEmitterSnapshot(
        int[] cellX,
        int[] cellY,
        int[] cellZ,
        double[] pollution
    ) {
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
            pollutionEmitters.put(cellPosition, emitter);
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
            pollutionEmitters.put(cell, emitter);
            registerEmitter(emitter);
        }

        emitter.setPollution(Math.max(0.0D, emitter.getPollution() + pollution));
        dirtyEmitters.add(emitter);
    }

    private boolean canInfluence(PollutionEmitter emitter, PropagationInfluencer influencer) {
        double range = emitter.getPropagationRange() + influencer.getRange();
        double distance = emitter.getPosition().distanceTo(influencer.getPosition());
        return distance <= range;
    }

    private Vec3 getCellPosition(Vec3 position) {
        return Vec3.createVectorHelper(
            ((int) Math.floor(position.xCoord)) >> 4,
            ((int) Math.floor(position.yCoord)) >> 4,
            ((int) Math.floor(position.zCoord)) >> 4
        );
    }
}
