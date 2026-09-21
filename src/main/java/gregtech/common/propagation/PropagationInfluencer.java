package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public interface PropagationInfluencer {

    double influence(Vec3 pos, InfluenceVector influenceVector);

    Vec3 getPosition();

    double getRange();
}
