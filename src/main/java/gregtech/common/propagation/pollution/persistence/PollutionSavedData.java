package gregtech.common.propagation.pollution.persistence;

import gregtech.common.propagation.pollution.PollutionManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

public class PollutionSavedData extends WorldSavedData {

    private static final String DATA_NAME = "GT_POLLUTION_PROPAGATION";
    private NBTTagCompound storedData = new NBTTagCompound();
    private PollutionManager manager;
    private boolean legacyStorageMigrated;

    public PollutionSavedData(String name) {
        super(name);
    }

    public static PollutionSavedData get(World world) {
        MapStorage storage = world.perWorldStorage;

        PollutionSavedData data = (PollutionSavedData) storage.loadData(PollutionSavedData.class, DATA_NAME);

        if (data == null) {
            data = new PollutionSavedData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        storedData = nbt.getCompoundTag("Pollution");
        legacyStorageMigrated = nbt.getBoolean("LegacyStorageMigrated");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        if (manager != null) {
            NBTTagCompound pollution = new NBTTagCompound();
            manager.writeToNBT(pollution);
            nbt.setTag("Pollution", pollution);
        } else {
            nbt.setTag("Pollution", storedData);
        }
        nbt.setBoolean("LegacyStorageMigrated", legacyStorageMigrated);
    }

    public boolean isLegacyStorageMigrated() {
        return legacyStorageMigrated;
    }

    public void setLegacyStorageMigrated() {
        if (legacyStorageMigrated) return;

        legacyStorageMigrated = true;
        markDirty();
    }

    public void loadInto(PollutionManager manager) {
        this.manager = manager;
        manager.readFromNBT(storedData);
    }
}
