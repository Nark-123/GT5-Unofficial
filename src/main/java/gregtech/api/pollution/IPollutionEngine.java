package gregtech.api.pollution;

public interface IPollutionEngine {

    String id();

    void change(int x, int y, int z, int amount);

    int get(int x, int y, int z);

    default void tick() {}

    default void close() {}
}
