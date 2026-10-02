package gregtech.api.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gregtech.common.propagation.definition.InfluencerStateSnapshot;
import gregtech.common.propagation.network.InfluencerStateCodecRegistry;
import gregtech.common.propagation.pollution.network.PollutionInfluencerStateCodecs;
import gregtech.common.propagation.runtime.InfluencerRuntimeChange;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.google.common.io.ByteArrayDataInput;

import gregtech.common.pollution.Pollution;
import gregtech.common.propagation.definition.EmitterStateSnapshot;
import gregtech.common.propagation.network.EmitterStateCodecRegistry;
import gregtech.common.propagation.pollution.PollutionManager;
import gregtech.common.propagation.pollution.network.PollutionStateCodecs;
import gregtech.common.propagation.runtime.EmitterRuntimeChange;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class GTPacketPollutionState
    extends GTPacket {

    private static final int PROTOCOL_VERSION = 2;

    private static final int OP_UPSERT = 0;
    private static final int OP_REMOVE = 1;

    private static final int MAX_RECORDS = 65_535;

    private static final int MAX_DEFINITION_ID_BYTES = 64;

    private static final int MAX_STATE_PAYLOAD_BYTES = 512;

    private static final int MAX_TOTAL_STATE_PAYLOAD_BYTES =
        8 * 1024 * 1024;

    private boolean fullSnapshot;

    private List<EmitterRuntimeChange> records =
        Collections.emptyList();

    private List<EmitterRuntimeChange>
        emitterRecords =
        Collections.emptyList();

    private List<InfluencerRuntimeChange>
        influencerRecords =
        Collections.emptyList();

    public GTPacketPollutionState() {}

    public GTPacketPollutionState(
        boolean fullSnapshot,
        List<EmitterRuntimeChange> emitterRecords,
        List<InfluencerRuntimeChange> influencerRecords) {

        if (emitterRecords == null
            || influencerRecords == null) {

            throw new IllegalArgumentException(
                "Propagation records are null");
        }

        this.fullSnapshot =
            fullSnapshot;

        this.emitterRecords =
            Collections.unmodifiableList(
                new ArrayList<>(
                    emitterRecords));

        this.influencerRecords =
            Collections.unmodifiableList(
                new ArrayList<>(
                    influencerRecords));
    }

    public static GTPacketPollutionState full(
        List<EmitterRuntimeChange> emitters,
        List<InfluencerRuntimeChange> influencers) {

        return new GTPacketPollutionState(
            true,
            emitters,
            influencers);
    }

    public static GTPacketPollutionState delta(
        List<EmitterRuntimeChange> emitters,
        List<InfluencerRuntimeChange> influencers) {

        return new GTPacketPollutionState(
            false,
            emitters,
            influencers);
    }

    @Override
    public void encode(ByteBuf out) {
        if (out == null) {
            throw new IllegalArgumentException(
                "Output buffer is null");
        }

        if (emitterRecords.size() > MAX_RECORDS) {
            throw new IllegalStateException(
                "Too many emitter records: "
                    + emitterRecords.size());
        }

        if (influencerRecords.size() > MAX_RECORDS) {
            throw new IllegalStateException(
                "Too many influencer records: "
                    + influencerRecords.size());
        }

        out.writeByte(PROTOCOL_VERSION);
        out.writeBoolean(fullSnapshot);

        int totalPayloadBytes = 0;

        /*
         * =========================================================
         * EMITTER SECTION
         * =========================================================
         */

        out.writeInt(emitterRecords.size());

        EmitterStateCodecRegistry emitterCodecs =
            PollutionStateCodecs.registry();

        for (EmitterRuntimeChange record : emitterRecords) {
            if (record == null) {
                throw new IllegalStateException(
                    "Null emitter record");
            }

            switch (record.getOperation()) {
                case UPSERT: {
                    out.writeByte(OP_UPSERT);

                    out.writeLong(
                        record.getEmitterId());

                    writeDefinitionId(
                        out,
                        record.getDefinitionId());

                    EmitterStateSnapshot state =
                        record.getState();

                    if (state == null) {
                        throw new IllegalStateException(
                            "Emitter UPSERT state is null");
                    }

                    ByteBuf payload =
                        Unpooled.buffer();

                    try {
                        emitterCodecs.encode(
                            record.getDefinitionId(),
                            state,
                            payload);

                        int payloadLength =
                            payload.readableBytes();

                        if (payloadLength <= 0
                            || payloadLength
                            > MAX_STATE_PAYLOAD_BYTES) {

                            throw new IllegalStateException(
                                "Invalid emitter payload length: "
                                    + payloadLength);
                        }

                        totalPayloadBytes +=
                            payloadLength;

                        if (totalPayloadBytes
                            > MAX_TOTAL_STATE_PAYLOAD_BYTES) {

                            throw new IllegalStateException(
                                "Propagation payload limit exceeded");
                        }

                        out.writeInt(
                            payloadLength);

                        out.writeBytes(
                            payload,
                            payload.readerIndex(),
                            payloadLength);

                    } finally {
                        payload.release();
                    }

                    break;
                }

                case REMOVE:
                    if (fullSnapshot) {
                        throw new IllegalStateException(
                            "Full snapshot cannot contain emitter REMOVE");
                    }

                    out.writeByte(OP_REMOVE);

                    out.writeLong(
                        record.getEmitterId());

                    break;

                default:
                    throw new IllegalStateException(
                        "Unknown emitter operation: "
                            + record.getOperation());
            }
        }

        /*
         * =========================================================
         * INFLUENCER SECTION
         * =========================================================
         */

        out.writeInt(
            influencerRecords.size());

        InfluencerStateCodecRegistry influencerCodecs =
            PollutionInfluencerStateCodecs.registry();

        for (InfluencerRuntimeChange record
            : influencerRecords) {

            if (record == null) {
                throw new IllegalStateException(
                    "Null influencer record");
            }

            switch (record.getOperation()) {
                case UPSERT: {
                    out.writeByte(OP_UPSERT);

                    out.writeLong(
                        record.getInfluencerId());

                    writeDefinitionId(
                        out,
                        record.getDefinitionId());

                    InfluencerStateSnapshot state =
                        record.getState();

                    if (state == null) {
                        throw new IllegalStateException(
                            "Influencer UPSERT state is null");
                    }

                    ByteBuf payload =
                        Unpooled.buffer();

                    try {
                        influencerCodecs.encode(
                            record.getDefinitionId(),
                            state,
                            payload);

                        int payloadLength =
                            payload.readableBytes();

                        if (payloadLength <= 0
                            || payloadLength
                            > MAX_STATE_PAYLOAD_BYTES) {

                            throw new IllegalStateException(
                                "Invalid influencer payload length: "
                                    + payloadLength);
                        }

                        totalPayloadBytes +=
                            payloadLength;

                        if (totalPayloadBytes
                            > MAX_TOTAL_STATE_PAYLOAD_BYTES) {

                            throw new IllegalStateException(
                                "Propagation payload limit exceeded");
                        }

                        out.writeInt(
                            payloadLength);

                        out.writeBytes(
                            payload,
                            payload.readerIndex(),
                            payloadLength);

                    } finally {
                        payload.release();
                    }

                    break;
                }

                case REMOVE:
                    if (fullSnapshot) {
                        throw new IllegalStateException(
                            "Full snapshot cannot contain influencer REMOVE");
                    }

                    out.writeByte(OP_REMOVE);

                    out.writeLong(
                        record.getInfluencerId());

                    break;

                default:
                    throw new IllegalStateException(
                        "Unknown influencer operation: "
                            + record.getOperation());
            }
        }
    }

    @Override
    public GTPacket decode(
        ByteArrayDataInput data) {

        int protocolVersion =
            data.readUnsignedByte();

        if (protocolVersion
            != PROTOCOL_VERSION) {

            throw new IllegalArgumentException(
                "Unsupported pollution state protocol: "
                    + protocolVersion);
        }

        boolean fullSnapshot =
            data.readBoolean();

        int totalPayloadBytes = 0;

        /*
         * =========================================================
         * EMITTER SECTION
         * =========================================================
         */

        int emitterCount =
            data.readInt();

        if (emitterCount < 0
            || emitterCount > MAX_RECORDS) {

            throw new IllegalArgumentException(
                "Invalid emitter record count: "
                    + emitterCount);
        }

        List<EmitterRuntimeChange> emitterRecords =
            new ArrayList<>(
                emitterCount);

        EmitterStateCodecRegistry emitterCodecs =
            PollutionStateCodecs.registry();

        for (int i = 0;
             i < emitterCount;
             i++) {

            int operation =
                data.readUnsignedByte();

            long emitterId =
                data.readLong();

            if (emitterId <= 0L) {
                throw new IllegalArgumentException(
                    "Invalid emitter id at record "
                        + i
                        + ": "
                        + emitterId);
            }

            switch (operation) {
                case OP_UPSERT: {
                    String definitionId =
                        readDefinitionId(
                            data);

                    int payloadLength =
                        data.readInt();

                    if (payloadLength <= 0
                        || payloadLength
                        > MAX_STATE_PAYLOAD_BYTES) {

                        throw new IllegalArgumentException(
                            "Invalid emitter payload length at record "
                                + i
                                + ": "
                                + payloadLength);
                    }

                    totalPayloadBytes +=
                        payloadLength;

                    if (totalPayloadBytes
                        > MAX_TOTAL_STATE_PAYLOAD_BYTES) {

                        throw new IllegalArgumentException(
                            "Propagation payload limit exceeded");
                    }

                    byte[] payloadBytes =
                        new byte[payloadLength];

                    data.readFully(
                        payloadBytes);

                    ByteBuf payload =
                        Unpooled.wrappedBuffer(
                            payloadBytes);

                    EmitterStateSnapshot state;

                    try {
                        state =
                            emitterCodecs.decode(
                                definitionId,
                                payload);

                        if (payload.isReadable()) {
                            throw new IllegalArgumentException(
                                "Emitter codec did not consume entire payload: "
                                    + definitionId
                                    + ", remaining="
                                    + payload.readableBytes());
                        }

                    } finally {
                        payload.release();
                    }

                    emitterRecords.add(
                        EmitterRuntimeChange.upsert(
                            emitterId,
                            definitionId,
                            state));

                    break;
                }

                case OP_REMOVE:
                    if (fullSnapshot) {
                        throw new IllegalArgumentException(
                            "Full snapshot contains emitter REMOVE at record "
                                + i);
                    }

                    emitterRecords.add(
                        EmitterRuntimeChange.remove(
                            emitterId));

                    break;

                default:
                    throw new IllegalArgumentException(
                        "Unknown emitter operation at record "
                            + i
                            + ": "
                            + operation);
            }
        }

        /*
         * =========================================================
         * INFLUENCER SECTION
         * =========================================================
         */

        int influencerCount =
            data.readInt();

        if (influencerCount < 0
            || influencerCount > MAX_RECORDS) {

            throw new IllegalArgumentException(
                "Invalid influencer record count: "
                    + influencerCount);
        }

        List<InfluencerRuntimeChange>
            influencerRecords =
            new ArrayList<>(
                influencerCount);

        InfluencerStateCodecRegistry influencerCodecs =
            PollutionInfluencerStateCodecs.registry();

        for (int i = 0;
             i < influencerCount;
             i++) {

            int operation =
                data.readUnsignedByte();

            long influencerId =
                data.readLong();

            if (influencerId <= 0L) {
                throw new IllegalArgumentException(
                    "Invalid influencer id at record "
                        + i
                        + ": "
                        + influencerId);
            }

            switch (operation) {
                case OP_UPSERT: {
                    String definitionId =
                        readDefinitionId(
                            data);

                    int payloadLength =
                        data.readInt();

                    if (payloadLength <= 0
                        || payloadLength
                        > MAX_STATE_PAYLOAD_BYTES) {

                        throw new IllegalArgumentException(
                            "Invalid influencer payload length at record "
                                + i
                                + ": "
                                + payloadLength);
                    }

                    totalPayloadBytes +=
                        payloadLength;

                    if (totalPayloadBytes
                        > MAX_TOTAL_STATE_PAYLOAD_BYTES) {

                        throw new IllegalArgumentException(
                            "Propagation payload limit exceeded");
                    }

                    byte[] payloadBytes =
                        new byte[payloadLength];

                    data.readFully(
                        payloadBytes);

                    ByteBuf payload =
                        Unpooled.wrappedBuffer(
                            payloadBytes);

                    InfluencerStateSnapshot state;

                    try {
                        state =
                            influencerCodecs.decode(
                                definitionId,
                                payload);

                        if (payload.isReadable()) {
                            throw new IllegalArgumentException(
                                "Influencer codec did not consume entire payload: "
                                    + definitionId
                                    + ", remaining="
                                    + payload.readableBytes());
                        }

                    } finally {
                        payload.release();
                    }

                    influencerRecords.add(
                        InfluencerRuntimeChange.upsert(
                            influencerId,
                            definitionId,
                            state));

                    break;
                }

                case OP_REMOVE:
                    if (fullSnapshot) {
                        throw new IllegalArgumentException(
                            "Full snapshot contains influencer REMOVE at record "
                                + i);
                    }

                    influencerRecords.add(
                        InfluencerRuntimeChange.remove(
                            influencerId));

                    break;

                default:
                    throw new IllegalArgumentException(
                        "Unknown influencer operation at record "
                            + i
                            + ": "
                            + operation);
            }
        }

        return new GTPacketPollutionState(
            fullSnapshot,
            emitterRecords,
            influencerRecords);
    }

    @Override
    public void process(
        IBlockAccess blockAccess) {

        if (!(blockAccess instanceof World)) {
            return;
        }

        World world =
            (World) blockAccess;

        if (!world.isRemote) {
            return;
        }

        PollutionManager manager =
            Pollution.getPropagationManager(
                world);

        if (fullSnapshot) {
            manager.enqueueReplicaFullSnapshot(
                emitterRecords,
                influencerRecords);
        } else {
            manager.enqueueReplicaDelta(
                emitterRecords,
                influencerRecords);
        }
    }

    @Override
    public byte getPacketID() {
        return GTPacketTypes.POLLUTION_STATE.id;
    }

    private static void writeDefinitionId(
        ByteBuf out,
        String definitionId) {

        if (definitionId == null
            || definitionId.isEmpty()) {

            throw new IllegalArgumentException(
                "Invalid emitter definition id");
        }

        byte[] bytes =
            definitionId.getBytes(
                StandardCharsets.UTF_8);

        if (bytes.length == 0
            || bytes.length
            > MAX_DEFINITION_ID_BYTES) {

            throw new IllegalArgumentException(
                "Emitter definition id is too long: "
                    + definitionId);
        }

        out.writeByte(bytes.length);
        out.writeBytes(bytes);
    }

    private static String readDefinitionId(
        ByteArrayDataInput data) {

        int length =
            data.readUnsignedByte();

        if (length <= 0
            || length
            > MAX_DEFINITION_ID_BYTES) {

            throw new IllegalArgumentException(
                "Invalid emitter definition id length: "
                    + length);
        }

        byte[] bytes =
            new byte[length];

        data.readFully(bytes);

        String definitionId =
            new String(
                bytes,
                StandardCharsets.UTF_8);

        if (definitionId.isEmpty()) {
            throw new IllegalArgumentException(
                "Emitter definition id is empty");
        }

        return definitionId;
    }
}
