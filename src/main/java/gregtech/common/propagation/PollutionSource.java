package gregtech.common.propagation;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.util.Vec3;

public class PollutionSource implements PropagationSource {

    private final MetaTileEntity source;
    private final int dimension;
    private final Vec3 position;

    private double pendingPollution;
    private double effectivePollution;

    public PollutionSource(MetaTileEntity source) {
        this.source = source;

        IGregTechTileEntity base = source.getBaseMetaTileEntity();

        if (base == null || base.getWorld() == null) {
            throw new IllegalArgumentException("Cannot create PollutionSource for invalid source");
        }

        this.dimension = base.getWorld().provider.dimensionId;
        this.position = Vec3.createVectorHelper(base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    public void addPollution(double amount) {
        pendingPollution += amount;
    }

    @Override
    public double consumeEmission() {
        double result = pendingPollution;
        pendingPollution = 0;
        return result;
    }

    @Override
    public boolean isValid() {
        if (source == null) return false;

        IGregTechTileEntity base = source.getBaseMetaTileEntity();

        return base != null
            && !base.isDead()
            && base.getMetaTileEntity() == source;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public Vec3 getPosition() {
        return position;
    }

    @Override
    public double getEffectiveEmission() {
        return effectivePollution;
    }

    @Override
    public void setEffectiveEmission(double value) {
        effectivePollution = value;
    }
}
