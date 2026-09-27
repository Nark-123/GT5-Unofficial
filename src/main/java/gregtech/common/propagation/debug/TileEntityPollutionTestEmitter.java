package gregtech.common.propagation.debug;

import gregtech.common.pollution.Pollution;
import gregtech.common.propagation.PropagationSource;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;

public class TileEntityPollutionTestEmitter extends TileEntity {

    private static final int MIN_EMISSION = 100;
    private static final int MAX_EMISSION = 100_000;

    private final Source source = new Source();

    private boolean registered;
    private int emissionPerSecond;
    private double lastSample;

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;

        if (emissionPerSecond == 0) {
            emissionPerSecond = MIN_EMISSION + worldObj.rand.nextInt(MAX_EMISSION - MIN_EMISSION + 1);
            markDirty();
        }

        if (!registered) {
            Pollution.getPropagationManager(worldObj).registerSource(source);
            registered = true;
        }

        source.addEmission(emissionPerSecond / 20.0D);

        lastSample = Pollution.getPollution(
            worldObj,
            xCoord,
            yCoord,
            zCoord
        );
    }

    @Override
    public void invalidate() {
        unregisterSource();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        unregisterSource();
        super.onChunkUnload();
    }

    private void unregisterSource() {
        if (!registered || worldObj == null || worldObj.isRemote) return;

        Pollution.getPropagationManager(worldObj).unregisterSource(source);
        registered = false;
    }

    public int getEmissionPerSecond() {
        return emissionPerSecond;
    }

    public double getLastSample() {
        return lastSample;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        emissionPerSecond = nbt.getInteger("EmissionPerSecond");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        nbt.setInteger("EmissionPerSecond", emissionPerSecond);
    }

    private final class Source implements PropagationSource {

        private double emission;

        private void addEmission(double amount) {
            emission += amount;
        }

        @Override
        public Vec3 getPosition() {
            return Vec3.createVectorHelper(
                xCoord + 0.5D,
                yCoord + 0.5D,
                zCoord + 0.5D
            );
        }

        @Override
        public int getDimension() {
            return worldObj.provider.dimensionId;
        }

        @Override
        public boolean isValid() {
            return !TileEntityPollutionTestEmitter.this.isInvalid()
                && worldObj != null
                && worldObj.getTileEntity(xCoord, yCoord, zCoord) == TileEntityPollutionTestEmitter.this;
        }

        @Override
        public double consumeEmission() {
            double result = emission;
            emission = 0.0D;
            return result;
        }
    }
}
