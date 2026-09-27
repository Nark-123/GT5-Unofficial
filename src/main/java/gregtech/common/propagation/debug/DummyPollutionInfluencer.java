package gregtech.common.propagation.debug;

import net.minecraft.util.Vec3;

import gregtech.common.propagation.InfluenceVector;
import gregtech.common.propagation.PropagationInfluencer;

public class DummyPollutionInfluencer implements PropagationInfluencer {

    private static final double RANGE = 160.0D;

    private static final double ETA_MAX = 1.0D;
    private static final double SIGMA_CORE = 10.0D;
    private static final double L_UP = 15.0D;
    private static final double L_DOWN = 80.0D;
    private static final double SIGMA0 = 5.0D;
    private static final double DIFFUSION = 8.0D;

    private final Vec3 position;
    private boolean valid = true;

    public DummyPollutionInfluencer(Vec3 position) {
        this.position = position;
    }

    @Override
    public double influence(Vec3 pos, InfluenceVector influenceVector) {
        if (influenceVector.distance == 0.0D) return 1.0D;

        double dx = influenceVector.normalizedVec.xCoord;
        double dy = influenceVector.normalizedVec.yCoord;
        double dz = influenceVector.normalizedVec.zCoord;

        double rx = pos.xCoord - position.xCoord;
        double ry = pos.yCoord - position.yCoord;
        double rz = pos.zCoord - position.zCoord;

        double r2 = rx * rx + ry * ry + rz * rz;

        double s = rx * dx + ry * dy + rz * dz;
        double rho2 = Math.max(0.0D, r2 - s * s);

        double core = Math.exp(-r2 / (2.0D * SIGMA_CORE * SIGMA_CORE));

        double l = s < 0.0D ? L_UP : L_DOWN;
        double downstream = Math.max(s, 0.0D);

        double sigma2 = SIGMA0 * SIGMA0 + 2.0D * DIFFUSION * downstream;

        double wake = Math.exp(-(s * s) / (l * l) - rho2 / (2.0D * sigma2));

        double combined = core + wake - core * wake;
        return 1.0D - ETA_MAX * combined;
    }

    @Override
    public Vec3 getPosition() {
        return position;
    }

    @Override
    public double getRange() {
        return RANGE;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    public void invalidate() {
        valid = false;
    }
}
