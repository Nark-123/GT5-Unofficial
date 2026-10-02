package gregtech.api.net;

import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.google.common.io.ByteArrayDataInput;

import gregtech.GTMod;
import gregtech.common.propagation.pollution.PollutionManager;
import io.netty.buffer.ByteBuf;

public class GTPacketPollutionEmitter extends GTPacket {

    private boolean fullSnapshot;

    private int[] cellX;
    private int[] cellY;
    private int[] cellZ;
    private double[] pollution;

    public GTPacketPollutionEmitter() {}

    public GTPacketPollutionEmitter(boolean fullSnapshot, int[] cellX, int[] cellY, int[] cellZ, double[] pollution) {
        this.fullSnapshot = fullSnapshot;
        this.cellX = cellX;
        this.cellY = cellY;
        this.cellZ = cellZ;
        this.pollution = pollution;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeBoolean(fullSnapshot);
        out.writeInt(pollution.length);

        for (int i = 0; i < pollution.length; i++) {
            out.writeInt(cellX[i]);
            out.writeInt(cellY[i]);
            out.writeInt(cellZ[i]);
            out.writeDouble(pollution[i]);
        }
    }

    @Override
    public GTPacket decode(ByteArrayDataInput data) {
        boolean fullSnapshot = data.readBoolean();
        int count = data.readInt();

        int[] cellX = new int[count];
        int[] cellY = new int[count];
        int[] cellZ = new int[count];
        double[] pollution = new double[count];

        for (int i = 0; i < count; i++) {
            cellX[i] = data.readInt();
            cellY[i] = data.readInt();
            cellZ[i] = data.readInt();
            pollution[i] = data.readDouble();
        }

        return new GTPacketPollutionEmitter(fullSnapshot, cellX, cellY, cellZ, pollution);
    }

    @Override
    public void process(IBlockAccess world) {
        if (!(world instanceof World)) {
            return;
        }

        World clientWorld = (World) world;
        int dimension = clientWorld.provider.dimensionId;

        if (fullSnapshot) {
            GTMod.clientProxy()
                .getClientPollutionManager(dimension)
                .applyEmitterSnapshot(cellX, cellY, cellZ, pollution);

            return;
        }

        PollutionManager manager = GTMod.clientProxy()
            .getClientPollutionManager(dimension);

        for (int i = 0; i < pollution.length; i++) {
            manager.setEmitterPollution(cellX[i], cellY[i], cellZ[i], pollution[i]);
        }
    }

    @Override
    public byte getPacketID() {
        return GTPacketTypes.POLLUTION_EMITTER.id;
    }
}
