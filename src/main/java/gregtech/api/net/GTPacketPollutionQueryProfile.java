package gregtech.api.net;

import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.google.common.io.ByteArrayDataInput;

import gregtech.common.propagation.debug.PollutionQueryProfileClientState;
import gregtech.common.propagation.debug.PollutionQueryProfiler;
import io.netty.buffer.ByteBuf;

public class GTPacketPollutionQueryProfile extends GTPacket {

    private int ticks;
    private long queries;
    private long queryNanos;
    private long emitterCandidates;
    private long emittersInsideRange;
    private long influencerCalls;
    private long influencerNoops;
    private int maxEmitterCandidates;
    private int maxInfluencersPerEmitter;
    private int emitterCount;
    private int influencerCount;

    private boolean hasParity;
    private int parityX;
    private int parityY;
    private int parityZ;
    private double serverSample;
    private double serverReference;

    public GTPacketPollutionQueryProfile() {}

    public GTPacketPollutionQueryProfile(
        int ticks,
        PollutionQueryProfiler.Snapshot snapshot,
        int emitterCount,
        int influencerCount,
        int parityX,
        int parityY,
        int parityZ,
        double serverSample,
        double serverReference) {

        this(
            ticks,
            snapshot,
            emitterCount,
            influencerCount);

        if (!Double.isFinite(serverSample)
            || !Double.isFinite(serverReference)) {

            throw new IllegalArgumentException(
                "Invalid parity sample");
        }

        this.hasParity = true;

        this.parityX = parityX;
        this.parityY = parityY;
        this.parityZ = parityZ;

        this.serverSample =
            serverSample;

        this.serverReference =
            serverReference;
    }

    public GTPacketPollutionQueryProfile(int ticks, PollutionQueryProfiler.Snapshot snapshot, int emitterCount,
        int influencerCount) {
        this.ticks = ticks;
        this.queries = snapshot.queries;
        this.queryNanos = snapshot.queryNanos;
        this.emitterCandidates = snapshot.emitterCandidates;
        this.emittersInsideRange = snapshot.emittersInsideRange;
        this.influencerCalls = snapshot.influencerCalls;
        this.influencerNoops = snapshot.influencerNoops;
        this.maxEmitterCandidates = snapshot.maxEmitterCandidates;
        this.maxInfluencersPerEmitter = snapshot.maxInfluencersPerEmitter;
        this.emitterCount = emitterCount;
        this.influencerCount = influencerCount;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeInt(ticks);
        out.writeLong(queries);
        out.writeLong(queryNanos);
        out.writeLong(emitterCandidates);
        out.writeLong(emittersInsideRange);
        out.writeLong(influencerCalls);
        out.writeLong(influencerNoops);
        out.writeInt(maxEmitterCandidates);
        out.writeInt(maxInfluencersPerEmitter);
        out.writeInt(emitterCount);
        out.writeInt(influencerCount);
        out.writeBoolean(hasParity);

        if (hasParity) {
            out.writeInt(
                parityX);

            out.writeInt(
                parityY);

            out.writeInt(
                parityZ);

            out.writeDouble(
                serverSample);

            out.writeDouble(
                serverReference);
        }
    }

    @Override
    public GTPacket decode(ByteArrayDataInput data) {
        GTPacketPollutionQueryProfile packet = new GTPacketPollutionQueryProfile();

        packet.ticks = data.readInt();
        packet.queries = data.readLong();
        packet.queryNanos = data.readLong();
        packet.emitterCandidates = data.readLong();
        packet.emittersInsideRange = data.readLong();
        packet.influencerCalls = data.readLong();
        packet.influencerNoops = data.readLong();
        packet.maxEmitterCandidates = data.readInt();
        packet.maxInfluencersPerEmitter = data.readInt();
        packet.emitterCount = data.readInt();
        packet.influencerCount = data.readInt();
        packet.hasParity = data.readBoolean();

        if (packet.hasParity) {
            packet.parityX =
                data.readInt();

            packet.parityY =
                data.readInt();

            packet.parityZ =
                data.readInt();

            packet.serverSample =
                data.readDouble();

            packet.serverReference =
                data.readDouble();

            if (!Double.isFinite(
                packet.serverSample)
                || !Double.isFinite(
                packet.serverReference)) {

                throw new IllegalArgumentException(
                    "Invalid parity sample");
            }
        }

        return packet;
    }

    @Override
    public void process(IBlockAccess world) {
        if (!(world instanceof World)) return;

        int dimension = ((World) world).provider.dimensionId;

        PollutionQueryProfileClientState.update(
            dimension,
            ticks,
            queries,
            queryNanos,
            emitterCandidates,
            emittersInsideRange,
            influencerCalls,
            influencerNoops,
            maxEmitterCandidates,
            maxInfluencersPerEmitter,
            emitterCount,
            influencerCount);

        if (hasParity) {
            PollutionQueryProfileClientState
                .updateParity(
                    dimension,
                    parityX,
                    parityY,
                    parityZ,
                    serverSample,
                    serverReference,
                    emitterCount,
                    influencerCount);
        }
    }

    @Override
    public byte getPacketID() {
        return GTPacketTypes.POLLUTION_QUERY_PROFILE.id;
    }
}
