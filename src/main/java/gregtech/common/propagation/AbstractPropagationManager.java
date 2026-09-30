package gregtech.common.propagation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

import net.minecraft.util.Vec3;

public abstract class AbstractPropagationManager<E extends PropagationEmitter>
    implements PropagationManager {

    protected final int dimension;

    private final EmitterDefinitionSelector<E> sourceDefinitionSelector;

    private final Map<EmitterGroupRef<?>, E> emitterGroups = new HashMap<>();

    protected final List<E> emitters = new ArrayList<>();
    protected final PropagationSpatialIndex<E> spatialIndex =
        new PropagationSpatialIndex<>();

    protected final List<PropagationInfluencer> influencers = new ArrayList<>();
    protected final PropagationInfluencerSpatialIndex influencerSpatialIndex =
        new PropagationInfluencerSpatialIndex();

    private static final int INFLUENCER_UPDATE_INTERVAL = 20;
    private int influencerUpdateCursor;
    private int influencerUpdateAccumulator;

    private static final int EMITTER_UPDATE_INTERVAL = 20;
    private int emitterUpdateCursor;
    private int emitterUpdateAccumulator;

    protected AbstractPropagationManager(
        int dimension,
        PropagationModel<E> model) {

        if (model == null) {
            throw new IllegalArgumentException("Propagation model is null");
        }

        EmitterDefinitionSelector<E> sourceDefinitionSelector =
            model.getSourceDefinitionSelector();

        if (sourceDefinitionSelector == null) {
            throw new IllegalArgumentException("Emitter definition selector is null");
        }

        this.dimension = dimension;
        this.sourceDefinitionSelector = sourceDefinitionSelector;
    }

    @Override
    public final void registerInfluencer(PropagationInfluencer influencer) {
        if (influencer == null) {
            throw new IllegalArgumentException("Influencer is null");
        }

        if (influencers.contains(influencer)) {
            return;
        }

        Vec3 position = influencer.getPosition();
        validatePosition(position, "influencer");

        double range = influencer.getRange();

        if (!Double.isFinite(range) || range < 0.0D) {
            throw new IllegalArgumentException("Invalid influencer range: " + range);
        }

        influencers.add(influencer);
        influencerSpatialIndex.add(influencer);

        double coverageRange =
            influencerSpatialIndex.getCoverageRange(range);

        for (E emitter : spatialIndex.get(position, coverageRange)) {
            if (emitter.acceptsInfluencer(influencer)) {
                emitter.addInfluencer(influencer);
            }
        }
    }

    @Override
    public final void unregisterInfluencer(PropagationInfluencer influencer) {
        if (influencer == null) {
            return;
        }

        if (!influencers.remove(influencer)) {
            return;
        }

        Vec3 position = influencer.getPosition();
        double range = influencer.getRange();

        if (position != null
            && Double.isFinite(position.xCoord)
            && Double.isFinite(position.yCoord)
            && Double.isFinite(position.zCoord)
            && Double.isFinite(range)
            && range >= 0.0D) {

            double coverageRange =
                influencerSpatialIndex.getCoverageRange(range);

            for (E emitter : spatialIndex.get(position, coverageRange)) {
                emitter.removeInfluencer(influencer);
            }
        }

        influencerSpatialIndex.remove(influencer);
    }

    protected final void registerEmitterInfrastructure(E emitter) {
        if (emitter == null) {
            throw new IllegalArgumentException("Emitter is null");
        }

        if (emitter.getDimension() != dimension) {
            throw new IllegalArgumentException(
                "Emitter dimension mismatch: " + emitter.getDimension() + " != " + dimension);
        }

        if (emitters.contains(emitter)) {
            return;
        }

        emitters.add(emitter);
        spatialIndex.add(emitter);
        updateInfluencerCandidates(emitter);
    }

    public final int getEmitterCount() {
        return emitters.size();
    }

    public final int getInfluencerCount() {
        return influencers.size();
    }

    protected final void unregisterEmitterInfrastructure(E emitter) {
        emitters.remove(emitter);
        spatialIndex.remove(emitter);
        removeEmitterBindings(emitter);
    }

    @Override
    public final void registerSource(PropagationSource source) {
        if (source == null) {
            throw new IllegalArgumentException("Source is null");
        }

        if (source.getDimension() != dimension) {
            throw new IllegalArgumentException(
                "Source dimension mismatch: " + source.getDimension() + " != " + dimension);
        }

        Vec3 position = source.getPosition();
        validatePosition(position, "source");

        EmitterDefinition<?, ? extends E> definition =
            sourceDefinitionSelector.select(source);

        if (definition == null) {
            throw new IllegalStateException("Emitter definition selector returned null");
        }

        registerSourceWithDefinition(source, position, definition);
    }

    protected final void updateInfluencerCandidates(E emitter) {
        double range = influencerSpatialIndex.getCoverageRange(
                emitter.getPropagationRange());

        for (PropagationInfluencer influencer :
            influencerSpatialIndex.get(emitter.getPosition(), range)) {

            if (emitter.acceptsInfluencer(influencer)) {
                emitter.addInfluencer(influencer);
            }
        }
    }

    protected final void registerGroupedEmitter(
        EmitterGroupRef<?> groupRef,
        E emitter) {

        E existing = emitterGroups.get(groupRef);

        if (existing == emitter) {
            return;
        }

        if (existing != null) {
            throw new IllegalStateException(
                "Emitter group already occupied: " + groupRef.getDefinitionId());
        }

        registerEmitterManaged(emitter);
        emitterGroups.put(groupRef, emitter);
    }

    @Override
    public final void tick() {
        tickEmitters();
        tickInfluencers();
        onTick();
    }

    protected void onTick() {}

    protected final void tickEmitters() {
        if (emitters.isEmpty()) {
            emitterUpdateCursor = 0;
            emitterUpdateAccumulator = 0;
            return;
        }

        emitterUpdateAccumulator += emitters.size();

        int updates =
            emitterUpdateAccumulator / EMITTER_UPDATE_INTERVAL;

        emitterUpdateAccumulator %=
            EMITTER_UPDATE_INTERVAL;

        for (int i = 0; i < updates && !emitters.isEmpty(); i++) {
            if (emitterUpdateCursor >= emitters.size()) {
                emitterUpdateCursor = 0;
            }

            E emitter = emitters.get(emitterUpdateCursor);

            emitter.update();

            if (emitter.consumeStateChanged()) {
                onEmitterStateChanged(emitter);
            }

            if (!emitter.isValid()) {
                unregisterEmitterManaged(emitter);
                continue;
            }

            if (spatialIndex.ensureCoverage(emitter)) {
                updateInfluencerCandidates(emitter);
            }

            emitterUpdateCursor++;
        }
    }

    protected final void tickInfluencers() {
        if (influencers.isEmpty()) {
            influencerUpdateCursor = 0;
            influencerUpdateAccumulator = 0;
            return;
        }

        influencerUpdateAccumulator += influencers.size();

        int updates =
            influencerUpdateAccumulator / INFLUENCER_UPDATE_INTERVAL;

        influencerUpdateAccumulator %=
            INFLUENCER_UPDATE_INTERVAL;

        for (int i = 0; i < updates && !influencers.isEmpty(); i++) {
            if (influencerUpdateCursor >= influencers.size()) {
                influencerUpdateCursor = 0;
            }

            PropagationInfluencer influencer =
                influencers.get(influencerUpdateCursor);

            if (!influencer.isValid()) {
                unregisterInfluencer(influencer);
                continue;
            }

            influencerUpdateCursor++;
        }
    }

    protected final void removeEmitterBindings(E emitter) {
        Iterator<Map.Entry<EmitterGroupRef<?>, E>> groupIterator =
            emitterGroups.entrySet().iterator();

        while (groupIterator.hasNext()) {
            if (groupIterator.next().getValue() == emitter) {
                groupIterator.remove();
            }
        }
    }

    protected final void unregisterEmitterManaged(E emitter) {
        if (emitter == null) {
            return;
        }

        unregisterEmitterInfrastructure(emitter);
        onEmitterUnregistered(emitter);
    }

    protected abstract void onEmitterUnregistered(E emitter);

    protected abstract void onEmitterStateChanged(E emitter);

    protected abstract void validateEmitterRegistration(E emitter);

    protected abstract void onEmitterRegistered(E emitter);

    protected final void registerEmitterManaged(E emitter) {
        if (emitter == null) {
            throw new IllegalArgumentException("Emitter is null");
        }

        if (emitter.getDimension() != dimension) {
            throw new IllegalArgumentException(
                "Emitter dimension mismatch: " + emitter.getDimension() + " != " + dimension);
        }

        validateEmitterRegistration(emitter);
        registerEmitterInfrastructure(emitter);
        onEmitterRegistered(emitter);
    }

    @Override
    public final float sample(BlockPos pos) {
        if (pos == null) {
            throw new IllegalArgumentException("Position is null");
        }

        Vec3 position = Vec3.createVectorHelper(
            pos.x,
            pos.y,
            pos.z);

        return (float) calculateSample(position);
    }

    protected double calculateSample(Vec3 position) {
        double result = 0.0D;

        for (E emitter : spatialIndex.get(position)) {
            result += emitter.getInfluence(position);
        }

        return result;
    }

    @Override
    public float sampleReference(BlockPos pos) {
        Vec3 position = Vec3.createVectorHelper(
            pos.x,
            pos.y,
            pos.z);

        double result = 0.0D;

        for (E emitter : emitters) {
            result += emitter.getInfluence(position);
        }

        return (float) result;
    }
    public final List<E> getEmitters() {
        return Collections.unmodifiableList(emitters);
    }

    public final Set<E> getEmitterCandidates(Vec3 position, double range) {
        if (position == null) {
            throw new IllegalArgumentException("Position is null");
        }

        if (!Double.isFinite(range) || range < 0.0D) {
            throw new IllegalArgumentException("Invalid range: " + range);
        }

        return spatialIndex.get(position, range);
    }

    private <K> void registerSourceWithDefinition(
        PropagationSource source,
        Vec3 position,
        EmitterDefinition<K, ? extends E> definition) {

        K groupKey = definition.getGrouping().getGroupKey(source, position);

        if (groupKey == null) {
            throw new IllegalStateException(
                "Emitter grouping returned null: " + definition.getId());
        }

        EmitterGroupRef<K> groupRef =
            new EmitterGroupRef<>(definition.getId(), groupKey);

        E emitter = emitterGroups.get(groupRef);

        if (emitter == null) {
            emitter = definition.getFactory()
                .create(dimension, groupKey, source, position);

            if (emitter == null) {
                throw new IllegalStateException("Emitter factory returned null");
            }

            registerGroupedEmitter(groupRef, emitter);
        }

        emitter.addSource(source);
    }

    private void validatePosition(Vec3 position, String type) {
        if (position == null) {
            throw new IllegalArgumentException(type + " position is null");
        }

        if (!Double.isFinite(position.xCoord)
            || !Double.isFinite(position.yCoord)
            || !Double.isFinite(position.zCoord)) {

            throw new IllegalArgumentException("Invalid " + type + " position");
        }
    }
}
