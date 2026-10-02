package gregtech.common.propagation.pollution.debug;

import gregtech.common.pollution.Pollution;
import gregtech.common.propagation.debug.PollutionQueryProfileClientState;
import gregtech.common.propagation.pollution.PollutionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.MathHelper;

import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PollutionDebugRenderer {

    private final Minecraft mc = Minecraft.getMinecraft();

    private int updateTimer;
    private double cachedPollution;
    private double cachedReference;
    private double cachedDifference;
    private boolean sampleMismatch;

    private double cachedServerClientDifference;
    private double cachedServerClientReferenceDifference;

    private double cachedClientParitySample;
    private double cachedClientParityReference;

    private boolean parityMismatch;
    private boolean parityReferenceMismatch;

    private boolean emitterCountMismatch;
    private boolean influencerCountMismatch;

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Text event) {
        EntityClientPlayerMP player = mc.thePlayer;

        if (player == null || player.worldObj == null) {
            return;
        }

        int dimension = player.worldObj.provider.dimensionId;

        if (++updateTimer >= 20) {
            updateTimer = 0;

            BlockPos pos = new BlockPos(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posY),
                MathHelper.floor_double(player.posZ));

            PollutionManager manager = Pollution.getPropagationManager(player.worldObj);

            cachedPollution = manager.sample(pos);
            cachedReference = manager.sampleReference(pos);
            cachedDifference = Math.abs(cachedPollution - cachedReference);

            double tolerance = Math.max(1.0E-6D, Math.abs(cachedReference) * 1.0E-6D);
            sampleMismatch = cachedDifference > tolerance;

            PollutionQueryProfileClientState.ParityData
                parity =
                PollutionQueryProfileClientState
                    .getParity(dimension);

            if (parity != null) {
                BlockPos parityPos = new BlockPos(parity.x, parity.y, parity.z);

                cachedClientParitySample = manager.sampleUnprofiledForDebug(parityPos);
                cachedClientParityReference = manager.sampleReference(parityPos);
                cachedServerClientDifference = Math.abs(parity.serverSample - cachedClientParitySample);
                cachedServerClientReferenceDifference = Math.abs(parity.serverReference - cachedClientParityReference);

                double sampleScale = Math.max(Math.abs(parity.serverSample), Math.abs(cachedClientParitySample));

                double sampleTolerance = Math.max(1.0E-6D, sampleScale * 1.0E-6D);

                double referenceScale = Math.max(Math.abs(parity.serverReference), Math.abs(cachedClientParityReference));

                double referenceTolerance = Math.max(1.0E-6D, referenceScale * 1.0E-6D);

                parityMismatch = cachedServerClientDifference > sampleTolerance;

                parityReferenceMismatch = cachedServerClientReferenceDifference > referenceTolerance;

                emitterCountMismatch = manager.getEmitterCount() != parity.emitterCount;

                influencerCountMismatch = manager.getInfluencerCount() != parity.influencerCount;
            }
        }

        PollutionQueryProfileClientState.ParityData parity = PollutionQueryProfileClientState.getParity(dimension);

        PollutionManager manager = Pollution.getPropagationManager(player.worldObj);

        if (parity != null) {
            event.left.add("");
            event.left.add("Pollution Server/Client Parity");

            event.left.add(String.format("Sample pos: %d %d %d", parity.x, parity.y, parity.z));

            event.left.add(String.format("Server sample: %.6f", parity.serverSample));

            event.left.add(String.format("Client sample: %.6f", cachedClientParitySample));

            event.left.add(String.format("S/C difference: %.9f", cachedServerClientDifference));

            event.left.add("Sample parity: " + (parityMismatch ? "MISMATCH" : "OK"));

            event.left.add(String.format("Server reference: %.6f", parity.serverReference));

            event.left.add(String.format("Client reference: %.6f", cachedClientParityReference));

            event.left.add(String.format("Reference S/C diff: %.9f", cachedServerClientReferenceDifference));

            event.left.add("Reference parity: " + (parityReferenceMismatch ? "MISMATCH" : "OK"));

            event.left.add(String.format("Emitters S/C: %d / %d%s",
                parity.emitterCount, manager.getEmitterCount(), emitterCountMismatch ? " MISMATCH" : ""));

            event.left.add(String.format("Influencers S/C: %d / %d%s",
                parity.influencerCount, manager.getInfluencerCount(), influencerCountMismatch? " MISMATCH" : ""));
        }

        event.left.add(String.format("Pollution: %.2f", cachedPollution));
        event.left.add(String.format("Reference: %.2f", cachedReference));
        event.left.add(String.format("Difference: %.6f", cachedDifference));
        event.left.add("Reference check: " + (sampleMismatch ? "MISMATCH" : "OK"));
        event.left.add(String.format("Pos: %.1f %.1f %.1f", player.posX, player.posY, player.posZ));

        PollutionQueryProfileClientState.Data profile = PollutionQueryProfileClientState.get(dimension);

        if (profile == null) {
            event.left.add("Pollution Performance: waiting for server...");
            return;
        }

        event.left.add("");
        event.left.add("Pollution Performance");
        event.left.add(String.format("Emitters: %d  Influencers: %d", profile.emitterCount, profile.influencerCount));
        event.left.add(String.format("Queries/tick: %.2f", profile.getQueriesPerTick()));
        event.left.add(String.format("Query time/tick: %.3f ms", profile.getQueryMillisPerTick()));
        event.left.add(String.format("Average query: %.3f us", profile.getAverageQueryMicros()));
        event.left.add(String.format("Emitter candidates/query: %.2f", profile.getAverageEmitterCandidates()));
        event.left.add(String.format("Inside range/query: %.2f", profile.getAverageEmittersInsideRange()));
        event.left.add(String.format("Influencer calls/query: %.2f", profile.getAverageInfluencerCalls()));
        event.left.add(String.format("Influencer no-op: %.1f%%", profile.getInfluencerNoopPercent()));
        event.left.add(String.format("Max emitter candidates: %d", profile.maxEmitterCandidates));
        event.left.add(String.format("Max influencers/emitter: %d", profile.maxInfluencersPerEmitter));
    }
}
