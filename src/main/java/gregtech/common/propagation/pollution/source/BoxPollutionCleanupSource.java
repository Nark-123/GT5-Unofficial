package gregtech.common.propagation.pollution.source;

import gregtech.common.propagation.api.PropagationSource;
import gregtech.common.propagation.pollution.PollutionEmitterDefinitions;
import net.minecraft.util.Vec3;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

public final class BoxPollutionCleanupSource
    implements PropagationSource, BoxPollutionCleanupGeometry {

    private MetaTileEntity source;

    private final int dimension;
    private final Vec3 position;

    private final Vec3 fieldCenter;
    private final double halfX;
    private final double halfY;
    private final double halfZ;

    private double strength;
    private boolean toRemove;

    public BoxPollutionCleanupSource(
        MetaTileEntity source,
        Vec3 fieldCenter,
        double halfX,
        double halfY,
        double halfZ) {

        if (source == null || fieldCenter == null) {
            throw new IllegalArgumentException(
                "Invalid cleanup source");
        }

        IGregTechTileEntity base =
            source.getBaseMetaTileEntity();

        if (base == null || base.getWorld() == null) {
            throw new IllegalArgumentException(
                "Cannot create cleanup source "
                    + "for invalid source");
        }

        if (!Double.isFinite(halfX) || halfX <= 0.0D
            || !Double.isFinite(halfY) || halfY <= 0.0D
            || !Double.isFinite(halfZ) || halfZ <= 0.0D) {

            throw new IllegalArgumentException(
                "Invalid cleanup bounds");
        }

        this.source = source;
        this.dimension =
            base.getWorld().provider.dimensionId;

        this.position = Vec3.createVectorHelper(
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord());

        this.fieldCenter = fieldCenter;

        this.halfX = halfX;
        this.halfY = halfY;
        this.halfZ = halfZ;
    }

    @Override
    public String getEmitterDefinitionId() {
        return PollutionEmitterDefinitions
            .CLEANUP_BOX_ID;
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

    public Vec3 getFieldCenter() {
        return fieldCenter;
    }

    public double getHalfX() {
        return halfX;
    }

    public double getHalfY() {
        return halfY;
    }

    public double getHalfZ() {
        return halfZ;
    }
}
