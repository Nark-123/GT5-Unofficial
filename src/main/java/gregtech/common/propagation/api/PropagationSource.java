package gregtech.common.propagation.api;

import net.minecraft.util.Vec3;

public interface PropagationSource {

    Vec3 getPosition();

    int getDimension();

    boolean isValid();

    double consumeEmission();

    String getEmitterDefinitionId();
}
