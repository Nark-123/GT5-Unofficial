package gregtech.common.propagation;

import net.minecraft.util.Vec3;

import java.util.*;

public class PollutionManager implements PropagationManager {

    private final List<PropagationEmitter> emitters = new ArrayList<>();
    private final List<PropagationInfluencer> influencers = new ArrayList<>();

    private final PropagationSpatialIndex spatialIndex = new PropagationSpatialIndex();

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
        influencers.remove(influencer);
    }

    @Override
    public float getPollution(Vec3 pos) {
        float result = 0.0F;

        for (PropagationEmitter emitter : spatialIndex.get(pos)) {

            Vec3 emitterPos = emitter.getPosition();

            double distance = emitterPos.distanceTo(pos);

            if (distance > emitter.getPropagationRange()) {
                continue;
            }

            double influence = emitter.getEmissionRate();

            InfluenceVector vector =
                new InfluenceVector(emitterPos, pos);

            for (PropagationInfluencer influencer : emitter.getInfluencers()) {
                influence *= influencer.influence(pos, vector);
            }

            result = (float) (result + influence);
        }

        return result;
    }

    @Override
    public void tick() {
        // обновление эмиттеров
    }

    private boolean canInfluence(PropagationEmitter emitter, PropagationInfluencer influencer) {
        double range = emitter.getPropagationRange() + influencer.getRange();
        double distance = emitter.getPosition().distanceTo(influencer.getPosition());
        return distance <= range;
    }
}
