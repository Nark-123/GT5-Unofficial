package gregtech.common.propagation.pollution.debug;

import gregtech.common.propagation.pollution.PollutionEmitterDefinitions;
import gregtech.common.propagation.pollution.PollutionManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

import gregtech.common.pollution.Pollution;
import gregtech.common.propagation.api.PropagationSource;

public class TileEntityPollutionTestEmitter extends TileEntity {

    private static final int MIN_EMISSION = 100;
    private static final int MAX_EMISSION = 100_000;

    private Source source;

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
            source = new Source();

            Pollution.getPropagationManager(worldObj)
                .registerSource(source);

            registered = true;
        }

        source.addEmission(emissionPerSecond / 20.0D);

        PollutionManager manager = Pollution.getPropagationManager(worldObj);

        lastSample = Pollution.getLegacyPollution(worldObj, xCoord, yCoord, zCoord);

        if (worldObj.getTotalWorldTime() % 20L == 0L) {
            BlockPos pos = new BlockPos(xCoord, yCoord, zCoord);
            float reference = manager.sampleReference(pos);
        }
    }

    @Override
    public void invalidate() {
        retireSource();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        retireSource();
        super.onChunkUnload();
    }

    private void retireSource() {
        if (source != null) {
            source.retire();
            source = null;
        }

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
        private boolean valid = true;

        private void retire() {
            valid = false;
        }

        private void addEmission(double amount) {
            emission += amount;
        }

        @Override
        public Vec3 getPosition() {
            return Vec3.createVectorHelper(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D);
        }

        @Override
        public int getDimension() {
            return worldObj.provider.dimensionId;
        }

        @Override
        public boolean isValid() {
            return valid
                && !TileEntityPollutionTestEmitter.this.isInvalid()
                && worldObj != null
                && worldObj.getTileEntity(xCoord, yCoord, zCoord) == TileEntityPollutionTestEmitter.this;
        }

        @Override
        public double consumeEmission() {
            double result = emission;
            emission = 0.0D;
            return result;
        }

        @Override
        public String getEmitterDefinitionId() {
            return PollutionEmitterDefinitions.STANDARD_ID;
        }
    }
}
