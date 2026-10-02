package gregtech.common.propagation.network;

import gregtech.common.propagation.definition.InfluencerStateSnapshot;
import io.netty.buffer.ByteBuf;

public interface InfluencerStateCodec<
    S extends InfluencerStateSnapshot> {

    Class<S> getStateType();

    void encode(
        S state,
        ByteBuf out);

    S decode(
        ByteBuf in);
}
