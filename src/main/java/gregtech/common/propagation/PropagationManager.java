package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public interface PropagationManager {

    void registerEmitter(PropagationEmitter emitter);

    void unregisterEmitter(PropagationEmitter emitter);

    void registerInfluencer(PropagationInfluencer influencer);

    void unregisterInfluencer(PropagationInfluencer influencer);

    float getPollution(Vec3 pos);

    void tick(int tick);
}
