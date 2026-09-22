package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public interface PropagationManager {

    void registerSource(PropagationSource source);

    void unregisterSource(PropagationSource source);

    void registerInfluencer(PropagationInfluencer influencer);

    void unregisterInfluencer(PropagationInfluencer influencer);

    float sample(Vec3 pos);

    void tick(int tick);
}
