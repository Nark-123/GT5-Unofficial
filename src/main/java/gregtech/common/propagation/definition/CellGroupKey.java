package gregtech.common.propagation.definition;

public final class CellGroupKey {

    private final int x;
    private final int y;
    private final int z;

    public CellGroupKey(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof CellGroupKey)) {
            return false;
        }

        CellGroupKey other = (CellGroupKey) obj;

        return x == other.x
            && y == other.y
            && z == other.z;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }
}
