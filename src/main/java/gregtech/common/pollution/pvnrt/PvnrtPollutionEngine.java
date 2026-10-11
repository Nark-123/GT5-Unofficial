package gregtech.common.pollution.pvnrt;

import net.minecraft.world.World;

import gregtech.api.pollution.IPollutionEngine;
import pvnrt.PVNRT;
import pvnrt.api.PollutionApi;
import pvnrt.core.world.GasManager;
import pvnrt.gas.GasList;
import pvnrt.rust.Voxel;

public final class PvnrtPollutionEngine implements IPollutionEngine {

    private static final float MOLES_PER_POLLUTION = 0.1f;

    private final World world;

    public PvnrtPollutionEngine(World world) {
        this.world = world;
    }

    @Override
    public String id() {
        return "pvnrt";
    }

    @Override
    public void change(int x, int y, int z, int amount) {
        if (amount > 0) {
            if (!PollutionApi.emit(world, x, y, z, amount) && world.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
                PollutionApi.emit(world, x, Math.min(255, world.getHeightValue(x, z)), z, amount);
            }
        } else if (amount < 0) {
            remove(x, y, z, -(long) amount);
        }
    }

    @Override
    public int get(int x, int y, int z) {
        GasManager manager = getManager(x, y, z);
        if (manager == null) return 0;

        GasList gas = manager.getSimulator().getVoxelContents(x, y, z);
        if (gas == null) return 0;

        return Math.max(0, Math.round(gas.get(PVNRT.SMOG) / MOLES_PER_POLLUTION));
    }

    private void remove(int x, int y, int z, long amount) {
        GasManager manager = getManager(x, y, z);
        if (manager == null) return;

        Voxel voxel = manager.getSimulator().getVoxelInfo(x, y, z);
        if (voxel.isAtmosphere() || !voxel.hasGasPointer()) return;

        GasList gas = manager.getSimulator().getVoxelContents(x, y, z);
        if (gas == null) return;

        float available = gas.get(PVNRT.SMOG);
        if (!(available > 0)) return;

        float removed = (float) Math.min(available, amount * (double) MOLES_PER_POLLUTION);

        GasList delta = new GasList();
        delta.addAt(PVNRT.SMOG, -removed, gas.getTemperature());

        manager.addGas(x, y, z, delta);
    }

    private GasManager getManager(int x, int y, int z) {
        if (world.isRemote || y < 0 || y >= 256) return null;

        GasManager manager = GasManager.getGasManager(world);
        if (manager == null || manager.getTracker(x >> 4, y >> 4, z >> 4) == null) return null;

        return manager;
    }
}
