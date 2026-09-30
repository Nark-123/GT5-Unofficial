package gregtech.common.propagation;

import net.minecraft.util.Vec3;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

public class PollutionSource implements PropagationSource {

    private MetaTileEntity source;

    private final int dimension;
    private final Vec3 position;

    private double emission;
    private boolean toRemove;

    public PollutionSource(MetaTileEntity source) {
        this.source = source;

        IGregTechTileEntity base = source.getBaseMetaTileEntity();

        if (base == null || base.getWorld() == null) {
            throw new IllegalArgumentException("Cannot create PollutionSource for invalid source");
        }

        this.dimension = base.getWorld().provider.dimensionId;
        this.position = Vec3.createVectorHelper(base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    @Override
    public double consumeEmission() {
        double buffer = emission;
        emission = 0;
        return buffer;
    }

    @Override
    public boolean isValid() {
        if (toRemove || source == null) {
            return false;
        }

        IGregTechTileEntity base = source.getBaseMetaTileEntity();

        return base != null && !base.isDead() && base.getMetaTileEntity() == source;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public Vec3 getPosition() {
        return position;
    }

    public void remove() {
        toRemove = true;
    }

    public void addPollution(double amount) {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException(
                "Invalid pollution emission: " + amount);
        }

        double newEmission = emission + amount;

        if (!Double.isFinite(newEmission)) {
            throw new IllegalStateException(
                "Pollution source emission overflow");
        }

        emission = newEmission;
    }
}
