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
    private final Set<E> emitterIdentities = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<E, EmitterGroupRef<?>> emitterGroupRefs = new IdentityHashMap<>();
    protected final PropagationSpatialIndex<E> spatialIndex = new PropagationSpatialIndex<>();

    protected final List<PropagationInfluencer> influencers = new ArrayList<>();
    protected final PropagationInfluencerSpatialIndex influencerSpatialIndex = new PropagationInfluencerSpatialIndex();
    private final Set<PropagationInfluencer> influencerIdentities = Collections.newSetFromMap(new IdentityHashMap<>());

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

        if (influencerIdentities.contains(influencer)) {
            return;
        }

        Vec3 position = influencer.getPosition();
        validatePosition(position, "influencer");

        double range = influencer.getRange();

        if (!Double.isFinite(range) || range < 0.0D) {
            throw new IllegalArgumentException("Invalid influencer range: " + range);
        }

        try {
            influencerSpatialIndex.add(influencer);
            influencers.add(influencer);
            influencerIdentities.add(influencer);

            double coverageRange =
                influencerSpatialIndex.getCoverageRange(range);

            for (E emitter : spatialIndex.get(position, coverageRange)) {
                if (emitter.acceptsInfluencer(influencer)) {
                    emitter.addInfluencer(influencer);
                }
            }
        } catch (RuntimeException failure) {
            rollbackInfluencerRegistration(influencer);
            throw failure;
        }
    }

    private void rollbackInfluencerRegistration(
        PropagationInfluencer influencer) {

        for (E emitter : emitters) {
            emitter.removeInfluencer(influencer);
        }

        influencerIdentities.remove(influencer);
        removeInfluencerIdentity(influencer);
        influencerSpatialIndex.remove(influencer);
    }

    private boolean removeInfluencerIdentity(
        PropagationInfluencer influencer) {

        for (Iterator<PropagationInfluencer> iterator =
             influencers.iterator(); iterator.hasNext();) {

            if (iterator.next() == influencer) {
                iterator.remove();
                return true;
            }
        }

        return false;
    }

    @Override
    public final void unregisterInfluencer(
        PropagationInfluencer influencer) {

        if (influencer == null
            || !influencerIdentities.remove(influencer)) {
            return;
        }

        removeInfluencerIdentity(influencer);

        for (E emitter : emitters) {
            emitter.removeInfluencer(influencer);
        }

        influencerSpatialIndex.remove(influencer);
    }

    protected final void refreshEmitterGeometry(E emitter) {
        if (emitter == null) {
            throw new IllegalArgumentException("Emitter is null");
        }

        if (!emitterIdentities.contains(emitter)) {
            throw new IllegalStateException(
                "Emitter is not registered");
        }

        if (spatialIndex.ensureCoverage(emitter)) {
            updateInfluencerCandidates(emitter);
        }
    }

    protected final void registerEmitterInfrastructure(E emitter) {
        if (emitter == null) {
            throw new IllegalArgumentException("Emitter is null");
        }

        if (emitter.getDimension() != dimension) {
            throw new IllegalArgumentException(
                "Emitter dimension mismatch: " + emitter.getDimension() + " != " + dimension);
        }

        if (emitterIdentities.contains(emitter)) {
            return;
        }

        spatialIndex.add(emitter);
        emitters.add(emitter);
        emitterIdentities.add(emitter);
        updateInfluencerCandidates(emitter);
    }

    private void removeEmitterIdentity(E emitter) {
        for (Iterator<E> iterator = emitters.iterator(); iterator.hasNext();) {
            if (iterator.next() == emitter) {
                iterator.remove();
                return;
            }
        }
    }

    public final int getEmitterCount() {
        return emitters.size();
    }

    public final int getInfluencerCount() {
        return influencers.size();
    }

    protected final void unregisterEmitterInfrastructure(E emitter) {
        emitterIdentities.remove(emitter);
        removeEmitterIdentity(emitter);
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

        if (groupRef == null) {
            throw new IllegalArgumentException(
                "Emitter group ref is null");
        }

        if (emitter == null) {
            throw new IllegalArgumentException(
                "Emitter is null");
        }

        E existing = emitterGroups.get(groupRef);

        if (existing == emitter) {
            return;
        }

        if (existing != null) {
            throw new IllegalStateException(
                "Emitter group already occupied: "
                    + groupRef.getDefinitionId());
        }

        EmitterGroupRef<?> existingRef =
            emitterGroupRefs.get(emitter);

        if (existingRef != null) {
            throw new IllegalStateException(
                "Emitter already belongs to group: "
                    + existingRef.getDefinitionId());
        }

        if (emitterIdentities.contains(emitter)) {
            throw new IllegalStateException(
                "Registered emitter has no group binding");
        }

        validateEmitterRegistration(groupRef, emitter);

        try {
            registerEmitterInfrastructure(emitter);

            emitterGroups.put(groupRef, emitter);
            emitterGroupRefs.put(emitter, groupRef);

            onEmitterRegistered(groupRef, emitter);
        } catch (RuntimeException failure) {
            rollbackEmitterRegistration(groupRef, emitter);
            throw failure;
        }
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

            refreshEmitterGeometry(emitter);

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
        EmitterGroupRef<?> groupRef = emitterGroupRefs.remove(emitter);

        if (groupRef != null) {
            if (emitterGroups.get(groupRef) == emitter) {
                emitterGroups.remove(groupRef);
            }

            return;
        }

        // Defensive cleanup for partially registered/legacy state.
        Iterator<Map.Entry<EmitterGroupRef<?>, E>> iterator = emitterGroups.entrySet().iterator();

        while (iterator.hasNext()) {
            if (iterator.next().getValue() == emitter) {
                iterator.remove();
            }
        }
    }

    protected final void unregisterEmitterManaged(E emitter) {
        if (emitter == null) {
            return;
        }

        if (!emitterIdentities.contains(emitter)) {
            return;
        }

        EmitterGroupRef<?> groupRef =
            emitterGroupRefs.get(emitter);

        if (groupRef == null) {
            throw new IllegalStateException(
                "Registered emitter has no group binding");
        }

        unregisterEmitterInfrastructure(emitter);
        onEmitterUnregistered(groupRef, emitter);
    }

    protected abstract void onEmitterUnregistered(EmitterGroupRef<?> groupRef, E emitter);

    protected abstract void onEmitterStateChanged(E emitter);

    protected abstract void validateEmitterRegistration(EmitterGroupRef<?> groupRef, E emitter);

    protected abstract void onEmitterRegistered(EmitterGroupRef<?> groupRef, E emitter);

    protected void onEmitterRegistrationFailed(EmitterGroupRef<?> groupRef, E emitter) {}

    private void rollbackEmitterRegistration(
        EmitterGroupRef<?> groupRef,
        E emitter) {

        for (PropagationInfluencer influencer : influencers) {
            emitter.removeInfluencer(influencer);
        }

        emitterIdentities.remove(emitter);
        removeEmitterIdentity(emitter);
        spatialIndex.remove(emitter);
        removeEmitterBindings(emitter);

        onEmitterRegistrationFailed(groupRef, emitter);
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

    protected final EmitterGroupRef<?> getEmitterGroupRef(E emitter) {
        return emitterGroupRefs.get(emitter);
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
        boolean created = false;

        if (emitter == null) {
            emitter = definition.getFactory()
                .create(dimension, groupKey, source, position);

            if (emitter == null) {
                throw new IllegalStateException("Emitter factory returned null");
            }

            registerGroupedEmitter(groupRef, emitter);
            created = true;
        }

        try {
            emitter.addSource(source);
        } catch (RuntimeException failure) {
            if (created) {
                rollbackEmitterRegistration(groupRef, emitter);
            }

            throw failure;
        }
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
