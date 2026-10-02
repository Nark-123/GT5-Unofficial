package gregtech.common.propagation.network;

import java.util.HashMap;
import java.util.Map;

import gregtech.common.propagation.definition.InfluencerStateSnapshot;
import io.netty.buffer.ByteBuf;

public final class InfluencerStateCodecRegistry {

    private final Map<String, InfluencerStateCodec<?>>
        codecs = new HashMap<>();

    public void register(
        String definitionId,
        InfluencerStateCodec<?> codec) {

        if (definitionId == null
            || definitionId.isEmpty()) {

            throw new IllegalArgumentException(
                "Invalid influencer definition id");
        }

        if (codec == null) {
            throw new IllegalArgumentException(
                "Influencer codec is null");
        }

        if (codecs.put(
            definitionId,
            codec) != null) {

            throw new IllegalStateException(
                "Duplicate influencer codec: "
                    + definitionId);
        }
    }

    public InfluencerStateSnapshot decode(
        String definitionId,
        ByteBuf in) {

        InfluencerStateCodec<?> codec =
            requireCodec(definitionId);

        InfluencerStateSnapshot result =
            codec.decode(in);

        if (result == null
            || !codec.getStateType()
            .isInstance(result)) {

            throw new IllegalStateException(
                "Invalid influencer codec result: "
                    + definitionId);
        }

        return result;
    }

    public void encode(
        String definitionId,
        InfluencerStateSnapshot state,
        ByteBuf out) {

        InfluencerStateCodec<?> codec =
            requireCodec(definitionId);

        if (!codec.getStateType()
            .isInstance(state)) {

            throw new IllegalArgumentException(
                "Influencer state type mismatch: "
                    + definitionId);
        }

        encodeUnchecked(
            codec,
            state,
            out);
    }

    private InfluencerStateCodec<?> requireCodec(
        String definitionId) {

        InfluencerStateCodec<?> codec =
            codecs.get(definitionId);

        if (codec == null) {
            throw new IllegalArgumentException(
                "Unknown influencer codec: "
                    + definitionId);
        }

        return codec;
    }

    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    private static void encodeUnchecked(
        InfluencerStateCodec codec,
        InfluencerStateSnapshot state,
        ByteBuf out) {

        codec.encode(
            state,
            out);
    }
}
