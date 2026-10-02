package gregtech.common.propagation.network;

import gregtech.common.propagation.definition.EmitterStateSnapshot;
import io.netty.buffer.ByteBuf;

public interface EmitterStateCodec<
    S extends EmitterStateSnapshot> {

    Class<S> getStateType();

    void encode(
        S state,
        ByteBuf out);

    S decode(
        ByteBuf in);
}
