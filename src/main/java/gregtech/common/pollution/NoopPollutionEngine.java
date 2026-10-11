package gregtech.common.pollution;

import gregtech.api.pollution.IPollutionEngine;
import net.minecraft.util.AxisAlignedBB;

public final class NoopPollutionEngine implements IPollutionEngine {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public void change(int x, int y, int z, int amount) {}

    @Override
    public int get(int x, int y, int z) {
        return 0;
    }
}
