package gregtech.common.propagation.definition;

import gregtech.common.propagation.api.PropagationEmitter;
import gregtech.common.propagation.api.PropagationSource;
import net.minecraft.util.Vec3;

public interface EmitterFactory<K, E extends PropagationEmitter> {

    E create(int dimension, K groupKey, PropagationSource source, Vec3 position);
}
