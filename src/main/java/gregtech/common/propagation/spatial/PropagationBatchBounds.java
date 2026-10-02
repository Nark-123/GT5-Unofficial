package gregtech.common.propagation.spatial;

final class PropagationBatchBounds {

    final int minX;
    final int minY;
    final int minZ;
    final int maxX;
    final int maxY;
    final int maxZ;

    PropagationBatchBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    boolean contains(PropagationBatchBounds other) {
        return other.minX >= minX && other.minY >= minY
            && other.minZ >= minZ
            && other.maxX <= maxX
            && other.maxY <= maxY
            && other.maxZ <= maxZ;
    }

    boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    PropagationBatchBounds expandToInclude(PropagationBatchBounds other) {
        return new PropagationBatchBounds(
            Math.min(minX, other.minX),
            Math.min(minY, other.minY),
            Math.min(minZ, other.minZ),
            Math.max(maxX, other.maxX),
            Math.max(maxY, other.maxY),
            Math.max(maxZ, other.maxZ));
    }
}
