package gregtech.common.propagation.network;

import java.util.HashMap;
import java.util.Map;

import com.google.common.io.ByteArrayDataInput;

import gregtech.common.propagation.definition.EmitterStateSnapshot;
import io.netty.buffer.ByteBuf;

public final class EmitterStateCodecRegistry {

    private final Map<String, EmitterStateCodec<?>>
        codecs =
        new HashMap<>();

    public <S extends EmitterStateSnapshot>
    void register(
        String definitionId,
        EmitterStateCodec<S> codec) {

        if (definitionId == null
            || definitionId.isEmpty()) {

            throw new IllegalArgumentException(
                "Invalid definition id");
        }

        if (codec == null) {
            throw new IllegalArgumentException(
                "Emitter state codec is null");
        }

        if (codecs.containsKey(definitionId)) {
            throw new IllegalStateException(
                "Emitter state codec already registered: "
                    + definitionId);
        }

        codecs.put(
            definitionId,
            codec);
    }

    public boolean contains(
        String definitionId) {

        return codecs.containsKey(
            definitionId);
    }

    public void encode(
        String definitionId,
        EmitterStateSnapshot state,
        ByteBuf out) {

        if (state == null) {
            throw new IllegalArgumentException(
                "Emitter state is null");
        }

        if (out == null) {
            throw new IllegalArgumentException(
                "Output buffer is null");
        }

        EmitterStateCodec<?> codec =
            requireCodec(definitionId);

        if (!codec.getStateType()
            .isInstance(state)) {

            throw new IllegalArgumentException(
                "Emitter state type mismatch for "
                    + definitionId
                    + ": "
                    + state.getClass().getName());
        }

        encodeUnchecked(
            codec,
            state,
            out);
    }

    public EmitterStateSnapshot decode(
        String definitionId,
        ByteBuf in) {

        if (in == null) {
            throw new IllegalArgumentException(
                "Input buffer is null");
        }

        EmitterStateCodec<?> codec =
            requireCodec(definitionId);

        EmitterStateSnapshot state =
            codec.decode(in);

        if (state == null) {
            throw new IllegalStateException(
                "Emitter codec returned null: "
                    + definitionId);
        }

        if (!codec.getStateType()
            .isInstance(state)) {

            throw new IllegalStateException(
                "Emitter codec returned wrong state type: "
                    + definitionId);
        }

        return state;
    }

    private EmitterStateCodec<?> requireCodec(
        String definitionId) {

        EmitterStateCodec<?> codec =
            codecs.get(definitionId);

        if (codec == null) {
            throw new IllegalArgumentException(
                "Unknown emitter state codec: "
                    + definitionId);
        }

        return codec;
    }

    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    private static void encodeUnchecked(
        EmitterStateCodec codec,
        EmitterStateSnapshot state,
        ByteBuf out) {

        codec.encode(
            state,
            out);
    }
}
