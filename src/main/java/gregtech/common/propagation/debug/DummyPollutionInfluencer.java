package gregtech.common.propagation.debug;

import gregtech.common.propagation.InfluenceVector;
import gregtech.common.propagation.PropagationInfluencer;

import net.minecraft.util.Vec3;

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
        double ex = position.xCoord - influenceVector.emitter.xCoord;
        double ey = position.yCoord - influenceVector.emitter.yCoord;
        double ez = position.zCoord - influenceVector.emitter.zCoord;

        double emitterDistanceSquared = ex * ex + ey * ey + ez * ez;

        if (emitterDistanceSquared == 0.0D) {
            return 1.0D;
        }

        double inverseEmitterDistance = 1.0D / Math.sqrt(emitterDistanceSquared);

        double dx = ex * inverseEmitterDistance;
        double dy = ey * inverseEmitterDistance;
        double dz = ez * inverseEmitterDistance;

        double rx = pos.xCoord - position.xCoord;
        double ry = pos.yCoord - position.yCoord;
        double rz = pos.zCoord - position.zCoord;

        double r2 = rx * rx + ry * ry + rz * rz;

        double s = rx * dx + ry * dy + rz * dz;
        double rho2 = Math.max(0.0D, r2 - s * s);

        double core = Math.exp(
            -r2 / (2.0D * SIGMA_CORE * SIGMA_CORE)
        );

        double l = s < 0.0D ? L_UP : L_DOWN;
        double downstream = Math.max(s, 0.0D);

        double sigma2 = SIGMA0 * SIGMA0 + 2.0D * DIFFUSION * downstream;

        double wake = Math.exp(
            -(s * s) / (l * l)
                - rho2 / (2.0D * sigma2)
        );

        double combined = 1.0D - (1.0D - core) * (1.0D - wake);
        double eta = ETA_MAX * Math.min(1.0D, Math.max(0.0D, combined));

        return 1.0D - eta;
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
