package gregtech.api.net;

import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.google.common.io.ByteArrayDataInput;

import gregtech.common.propagation.PollutionQueryProfileClientState;
import gregtech.common.propagation.PollutionQueryProfiler;
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

    public GTPacketPollutionQueryProfile() {}

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
    }

    @Override
    public byte getPacketID() {
        return GTPacketTypes.POLLUTION_QUERY_PROFILE.id;
    }
}
