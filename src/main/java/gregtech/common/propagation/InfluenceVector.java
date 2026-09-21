package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public class InfluenceVector {

    public final Vec3 emitter;
    public final Vec3 influencer;

    public final Vec3 normalizedVec;
    public final double distance;


    public InfluenceVector(Vec3 e, Vec3 i) {
        emitter = e;
        influencer = i;

        Vec3 diff = i.subtract(e);
        distance = diff.lengthVector();
        normalizedVec = diff.normalize();
    }
}
