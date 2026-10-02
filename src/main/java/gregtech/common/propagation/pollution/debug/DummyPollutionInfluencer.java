package gregtech.common.propagation.pollution.debug;

import gregtech.common.propagation.pollution.PollutionInfluencerDefinitions;
import net.minecraft.util.Vec3;

import gregtech.common.propagation.api.InfluenceVector;
import gregtech.common.propagation.api.PropagationInfluencer;

public class DummyPollutionInfluencer implements PropagationInfluencer {

    private static final double RANGE = 160.0D;

    private static final double ETA_MAX = 1.0D;
    private static final double SIGMA_CORE = 10.0D;
    private static final double L_UP = 15.0D;
    private static final double L_DOWN = 80.0D;
    private static final double SIGMA0 = 5.0D;
    private static final double DIFFUSION = 8.0D;
    private static final double LOG2_E = 1.4426950408889634D;

    private final Vec3 position;
    private boolean valid = true;

    public DummyPollutionInfluencer(Vec3 position) {
        this.position = position;
    }

    private static double fastExpNeg(double x) {
        double y = x * LOG2_E;

        int n = (int) y;
        double f = y - n;

        double p = ((-0.03951000D * f + 0.23059332D) * f - 0.69107581D) * f + 0.99989849D;

        long scaleBits = (long) (1023 - n) << 52;
        double scale = Double.longBitsToDouble(scaleBits);

        return scale * p;
    }

    @Override
    public double influence(Vec3 pos, InfluenceVector influenceVector) {
        double rx = pos.xCoord - position.xCoord;
        double ry = pos.yCoord - position.yCoord;
        double rz = pos.zCoord - position.zCoord;

        double r2 = rx * rx + ry * ry + rz * rz;

        if (r2 > RANGE * RANGE) {
            return 1.0D;
        }

        double dx = influenceVector.normalizedVec.xCoord;
        double dy = influenceVector.normalizedVec.yCoord;
        double dz = influenceVector.normalizedVec.zCoord;

        double s = rx * dx + ry * dy + rz * dz;
        double rho2 = Math.max(0.0D, r2 - s * s);

        double core = fastExpNeg(r2 / (2.0D * SIGMA_CORE * SIGMA_CORE));

        double l = s < 0.0D ? L_UP : L_DOWN;
        double downstream = Math.max(s, 0.0D);

        double sigma2 = SIGMA0 * SIGMA0 + 2.0D * DIFFUSION * downstream;

        double wake = fastExpNeg(-(-(s * s) / (l * l) - rho2 / (2.0D * sigma2)));

        double combined = core + wake - core * wake;
        return Math.max(0.0D, Math.min(1.0D, 1.0D - ETA_MAX * combined));
    }

    @Override
    public String getInfluencerDefinitionId() {
        return PollutionInfluencerDefinitions.DUMMY_ID;
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
