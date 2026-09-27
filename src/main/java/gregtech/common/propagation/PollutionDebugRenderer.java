package gregtech.common.propagation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.common.pollution.Pollution;

public class PollutionDebugRenderer {

    private final Minecraft mc = Minecraft.getMinecraft();

    private int updateTimer;
    private double cachedPollution;

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Text event) {
        EntityClientPlayerMP player = mc.thePlayer;

        if (player == null || player.worldObj == null) {
            return;
        }

        if (++updateTimer >= 20) {
            updateTimer = 0;

            cachedPollution = Pollution.getPollution(
                player.worldObj,
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posY),
                MathHelper.floor_double(player.posZ));
        }

        int dimension = player.worldObj.provider.dimensionId;

        event.left.add(String.format("Pollution: %.2f", cachedPollution));
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
