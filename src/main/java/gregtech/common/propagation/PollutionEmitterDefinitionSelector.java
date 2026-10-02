package gregtech.common.propagation;

import java.util.HashMap;
import java.util.Map;

public final class PollutionEmitterDefinitionSelector
    implements EmitterDefinitionSelector<PollutionFieldEmitter> {

    private final Map<
        String,
        EmitterDefinition<
            ?, ? extends PollutionFieldEmitter>>
        definitions = new HashMap<>();

    public void register(
        EmitterDefinition<
            ?, ? extends PollutionFieldEmitter> definition) {

        if (definition == null) {
            throw new IllegalArgumentException(
                "Emitter definition is null");
        }

        String id = definition.getId();

        if (definitions.containsKey(id)) {
            throw new IllegalStateException(
                "Duplicate emitter definition: " + id);
        }

        definitions.put(id, definition);
    }

    @Override
    public EmitterDefinition<
        ?, ? extends PollutionFieldEmitter> select(
        PropagationSource source) {

        String id = source.getEmitterDefinitionId();

        EmitterDefinition<
            ?, ? extends PollutionFieldEmitter> definition =
            definitions.get(id);

        if (definition == null) {
            throw new IllegalArgumentException(
                "Unknown emitter definition: " + id);
        }

        return definition;
    }
}
