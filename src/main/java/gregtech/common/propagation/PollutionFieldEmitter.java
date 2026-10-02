package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public interface PollutionFieldEmitter extends PropagationEmitter {

    default double getInfluence(
        Vec3 position,
        PollutionQueryProfiler profiler) {

        return getInfluence(position);
    }
}
