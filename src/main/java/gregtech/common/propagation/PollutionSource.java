package gregtech.common.propagation;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

import net.minecraft.util.Vec3;


public class PollutionSource implements PropagationSource {

    private final MTEMultiBlockBase machine;

    private final int dimension;

    // Absolute block position
    private final Vec3 position;

    // Pollution grid position (16x16x16 cells)
    private final Vec3 cellPosition;

    // Pollution accumulated since last manager update
    private double pendingPollution;

    // Smoothed pollution value used by emitter
    private double effectivePollution;

    public PollutionSource(MTEMultiBlockBase machine) {
        this.machine = machine;

        IGregTechTileEntity base =
            machine.getBaseMetaTileEntity();

        if (base == null || base.getWorld() == null) {
            throw new IllegalArgumentException(
                "Cannot create PollutionSource for invalid machine"
            );
        }

        this.dimension =
            base.getWorld().provider.dimensionId;

        this.position = Vec3.createVectorHelper(
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord()
        );

        this.cellPosition = Vec3.createVectorHelper(
            base.getXCoord() >> 4,
            base.getYCoord() >> 4,
            base.getZCoord() >> 4
        );
    }

    /**
     * Adds pollution produced by the machine.
     */
    public void addPollution(double amount) {
        pendingPollution += amount;
    }

    /**
     * Returns pollution produced since previous check
     * and resets the counter.
     */
    public double consumeEmission() {
        double result = pendingPollution;
        pendingPollution = 0;
        return result;
    }

    /**
     * Checks whether the original machine still exists.
     */
    public boolean isValid() {
        if (machine == null) {
            return false;
        }

        IGregTechTileEntity base =
            machine.getBaseMetaTileEntity();

        return base != null
            && !base.isDead()
            && base.getMetaTileEntity() == machine;
    }

    public MTEMultiBlockBase getMachine() {
        return machine;
    }

    public int getDimension() {
        return dimension;
    }

    public Vec3 getPosition() {
        return position;
    }

    public Vec3 getCellPosition() {
        return cellPosition;
    }

    public double getEffectiveEmission() {
        return effectivePollution;
    }

    public void setEffectiveEmission(double value) {
        effectivePollution = value;
    }
}
