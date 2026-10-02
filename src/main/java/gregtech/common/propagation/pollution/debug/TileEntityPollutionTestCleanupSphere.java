package gregtech.common.propagation.pollution.debug;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import gregtech.common.pollution.Pollution;
import gregtech.common.propagation.pollution.PollutionEmitterDefinitions;
import gregtech.common.propagation.api.PropagationSource;

import static gregtech.GTLoggers.GT_FML_LOGGER;

public final class TileEntityPollutionTestCleanupSphere
    extends TileEntity {

    private static final double STRENGTH = 1_000_000.0D;

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
                    "[Cleanup test sphere] player={} pos={} {} {} sample={}",
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
        implements PropagationSource {

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
                .CLEANUP_SPHERE_ID;
        }

        @Override
        public Vec3 getPosition() {
            return Vec3.createVectorHelper(
                xCoord + 0.5D,
                yCoord + 0.5D,
                zCoord + 0.5D);
        }

        @Override
        public int getDimension() {
            return worldObj.provider.dimensionId;
        }

        @Override
        public boolean isValid() {
            return valid
                && worldObj != null
                && !TileEntityPollutionTestCleanupSphere.this
                .isInvalid()
                && worldObj.getTileEntity(
                xCoord, yCoord, zCoord)
                == TileEntityPollutionTestCleanupSphere.this;
        }

        @Override
        public double consumeEmission() {
            double result = strength;
            strength = 0.0D;
            return result;
        }
    }
}
