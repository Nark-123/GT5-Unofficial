package gregtech.api.pollution;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.world.World;

public final class PollutionEngineRegistry {

    private static final Map<String, Function<World, IPollutionEngine>> FACTORIES = new HashMap<>();
    private static boolean frozen;
    private static String activeEngineId = "none";

    private PollutionEngineRegistry() {}

    public static synchronized void register(String id, Function<World, IPollutionEngine> factory) {
        if (frozen) throw new IllegalStateException("Pollution engine registration is closed");
        if (id == null || id.isEmpty() || "none".equals(id) || factory == null || FACTORIES.containsKey(id)) {
            throw new IllegalArgumentException("Invalid pollution engine: " + id);
        }

        if (!"none".equals(id)) activeEngineId = id;

        FACTORIES.put(id, factory);
    }

    public static synchronized IPollutionEngine create(World world, String id, Supplier<IPollutionEngine> noneFactory) {
        frozen = true;

        IPollutionEngine engine;
        if ("none".equals(id)) {
            engine = noneFactory.get();
        } else {
            Function<World, IPollutionEngine> factory = FACTORIES.get(id);
            if (factory == null) throw new IllegalStateException("Pollution engine not registered: " + id);
            engine = factory.apply(world);
        }

        if (engine == null || !id.equals(engine.id())) throw new IllegalStateException("Invalid pollution engine: " + id);
        return engine;
    }

    public static String activeEngineId() {
        return activeEngineId;
    }
}
