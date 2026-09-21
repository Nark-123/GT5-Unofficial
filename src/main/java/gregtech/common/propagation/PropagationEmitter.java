package gregtech.common.propagation;

import net.minecraft.util.Vec3;

import java.util.List;

public interface PropagationEmitter {
    Vec3 getPosition();

    double getEmissionRate();

    double getPropagationRange();

    List<PropagationInfluencer> getInfluencers();

    void addInfluencer(PropagationInfluencer influencer);

    PropagationType getType();
}

