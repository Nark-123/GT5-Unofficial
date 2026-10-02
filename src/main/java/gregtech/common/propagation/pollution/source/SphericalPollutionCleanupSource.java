package gregtech.common.propagation.pollution.source;

import gregtech.common.propagation.api.PropagationSource;
import gregtech.common.propagation.pollution.PollutionEmitterDefinitions;
import net.minecraft.util.Vec3;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

public final class SphericalPollutionCleanupSource
    implements PropagationSource {

    private MetaTileEntity source;

    private final int dimension;
    private final Vec3 position;

    private double strength;
    private boolean toRemove;

    public SphericalPollutionCleanupSource(
        MetaTileEntity source) {

        if (source == null) {
            throw new IllegalArgumentException(
                "Source is null");
        }

        IGregTechTileEntity base =
            source.getBaseMetaTileEntity();

        if (base == null || base.getWorld() == null) {
            throw new IllegalArgumentException(
                "Cannot create cleanup source "
                    + "for invalid source");
        }

        this.source = source;
        this.dimension =
            base.getWorld().provider.dimensionId;

        this.position = Vec3.createVectorHelper(
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord());
    }

    @Override
    public String getEmitterDefinitionId() {
        return PollutionEmitterDefinitions
            .CLEANUP_SPHERE_ID;
    }

    @Override
    public double consumeEmission() {
        double result = strength;
        strength = 0.0D;
        return result;
    }

    public void setStrength(double strength) {
        if (!Double.isFinite(strength)
            || strength < 0.0D) {

            throw new IllegalArgumentException(
                "Invalid cleanup strength: "
                    + strength);
        }

        this.strength = strength;
    }

    @Override
    public boolean isValid() {
        if (toRemove || source == null) {
            return false;
        }

        IGregTechTileEntity base =
            source.getBaseMetaTileEntity();

        return base != null
            && !base.isDead()
            && base.getMetaTileEntity() == source;
    }

    public void remove() {
        toRemove = true;
        strength = 0.0D;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public Vec3 getPosition() {
        return position;
    }
}
