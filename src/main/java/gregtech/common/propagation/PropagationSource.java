package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public interface PropagationSource {

    Vec3 getPosition();

    int getDimension();

    boolean isValid();

    double consumeEmission();
}

