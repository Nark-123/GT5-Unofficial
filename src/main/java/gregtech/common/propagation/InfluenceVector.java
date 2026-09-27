package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public class InfluenceVector {

    public final PropagationInfluencer source;

    public final Vec3 emitter;
    public final Vec3 influencer;

    public final Vec3 normalizedVec;
    public final double distance;

    public InfluenceVector(Vec3 emitter, PropagationInfluencer source) {
        this.source = source;
        this.emitter = emitter;
        this.influencer = source.getPosition();

        Vec3 diff = influencer.subtract(emitter);
        distance = diff.lengthVector();
        normalizedVec = diff.normalize();
    }
}
