package gregtech.common.propagation.pollution.debug;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;

import gregtech.common.pollution.Pollution;
import gregtech.common.propagation.pollution.source.BoxPollutionCleanupGeometry;
import gregtech.common.propagation.pollution.PollutionEmitterDefinitions;
import gregtech.common.propagation.api.PropagationSource;

import static gregtech.GTLoggers.GT_FML_LOGGER;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

public final class TileEntityPollutionTestCleanupBox
    extends TileEntity {

    private static final double STRENGTH = 1_000_000.0D;

    private static final double HALF_X = 3.5D;
    private static final double HALF_Y = 4.5D;
    private static final double HALF_Z = 3.5D;

    private Source source;

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }

        if (source == null) {
            source = new Source();

            Pollution.getPropagationManager(worldObj)
                .registerSource(source);
        }

        source.setStrength(STRENGTH);

        if (worldObj.getTotalWorldTime() % 20L == 0L) {
            for (Object object : worldObj.playerEntities) {
                EntityPlayer player = (EntityPlayer) object;

                BlockPos pos = new BlockPos(
                    MathHelper.floor_double(player.posX),
                    MathHelper.floor_double(player.posY),
                    MathHelper.floor_double(player.posZ));

                float sample =
                    Pollution.getPropagationManager(worldObj)
                        .sample(pos);

                GT_FML_LOGGER.info(
                    "[Cleanup test box] player={} pos={} {} {} sample={}",
                    player.getCommandSenderName(),
                    pos.x,
                    pos.y,
                    pos.z,
                    sample);
            }
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
    }

    private final class Source
        implements PropagationSource,
        BoxPollutionCleanupGeometry {

        private double strength;
        private boolean valid = true;

        private void setStrength(double strength) {
            this.strength = strength;
        }

        private void retire() {
            valid = false;
            strength = 0.0D;
        }

        @Override
        public String getEmitterDefinitionId() {
            return PollutionEmitterDefinitions
                .CLEANUP_BOX_ID;
        }

        @Override
        public Vec3 getPosition() {
            return Vec3.createVectorHelper(
                xCoord + 0.5D,
                yCoord + 0.5D,
                zCoord + 0.5D);
        }

        @Override
        public Vec3 getFieldCenter() {
            return Vec3.createVectorHelper(
                xCoord + 0.5D,
                yCoord - 3.5D,
                zCoord + 0.5D);
        }

        @Override
        public double getHalfX() {
            return HALF_X;
        }

        @Override
        public double getHalfY() {
            return HALF_Y;
        }

        @Override
        public double getHalfZ() {
            return HALF_Z;
        }

        @Override
        public int getDimension() {
            return worldObj.provider.dimensionId;
        }

        @Override
        public boolean isValid() {
            return valid
                && worldObj != null
                && !TileEntityPollutionTestCleanupBox.this
                .isInvalid()
                && worldObj.getTileEntity(
                xCoord, yCoord, zCoord)
                == TileEntityPollutionTestCleanupBox.this;
        }

        @Override
        public double consumeEmission() {
            double result = strength;
            strength = 0.0D;
            return result;
        }
    }
}
