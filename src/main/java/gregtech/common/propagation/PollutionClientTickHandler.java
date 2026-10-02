package gregtech.common.propagation;

import gregtech.common.pollution.Pollution;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class PollutionClientTickHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;

        if (world == null || mc.isGamePaused()) return;

        Pollution.getPropagationManager(world).tick();
    }
}
