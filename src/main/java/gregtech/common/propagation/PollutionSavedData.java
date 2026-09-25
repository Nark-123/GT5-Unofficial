package gregtech.common.propagation;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

public class PollutionSavedData extends WorldSavedData {

    private static final String DATA_NAME = "GT_POLLUTION_PROPAGATION";
    private NBTTagCompound storedData = new NBTTagCompound();
    private PollutionManager manager;

    public PollutionSavedData(String name) {
        super(name);
    }

    public static PollutionSavedData get(World world) {
        MapStorage storage = world.perWorldStorage;

        PollutionSavedData data = (PollutionSavedData) storage.loadData(
            PollutionSavedData.class,
            DATA_NAME
        );

        if (data == null) {
            data = new PollutionSavedData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        storedData = nbt.getCompoundTag("Pollution");
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
    }

    public void loadInto(PollutionManager manager) {
        this.manager = manager;
        manager.readFromNBT(storedData);
    }
}
