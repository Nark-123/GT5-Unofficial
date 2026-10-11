package gregtech.common.pollution.pvnrt;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import gregtech.api.pollution.PollutionEngineRegistry;

@Mod(
    modid = "gt5upvnrt",
    name = "GT5U pVnRT Integration",
    version = "1.0",
    dependencies = "required-after:gregtech;required-after:pvnrt"
)
public class PvnrtIntegration {

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        PollutionEngineRegistry.register("pvnrt", PvnrtPollutionEngine::new);
    }
}
