package gregtech.common.propagation;

import net.minecraft.util.Vec3;

import java.util.*;

public class PollutionManager implements PropagationManager {

    private final List<PropagationEmitter> emitters = new ArrayList<>();
    private final List<PropagationInfluencer> influencers = new ArrayList<>();
    private final PropagationSpatialIndex spatialIndex = new PropagationSpatialIndex();
    private static final int UPDATE_INTERVAL = 20;
    private int updateCursor;

    @Override
    public void registerEmitter(PropagationEmitter emitter) {
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

    @Override
    public void unregisterEmitter(PropagationEmitter emitter) {
        emitters.remove(emitter);
        spatialIndex.remove(emitter);
    }

    @Override
    public void registerInfluencer(PropagationInfluencer influencer) {
        if (influencers.contains(influencer)) {
            return;
        };

        influencers.add(influencer);

        for (PropagationEmitter emitter : emitters) {
            if (canInfluence(emitter, influencer)) {
                emitter.addInfluencer(influencer);
            }
        }
    }

    @Override
    public void unregisterInfluencer(PropagationInfluencer influencer) {
        if (!influencers.remove(influencer)) return;

        for (PropagationEmitter emitter : emitters) {
            emitter.removeInfluencer(influencer);
        }
    }

    @Override
    public float getPollution(Vec3 pos) {
        double result = 0.0D;

        for (PropagationEmitter emitter : spatialIndex.get(pos)) {
            result += emitter.getInfluence(pos);
        }

        return (float) result;
    }

    @Override
    public void tick(int tick) {
        if (emitters.isEmpty()) {
            updateCursor = 0;
            return;
        }

        int updates = Math.max(1, (emitters.size() + UPDATE_INTERVAL - 1) / UPDATE_INTERVAL);

        for (int i = 0; i < updates && !emitters.isEmpty(); i++) {
            if (updateCursor >= emitters.size()) {
                updateCursor = 0;
            }

            PropagationEmitter emitter = emitters.get(updateCursor);

            if (!emitter.isValid()) {
                unregisterEmitter(emitter);
                continue;
            }

            emitter.update(tick);
            updateCursor++;
        }
    }

    private boolean canInfluence(PropagationEmitter emitter, PropagationInfluencer influencer) {
        double range = emitter.getPropagationRange() + influencer.getRange();
        double distance = emitter.getPosition().distanceTo(influencer.getPosition());
        return distance <= range;
    }
}
