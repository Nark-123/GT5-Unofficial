package gregtech.common.propagation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.MathHelper;

import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.GTMod;

public class PollutionDebugRenderer {

    private final Minecraft mc = Minecraft.getMinecraft();

    private int updateTimer;
    private double cachedPollution;
    private double cachedReference;
    private double cachedDifference;
    private boolean sampleMismatch;

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

            PollutionManager manager = GTMod.clientProxy()
                .getClientPollutionManager(dimension);

            cachedPollution = manager.sample(pos);
            cachedReference = manager.sampleReference(pos);
            cachedDifference = Math.abs(cachedPollution - cachedReference);

            double tolerance = Math.max(1.0E-6D, Math.abs(cachedReference) * 1.0E-6D);
            sampleMismatch = cachedDifference > tolerance;
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
