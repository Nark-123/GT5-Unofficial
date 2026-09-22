package gregtech.common.propagation;

import net.minecraft.util.Vec3;

import java.util.List;

public interface PropagationEmitter {
    Vec3 getPosition();

    double getEmissionRate();

    double getPropagationRange();

    double getInfluence(Vec3 pos);

    boolean update(long tick);

    List<PropagationInfluencer> getInfluencers();

    void addInfluencer(PropagationInfluencer influencer);

    void removeInfluencer(PropagationInfluencer influencer);

    PropagationType getType();

    boolean isValid();
}

