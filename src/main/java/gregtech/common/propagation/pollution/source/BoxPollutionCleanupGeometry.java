package gregtech.common.propagation.pollution.source;

import net.minecraft.util.Vec3;

public interface BoxPollutionCleanupGeometry {

    Vec3 getFieldCenter();

    double getHalfX();

    double getHalfY();

    double getHalfZ();
}
