package gregtech.common.propagation;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.common.pollution.Pollution;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

public class PollutionDebugRenderer {

    private final Minecraft mc = Minecraft.getMinecraft();

    private int updateTimer = 0;
    private double cachedPollution = 0.0D;


    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Text event) {

        EntityClientPlayerMP player = mc.thePlayer;

        if (player == null) {
            return;
        }

        if (++updateTimer >= 20) {

            updateTimer = 0;

            cachedPollution = Pollution.getPollution(
                player.worldObj,
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posY),
                MathHelper.floor_double(player.posZ)
            );
        }


        event.left.add(
            "Pollution: " + String.format("%.2f", cachedPollution)
        );


        event.left.add(
            String.format(
                "Pos: %.1f %.1f %.1f",
                player.posX,
                player.posY,
                player.posZ
            )
        );
    }
}
