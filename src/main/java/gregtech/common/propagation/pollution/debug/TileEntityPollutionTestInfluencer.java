package gregtech.common.propagation.pollution.debug;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;

import gregtech.common.pollution.Pollution;
import gregtech.common.propagation.pollution.PollutionManager;

public class TileEntityPollutionTestInfluencer extends TileEntity {

    private PollutionManager manager;
    private DummyPollutionInfluencer influencer;
    private boolean registered;

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || registered) return;

        Vec3 position = Vec3.createVectorHelper(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D);

        influencer = new DummyPollutionInfluencer(position);
        manager = Pollution.getPropagationManager(worldObj);

        manager.registerInfluencer(influencer);
        registered = true;
    }

    @Override
    public void invalidate() {
        unregisterInfluencer();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        unregisterInfluencer();
        super.onChunkUnload();
    }

    private void unregisterInfluencer() {
        if (!registered) return;

        manager.unregisterInfluencer(influencer);
        influencer.invalidate();

        influencer = null;
        manager = null;
        registered = false;
    }
}
