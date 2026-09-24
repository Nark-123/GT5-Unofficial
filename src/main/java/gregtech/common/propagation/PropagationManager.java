package gregtech.common.propagation;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

public interface PropagationManager {

    void registerSource(PropagationSource source);

    void unregisterSource(PropagationSource source);

    void registerInfluencer(PropagationInfluencer influencer);

    void unregisterInfluencer(PropagationInfluencer influencer);

    float sample(BlockPos pos);

    void tick(int tick);
}
