package gregtech.common.propagation;

import net.minecraft.util.Vec3;

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
        this.dimension = dimension;
    }

    @Override
    public void registerSource(PropagationSource source) {
        if (source.getDimension() != dimension) {
            throw new IllegalArgumentException("Source belongs to another dimension");
        }

        Vec3 cellPosition = getCellPosition(source.getPosition());
        PollutionEmitter emitter = pollutionEmitters.get(cellPosition);

        if (emitter == null) {
            emitter = new PollutionEmitter(dimension, cellPosition, 50);
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
        if (emitters.contains(emitter)) {
            return;
        }

        emitters.add(emitter);
        spatialIndex.add(emitter);

        for (PropagationInfluencer influencer : influencers) {
            if (canInfluence(emitter, influencer)) {
                emitter.addInfluencer(influencer);
            }
        }
    }

    public void unregisterEmitter(PollutionEmitter emitter) {
        emitters.remove(emitter);
        spatialIndex.remove(emitter);
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
    public float sample(Vec3 pos) {
        double result = 0.0D;

        for (PollutionEmitter emitter : spatialIndex.get(pos)) {
            result += emitter.getInfluence(pos);
        }

        return (float) result;
    }

    @Override
    public void tick(int tick) {
        tickEmitters(tick);
        tickInfluencers();
    }

    private void tickEmitters(int tick) {
        if (emitters.isEmpty()) {
            updateCursor = 0;
            return;
        }

        int emitterUpdateBudget = emitters.size();
        int updates = emitterUpdateBudget / UPDATE_INTERVAL;

        for (int i = 0; i < updates && !emitters.isEmpty(); i++) {
            if (updateCursor >= emitters.size()) {
                updateCursor = 0;
            }

            PollutionEmitter emitter = emitters.get(updateCursor);

            if (!emitter.isValid()) {
                unregisterEmitter(emitter);
                continue;
            }

            emitter.update(tick);
            updateCursor++;
        }
    }

    private void tickInfluencers() {
        if (influencers.isEmpty()) {
            influencerUpdateCursor = 0;
            return;
        }

        int influencerUpdateBudget = influencers.size();
        int updates = influencerUpdateBudget / UPDATE_INTERVAL;

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
