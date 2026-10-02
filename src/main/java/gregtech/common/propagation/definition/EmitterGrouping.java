package gregtech.common.propagation.definition;

import gregtech.common.propagation.api.PropagationSource;
import net.minecraft.util.Vec3;

public interface EmitterGrouping<K> {

    K getGroupKey(PropagationSource source, Vec3 position);
}
