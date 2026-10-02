package gregtech.common.propagation.pollution.emitter;

import gregtech.common.propagation.debug.PollutionQueryProfiler;
import gregtech.common.propagation.api.PropagationEmitter;
import net.minecraft.util.Vec3;

public interface PollutionFieldEmitter extends PropagationEmitter {

    default double getInfluence(
        Vec3 position,
        PollutionQueryProfiler profiler) {

        return getInfluence(position);
    }
}
