package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public interface EmitterGrouping<K> {

    K getGroupKey(PropagationSource source, Vec3 position);
}
