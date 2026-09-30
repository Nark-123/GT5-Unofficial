package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public interface PropagationEmitter {

    int getDimension();

    Vec3 getPosition();

    double getPropagationRange();

    long getPropagationRevision();

    void addSource(PropagationSource source);

    void addInfluencer(PropagationInfluencer influencer);

    void removeInfluencer(PropagationInfluencer influencer);

    double getInfluence(Vec3 position);

    void update();

    boolean isValid();

    boolean consumeStateChanged();

    boolean acceptsInfluencer(PropagationInfluencer influencer);
}
