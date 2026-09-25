package gregtech.common.propagation;

import net.minecraft.util.Vec3;

public class PollutionBurstSource implements PropagationSource {

    private final int dimension;
    private final Vec3 position;
    private double emission;
    private boolean consumed;

    public PollutionBurstSource(int dimension, Vec3 position, double emission) {
        this.dimension = dimension;
        this.position = position;
        this.emission = emission;
    }

    @Override
    public Vec3 getPosition() {
        return position;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public boolean isValid() {
        return !consumed;
    }

    @Override
    public double consumeEmission() {
        if (consumed) {
            return 0.0D;
        }

        consumed = true;

        double result = emission;
        emission = 0.0D;
        return result;
    }
}
