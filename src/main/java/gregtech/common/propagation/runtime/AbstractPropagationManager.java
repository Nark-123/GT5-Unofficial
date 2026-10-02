package gregtech.common.propagation.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

import gregtech.common.propagation.definition.EmitterDefinition;
import gregtech.common.propagation.definition.EmitterDefinitionSelector;
import gregtech.common.propagation.definition.EmitterGroupRef;
import gregtech.common.propagation.definition.EmitterStateAdapter;
import gregtech.common.propagation.definition.EmitterStateSnapshot;
import gregtech.common.propagation.definition.InfluencerDefinition;
import gregtech.common.propagation.definition.InfluencerDefinitionSelector;
import gregtech.common.propagation.definition.InfluencerStateAdapter;
import gregtech.common.propagation.definition.InfluencerStateSnapshot;
import gregtech.common.propagation.spatial.PropagationInfluencerSpatialIndex;
import gregtech.common.propagation.spatial.PropagationSpatialIndex;
import gregtech.common.propagation.api.PropagationEmitter;
import gregtech.common.propagation.api.PropagationInfluencer;
import gregtech.common.propagation.api.PropagationManager;
import gregtech.common.propagation.api.PropagationModel;
import gregtech.common.propagation.api.PropagationSource;
import net.minecraft.util.Vec3;

public abstract class AbstractPropagationManager<E extends PropagationEmitter>
    implements PropagationManager {

    protected final int dimension;

    private final EmitterDefinitionSelector<E> sourceDefinitionSelector;

    private Map<EmitterGroupRef<?>, E> emitterGroups = new HashMap<>();
    private Map<E, EmitterGroupRef<?>> emitterGroupRefs = new IdentityHashMap<>();
    private Map<E, EmitterRuntimeState> emitterRuntimeStates = new IdentityHashMap<>();
    private Map<Long, E> emittersByRuntimeId = new HashMap<>();
    private Map<Long, EmitterRuntimeChange> pendingEmitterChanges = new LinkedHashMap<>();
    private boolean trackEmitterChanges;
    private long nextEmitterId = 1L;

    private final InfluencerDefinitionSelector influencerDefinitionSelector;

    private Map<PropagationInfluencer, InfluencerRuntimeState> influencerRuntimeStates = new IdentityHashMap<>();

    private Map<Long, PropagationInfluencer> influencersByRuntimeId = new HashMap<>();

    private final Map<Long, InfluencerRuntimeChange> pendingInfluencerChanges = new LinkedHashMap<>();

    private long nextInfluencerId = 1L;

    private final ReplicaUpdateQueue replicaUpdateQueue = new ReplicaUpdateQueue();

    private final PropagationRuntimeMode runtimeMode;

    protected List<E> emitters = new ArrayList<>();
    private final Set<E> emitterIdentities = Collections.newSetFromMap(new IdentityHashMap<>());
    protected PropagationSpatialIndex<E> spatialIndex = new PropagationSpatialIndex<>();

    protected List<PropagationInfluencer> influencers = new ArrayList<>();
    protected PropagationInfluencerSpatialIndex influencerSpatialIndex = new PropagationInfluencerSpatialIndex();
    private Set<PropagationInfluencer> influencerIdentities = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final int INFLUENCER_UPDATE_INTERVAL = 20;
    private int influencerUpdateCursor;
    private int influencerUpdateAccumulator;

    private static final int EMITTER_UPDATE_INTERVAL = 20;
    private int emitterUpdateCursor;
    private int emitterUpdateAccumulator;


    protected AbstractPropagationManager(
        int dimension,
        PropagationModel<E> model,
        PropagationRuntimeMode runtimeMode,
        boolean trackEmitterChanges) {

        if (model == null) {
            throw new IllegalArgumentException(
                "Propagation model is null");
        }

        if (runtimeMode == null) {
            throw new IllegalArgumentException(
                "Propagation runtime mode is null");
        }

        if (runtimeMode == PropagationRuntimeMode.REPLICA
            && trackEmitterChanges) {

            throw new IllegalArgumentException(
                "Replica manager cannot track local emitter changes");
        }

        EmitterDefinitionSelector<E> selector =
            model.getSourceDefinitionSelector();

        if (selector == null) {
            throw new IllegalArgumentException(
                "Emitter definition selector is null");
        }

        this.influencerDefinitionSelector =
            model.getInfluencerDefinitionSelector();

        if (influencerDefinitionSelector == null) {
            throw new IllegalArgumentException(
                "Influencer definition selector is null");
        }

        this.dimension = dimension;
        this.sourceDefinitionSelector = selector;
        this.runtimeMode = runtimeMode;
        this.trackEmitterChanges = trackEmitterChanges;
    }

    @Override
    public final void registerInfluencer(
        PropagationInfluencer influencer) {

        requireAuthoritative(
            "registerInfluencer");

        if (influencer == null) {
            throw new IllegalArgumentException(
                "Influencer is null");
        }

        if (influencers.contains(
            influencer)) {

            return;
        }

        Vec3 position =
            influencer.getPosition();

        validatePosition(
            position,
            "influencer");

        double range =
            influencer.getRange();

        if (!Double.isFinite(range)
            || range < 0.0D) {

            throw new IllegalArgumentException(
                "Invalid influencer range: "
                    + range);
        }

        String definitionId =
            influencer
                .getInfluencerDefinitionId();

        InfluencerDefinition<?, ?> definition =
            influencerDefinitionSelector
                .getDefinition(definitionId);

        if (definition == null) {
            throw new IllegalArgumentException(
                "Unknown influencer definition: "
                    + definitionId);
        }

        if (!definition
            .getInfluencerType()
            .isInstance(influencer)) {

            throw new IllegalArgumentException(
                "Influencer type mismatch: "
                    + definitionId);
        }

        long influencerId =
            allocateInfluencerId();

        boolean infrastructureRegistered =
            false;

        boolean runtimeRegistered =
            false;

        try {
            registerInfluencerInfrastructure(
                influencer);

            infrastructureRegistered = true;

            registerInfluencerRuntime(
                influencer,
                influencerId,
                definitionId);

            runtimeRegistered = true;

            queueInfluencerUpsert(
                influencer);

        } catch (RuntimeException e) {
            if (runtimeRegistered) {
                unregisterInfluencerRuntime(
                    influencer);
            }

            if (infrastructureRegistered) {
                unregisterInfluencerInfrastructure(
                    influencer);
            }

            throw e;
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

        requireAuthoritative(
            "unregisterInfluencer");

        if (influencer == null
            || !influencers.contains(
            influencer)) {

            return;
        }

        InfluencerRuntimeState runtime =
            requireInfluencerRuntimeState(
                influencer);

        long influencerId =
            runtime.getInfluencerId();

        String definitionId =
            runtime.getDefinitionId();

        unregisterInfluencerInfrastructure(
            influencer);

        unregisterInfluencerRuntime(
            influencer);

        if (trackEmitterChanges) {
            pendingInfluencerChanges.put(
                influencerId,
                InfluencerRuntimeChange.remove(
                    influencerId,
                    definitionId));
        }
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

    public final List<InfluencerRuntimeChange>
    consumeInfluencerRuntimeChanges() {

        requireAuthoritative(
            "consumeInfluencerRuntimeChanges");

        if (pendingInfluencerChanges.isEmpty()) {
            return Collections.emptyList();
        }

        List<InfluencerRuntimeChange> changes =
            new ArrayList<>(
                pendingInfluencerChanges.values());

        pendingInfluencerChanges.clear();

        return changes;
    }

    public final List<EmitterRuntimeChange> consumeEmitterRuntimeChanges() {
        if (!isAuthoritative()) {
            return Collections.emptyList();
        }

        if (pendingEmitterChanges.isEmpty()) {
            return Collections.emptyList();
        }

        List<EmitterRuntimeChange> result =
            new ArrayList<>(
                pendingEmitterChanges.values());

        pendingEmitterChanges.clear();

        return result;
    }

    private void queueEmitterRemove(
        long emitterId,
        String definitionId,
        long emitterRevision) {

        if (!trackEmitterChanges) {
            return;
        }

        pendingEmitterChanges.put(
            emitterId,
            EmitterRuntimeChange.remove(
                emitterId,
                definitionId,
                emitterRevision));
    }

    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    private EmitterStateSnapshot captureEmitterState(
        E emitter,
        EmitterGroupRef<?> groupRef) {

        EmitterDefinition definition =
            sourceDefinitionSelector.getDefinition(
                groupRef.getDefinitionId());

        if (definition == null) {
            throw new IllegalStateException(
                "Unknown emitter definition: "
                    + groupRef.getDefinitionId());
        }

        return definition
            .getStateAdapter()
            .capture(
                groupRef.getGroupKey(),
                emitter);
    }

    private void queueInfluencerUpsert(
        PropagationInfluencer influencer) {

        if (!trackEmitterChanges) {
            return;
        }

        InfluencerRuntimeState runtime =
            requireInfluencerRuntimeState(
                influencer);

        InfluencerStateSnapshot snapshot =
            captureInfluencerState(
                influencer,
                runtime.getDefinitionId());

        pendingInfluencerChanges.put(
            runtime.getInfluencerId(),
            InfluencerRuntimeChange.upsert(
                runtime.getInfluencerId(),
                runtime.getDefinitionId(),
                snapshot));
    }

    private void queueEmitterUpsert(E emitter) {
        if (!trackEmitterChanges) {
            return;
        }

        EmitterRuntimeState runtimeState =
            requireEmitterRuntimeState(emitter);

        EmitterGroupRef<?> groupRef =
            getEmitterGroupRef(emitter);

        if (groupRef == null) {
            throw new IllegalStateException(
                "Emitter has no group binding");
        }

        EmitterStateSnapshot snapshot =
            captureEmitterState(
                emitter,
                groupRef);

        pendingEmitterChanges.put(
            runtimeState.getEmitterId(),
            EmitterRuntimeChange.upsert(
                runtimeState.getEmitterId(),
                groupRef.getDefinitionId(),
                runtimeState.getEmitterRevision(),
                snapshot));
    }

    private long allocateInfluencerId() {
        long id = nextInfluencerId++;

        if (id <= 0L) {
            throw new IllegalStateException(
                "Influencer runtime id exhausted");
        }

        return id;
    }

    private void registerInfluencerRuntime(
        PropagationInfluencer influencer,
        long influencerId,
        String definitionId) {

        InfluencerRuntimeState state =
            new InfluencerRuntimeState(
                influencerId,
                definitionId);

        if (influencerRuntimeStates.put(
            influencer,
            state) != null) {

            throw new IllegalStateException(
                "Influencer already has runtime state");
        }

        if (influencersByRuntimeId.put(
            influencerId,
            influencer) != null) {

            influencerRuntimeStates.remove(
                influencer);

            throw new IllegalStateException(
                "Duplicate influencer runtime id: "
                    + influencerId);
        }
    }

    private void unregisterInfluencerRuntime(
        PropagationInfluencer influencer) {

        InfluencerRuntimeState state =
            influencerRuntimeStates.remove(
                influencer);

        if (state != null) {
            influencersByRuntimeId.remove(
                state.getInfluencerId());
        }
    }

    private InfluencerRuntimeState
    requireInfluencerRuntimeState(
        PropagationInfluencer influencer) {

        InfluencerRuntimeState state =
            influencerRuntimeStates.get(
                influencer);

        if (state == null) {
            throw new IllegalStateException(
                "Influencer has no runtime state");
        }

        return state;
    }

    private long allocateEmitterId() {
        if (nextEmitterId <= 0L
            || nextEmitterId == Long.MAX_VALUE) {

            throw new IllegalStateException(
                "Emitter id space exhausted");
        }

        return nextEmitterId++;
    }

    private void registerEmitterRuntime(
        E emitter,
        long emitterId,
        long emitterRevision) {

        if (emitterRuntimeStates.containsKey(emitter)) {
            throw new IllegalStateException(
                "Emitter already has runtime identity");
        }

        E existing = emittersByRuntimeId.get(emitterId);

        if (existing != null) {
            throw new IllegalStateException(
                "Emitter id already occupied: "
                    + emitterId);
        }

        EmitterRuntimeState state =
            new EmitterRuntimeState(
                emitterId,
                emitterRevision,
                emitter.getPropagationRevision());

        emitterRuntimeStates.put(emitter, state);
        emittersByRuntimeId.put(emitterId, emitter);
    }

    private void unregisterEmitterRuntime(E emitter) {
        EmitterRuntimeState state =
            emitterRuntimeStates.remove(emitter);

        if (state == null) {
            return;
        }

        E existing =
            emittersByRuntimeId.get(
                state.getEmitterId());

        if (existing == emitter) {
            emittersByRuntimeId.remove(
                state.getEmitterId());
        }
    }

    protected final long getEmitterId(E emitter) {
        return requireEmitterRuntimeState(emitter)
            .getEmitterId();
    }

    protected final long getEmitterRevision(E emitter) {
        return requireEmitterRuntimeState(emitter)
            .getEmitterRevision();
    }

    protected final E getEmitterByRuntimeId(
        long emitterId) {

        return emittersByRuntimeId.get(emitterId);
    }

    private EmitterRuntimeState requireEmitterRuntimeState(
        E emitter) {

        EmitterRuntimeState state =
            emitterRuntimeStates.get(emitter);

        if (state == null) {
            throw new IllegalStateException(
                "Emitter has no runtime state");
        }

        return state;
    }

    public final boolean isAuthoritative() {
        return runtimeMode
            == PropagationRuntimeMode.AUTHORITATIVE;
    }

    public final boolean isReplica() {
        return runtimeMode
            == PropagationRuntimeMode.REPLICA;
    }

    protected final void requireAuthoritative(
        String operation) {

        if (!isAuthoritative()) {
            throw new IllegalStateException(
                operation
                    + " is not allowed on a replica manager");
        }
    }

    protected final void requireReplica(String operation) {
        if (!isReplica()) {
            throw new IllegalStateException(
                operation
                    + " is only allowed on a replica manager");
        }
    }

    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    private void applyReplicaInfluencerUpsertNow(
        long influencerId,
        String definitionId,
        InfluencerStateSnapshot snapshot) {

        requireReplica(
            "applyReplicaInfluencerUpsertNow");

        if (influencerId <= 0L) {
            throw new IllegalArgumentException(
                "Invalid influencer id: "
                    + influencerId);
        }

        InfluencerDefinition definition =
            influencerDefinitionSelector
                .getDefinition(definitionId);

        if (definition == null) {
            throw new IllegalArgumentException(
                "Unknown influencer definition: "
                    + definitionId);
        }

        PropagationInfluencer existing =
            influencersByRuntimeId.get(
                influencerId);

        InfluencerStateAdapter adapter =
            definition.getStateAdapter();

        if (existing == null) {
            PropagationInfluencer influencer =
                (PropagationInfluencer)
                    adapter.createReplica(
                        dimension,
                        snapshot);

            if (!definition
                .getInfluencerType()
                .isInstance(influencer)) {

                throw new IllegalStateException(
                    "Replica influencer type mismatch: "
                        + definitionId);
            }

            registerInfluencerInfrastructure(
                influencer);

            registerInfluencerRuntime(
                influencer,
                influencerId,
                definitionId);

            return;
        }

        InfluencerRuntimeState runtime =
            requireInfluencerRuntimeState(
                existing);

        if (!definitionId.equals(
            runtime.getDefinitionId())) {

            throw new IllegalStateException(
                "Influencer definition changed for id "
                    + influencerId);
        }

        adapter.applyReplica(
            existing,
            snapshot);
    }

    private boolean applyReplicaUpsertNow(
        long emitterId,
        String definitionId,
        EmitterStateSnapshot snapshot) {

        requireReplica(
            "applyReplicaUpsert");

        if (emitterId <= 0L) {
            throw new IllegalArgumentException(
                "Invalid emitter id: "
                    + emitterId);
        }

        if (definitionId == null
            || definitionId.isEmpty()) {

            throw new IllegalArgumentException(
                "Invalid emitter definition id");
        }

        if (snapshot == null) {
            throw new IllegalArgumentException(
                "Emitter snapshot is null");
        }

        EmitterDefinition<?, ? extends E, ?>
            definition =
            sourceDefinitionSelector.getDefinition(
                definitionId);

        if (definition == null) {
            throw new IllegalStateException(
                "Unknown emitter definition: "
                    + definitionId);
        }

        E emitter =
            emittersByRuntimeId.get(emitterId);

        if (emitter == null) {
            registerReplicaEmitter(
                emitterId,
                definitionId,
                definition,
                snapshot);

            return true;
        }

        EmitterGroupRef<?> groupRef =
            getEmitterGroupRef(emitter);

        if (groupRef == null) {
            throw new IllegalStateException(
                "Replica emitter has no group binding");
        }

        if (!definitionId.equals(
            groupRef.getDefinitionId())) {

            throw new IllegalStateException(
                "Emitter definition changed for runtime id "
                    + emitterId);
        }

        applyReplicaState(
            definition,
            groupRef,
            emitter,
            snapshot);

        if (spatialIndex.ensureCoverage(emitter)) {
            updateInfluencerCandidates(emitter);
        }

        return true;
    }

    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    private void applyReplicaState(
        EmitterDefinition definition,
        EmitterGroupRef<?> currentGroupRef,
        E emitter,
        EmitterStateSnapshot snapshot) {

        EmitterStateAdapter adapter =
            definition.getStateAdapter();

        Object incomingGroupKey =
            adapter.getGroupKey(snapshot);

        if (incomingGroupKey == null) {
            throw new IllegalStateException(
                "Replica group key is null: "
                    + definition.getId());
        }

        Object currentGroupKey =
            currentGroupRef.getGroupKey();

        if (!currentGroupKey.equals(
            incomingGroupKey)) {

            throw new IllegalStateException(
                "Emitter group changed for runtime id");
        }

        adapter.applyReplica(
            emitter,
            snapshot);
    }

    private void applyReplicaInfluencerRemoveNow(
        long influencerId) {

        requireReplica(
            "applyReplicaInfluencerRemoveNow");

        if (influencerId <= 0L) {
            throw new IllegalArgumentException(
                "Invalid influencer id: "
                    + influencerId);
        }

        PropagationInfluencer influencer =
            influencersByRuntimeId.get(
                influencerId);

        if (influencer == null) {
            return;
        }

        unregisterInfluencerInfrastructure(
            influencer);

        unregisterInfluencerRuntime(
            influencer);
    }

    private boolean applyReplicaRemoveNow(
        long emitterId) {

        requireReplica(
            "applyReplicaRemove");

        if (emitterId <= 0L) {
            throw new IllegalArgumentException(
                "Invalid emitter id: "
                    + emitterId);
        }

        E emitter =
            emittersByRuntimeId.get(emitterId);

        if (emitter == null) {
            return false;
        }

        EmitterGroupRef<?> groupRef =
            getEmitterGroupRef(emitter);

        if (groupRef == null) {
            throw new IllegalStateException(
                "Replica emitter has no group binding");
        }

        unregisterEmitterInfrastructure(
            emitter);

        try {
            onEmitterUnregistered(
                groupRef,
                emitter);
        } finally {
            unregisterEmitterRuntime(
                emitter);
        }

        return true;
    }

    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    private void registerReplicaEmitter(
        long emitterId,
        String definitionId,
        EmitterDefinition definition,
        EmitterStateSnapshot snapshot) {

        EmitterStateAdapter adapter =
            definition.getStateAdapter();

        Object groupKey =
            adapter.getGroupKey(snapshot);

        if (groupKey == null) {
            throw new IllegalStateException(
                "Replica group key is null: "
                    + definitionId);
        }

        EmitterGroupRef groupRef =
            new EmitterGroupRef(
                definitionId,
                groupKey);

        if (emitterGroups.containsKey(groupRef)) {
            throw new IllegalStateException(
                "Replica emitter group already occupied: "
                    + definitionId);
        }

        E emitter =
            (E) adapter.createReplica(
                dimension,
                groupKey,
                snapshot);

        if (emitter == null) {
            throw new IllegalStateException(
                "Emitter adapter returned null replica: "
                    + definitionId);
        }

        validateEmitterRegistration(
            groupRef,
            emitter);

        boolean infrastructureRegistered = false;
        boolean groupBound = false;
        boolean runtimeRegistered = false;

        try {
            registerEmitterInfrastructure(emitter);
            infrastructureRegistered = true;

            emitterGroups.put(
                groupRef,
                emitter);

            emitterGroupRefs.put(
                emitter,
                groupRef);

            groupBound = true;

            /*
             * Replica does not use emitterRevision
             * for ordering.
             */
            registerEmitterRuntime(
                emitter,
                emitterId,
                0L);

            runtimeRegistered = true;

            onEmitterRegistered(
                groupRef,
                emitter);

        } catch (RuntimeException e) {

            if (runtimeRegistered) {
                unregisterEmitterRuntime(emitter);
            }

            if (groupBound) {
                emitterGroups.remove(groupRef);
                emitterGroupRefs.remove(emitter);
            }

            if (infrastructureRegistered) {
                unregisterEmitterInfrastructure(
                    emitter);
            }

            onEmitterRegistrationFailed(
                groupRef,
                emitter);

            throw e;
        }
    }

    private void registerInfluencerInfrastructure(
        PropagationInfluencer influencer) {

        influencers.add(influencer);
        influencerSpatialIndex.add(
            influencer);

        Vec3 position =
            influencer.getPosition();

        double range =
            influencer.getRange();

        double coverageRange =
            influencerSpatialIndex
                .getCoverageRange(range);

        for (E emitter :
            spatialIndex.get(
                position,
                coverageRange)) {

            if (emitter.acceptsInfluencer(
                influencer)) {

                emitter.addInfluencer(
                    influencer);
            }
        }
    }

    protected final void registerEmitterInfrastructure(E emitter) {
        validateEmitterBasics(emitter);

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

    private void unregisterInfluencerInfrastructure(
        PropagationInfluencer influencer) {

        Vec3 position =
            influencer.getPosition();

        double range =
            influencer.getRange();

        if (position != null
            && Double.isFinite(position.xCoord)
            && Double.isFinite(position.yCoord)
            && Double.isFinite(position.zCoord)
            && Double.isFinite(range)
            && range >= 0.0D) {

            double coverageRange =
                influencerSpatialIndex
                    .getCoverageRange(range);

            for (E emitter :
                spatialIndex.get(
                    position,
                    coverageRange)) {

                emitter.removeInfluencer(
                    influencer);
            }
        }

        influencerSpatialIndex.remove(
            influencer);

        influencers.remove(
            influencer);
    }

    protected final void unregisterEmitterInfrastructure(E emitter) {
        emitterIdentities.remove(emitter);
        removeEmitterIdentity(emitter);
        spatialIndex.remove(emitter);
        removeEmitterBindings(emitter);
    }

    @Override
    public final void registerSource(
        PropagationSource source) {

        requireAuthoritative("registerSource");

        if (source == null) {
            throw new IllegalArgumentException(
                "Source is null");
        }

        if (source.getDimension() != dimension) {
            throw new IllegalArgumentException(
                "Source dimension mismatch: " + source.getDimension() + " != " + dimension);
        }

        Vec3 position = source.getPosition();
        validatePosition(position, "source");

        EmitterDefinition<?, ? extends E, ?> definition =
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

        requireAuthoritative("registerGroupedEmitter");

        if (groupRef == null) {
            throw new IllegalArgumentException("Emitter group ref is null");
        }

        if (emitter == null) {
            throw new IllegalArgumentException("Emitter is null");
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

        if (emitterGroupRefs.containsKey(emitter)) {
            throw new IllegalStateException(
                "Emitter already belongs to a group");
        }

        validateEmitterRegistration(groupRef, emitter);

        boolean infrastructureRegistered = false;
        boolean groupBound = false;
        boolean runtimeRegistered = false;

        try {
            registerEmitterInfrastructure(emitter);
            infrastructureRegistered = true;

            emitterGroups.put(groupRef, emitter);
            emitterGroupRefs.put(emitter, groupRef);
            groupBound = true;

            registerEmitterRuntime(
                emitter,
                allocateEmitterId(),
                1L);

            runtimeRegistered = true;

            onEmitterRegistered(groupRef, emitter);

            queueEmitterUpsert(emitter);

        } catch (RuntimeException e) {

            if (runtimeRegistered) {
                unregisterEmitterRuntime(emitter);
            }

            if (groupBound) {
                emitterGroups.remove(groupRef);
                emitterGroupRefs.remove(emitter);
            }

            if (infrastructureRegistered) {
                unregisterEmitterInfrastructure(emitter);
            }

            onEmitterRegistrationFailed(
                groupRef,
                emitter);

            throw e;
        }
    }

    @Override
    public final void tick() {
        if (isAuthoritative()) {
            tickEmitters();
            tickInfluencers();
            onAuthoritativeTick();
            return;
        }

        replicaUpdateQueue.drain();

        onReplicaTick();
    }

    protected void onAuthoritativeTick() {}

    protected void onReplicaTick() {}

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

            boolean stateChanged =
                emitter.consumeStateChanged();

            EmitterRuntimeState runtimeState =
                requireEmitterRuntimeState(emitter);

            if (runtimeState.observePropagationRevision(
                emitter.getPropagationRevision())) {

                stateChanged = true;
            }

            if (stateChanged) {
                runtimeState.incrementEmitterRevision();

                onEmitterStateChanged(emitter);

                queueEmitterUpsert(emitter);
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

        EmitterGroupRef<?> groupRef =
            getEmitterGroupRef(emitter);

        if (groupRef == null) {
            return;
        }

        EmitterRuntimeState runtimeState =
            requireEmitterRuntimeState(emitter);

        long emitterId =
            runtimeState.getEmitterId();

        String definitionId =
            groupRef.getDefinitionId();

        runtimeState.incrementEmitterRevision();

        long removeRevision =
            runtimeState.getEmitterRevision();

        unregisterEmitterInfrastructure(emitter);

        try {
            onEmitterUnregistered(
                groupRef,
                emitter);
        } finally {
            queueEmitterRemove(
                emitterId,
                definitionId,
                removeRevision);

            unregisterEmitterRuntime(emitter);
        }
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

    private <K, T extends E, S extends EmitterStateSnapshot> void registerSourceWithDefinition(
        PropagationSource source,
        Vec3 position,
        EmitterDefinition<K, T, S> definition) {

        K groupKey =
            definition.getGrouping()
                .getGroupKey(source, position);

        if (groupKey == null) {
            throw new IllegalStateException(
                "Emitter grouping returned null: "
                    + definition.getId());
        }

        EmitterGroupRef<K> groupRef =
            new EmitterGroupRef<>(
                definition.getId(),
                groupKey);

        E emitter = emitterGroups.get(groupRef);

        if (emitter == null) {
            T created =
                definition.getFactory()
                    .create(
                        dimension,
                        groupKey,
                        source,
                        position);

            if (created == null) {
                throw new IllegalStateException(
                    "Emitter factory returned null: "
                        + definition.getId());
            }

            registerGroupedEmitter(
                groupRef,
                created);

            emitter = created;
        }

        emitter.addSource(source);
    }

    public final void enqueueReplicaDelta(
        List<EmitterRuntimeChange> emitterRecords,
        List<InfluencerRuntimeChange> influencerRecords) {

        requireReplica(
            "enqueueReplicaDelta");

        final List<EmitterRuntimeChange>
            emitterCopy =
            new ArrayList<>(
                emitterRecords);

        final List<InfluencerRuntimeChange>
            influencerCopy =
            new ArrayList<>(
                influencerRecords);

        replicaUpdateQueue.enqueue(
            new Runnable() {

                @Override
                public void run() {
                    applyReplicaDeltaNow(
                        emitterCopy,
                        influencerCopy);
                }
            });
    }

    private void applyReplicaDeltaNow(
        List<EmitterRuntimeChange> emitterRecords,
        List<InfluencerRuntimeChange> influencerRecords) {

        requireReplica(
            "applyReplicaDeltaNow");

        for (EmitterRuntimeChange change
            : emitterRecords) {

            switch (change.getOperation()) {
                case UPSERT:
                    applyReplicaUpsertNow(
                        change.getEmitterId(),
                        change.getDefinitionId(),
                        change.getState());
                    break;

                case REMOVE:
                    applyReplicaRemoveNow(
                        change.getEmitterId());
                    break;

                default:
                    throw new IllegalStateException(
                        "Unknown emitter operation");
            }
        }

        for (InfluencerRuntimeChange change
            : influencerRecords) {

            switch (change.getOperation()) {
                case UPSERT:
                    applyReplicaInfluencerUpsertNow(
                        change.getInfluencerId(),
                        change.getDefinitionId(),
                        change.getState());
                    break;

                case REMOVE:
                    applyReplicaInfluencerRemoveNow(
                        change.getInfluencerId());
                    break;

                default:
                    throw new IllegalStateException(
                        "Unknown influencer operation");
            }
        }
    }

    public final void enqueueReplicaUpsert(
        final long emitterId,
        final String definitionId,
        final EmitterStateSnapshot snapshot) {

        requireReplica(
            "enqueueReplicaUpsert");

        replicaUpdateQueue.enqueue(
            () -> applyReplicaUpsertNow(
                emitterId,
                definitionId,
                snapshot));
    }

    public final void enqueueReplicaRemove(
        final long emitterId) {

        requireReplica(
            "enqueueReplicaRemove");

        replicaUpdateQueue.enqueue(
            () -> applyReplicaRemoveNow(
                emitterId));
    }

    public final void enqueueReplicaFullSnapshot(
        List<EmitterRuntimeChange> emitterRecords,
        List<InfluencerRuntimeChange> influencerRecords) {

        requireReplica(
            "enqueueReplicaFullSnapshot");

        final List<EmitterRuntimeChange>
            emitterCopy =
            new ArrayList<>(
                emitterRecords);

        final List<InfluencerRuntimeChange>
            influencerCopy =
            new ArrayList<>(
                influencerRecords);

        replicaUpdateQueue.enqueue(
            new Runnable() {

                @Override
                public void run() {
                    applyReplicaFullSnapshotNow(
                        emitterCopy,
                        influencerCopy);
                }
            });
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

    private void validateEmitterBasics(E emitter) {
        if (emitter == null) {
            throw new IllegalArgumentException(
                "Emitter is null");
        }

        if (emitter.getDimension() != dimension) {
            throw new IllegalArgumentException(
                "Emitter dimension mismatch: "
                    + emitter.getDimension()
                    + " != "
                    + dimension);
        }

        Vec3 position = emitter.getPosition();

        validatePosition(
            position,
            "emitter");

        double range =
            emitter.getPropagationRange();

        if (!Double.isFinite(range)
            || range < 0.0D) {

            throw new IllegalArgumentException(
                "Invalid emitter propagation range: "
                    + range);
        }
    }

    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    private ReplicaInfluencerStage
    buildReplicaInfluencerStage(
        List<InfluencerRuntimeChange> records) {

        ReplicaInfluencerStage stage =
            new ReplicaInfluencerStage();

        for (InfluencerRuntimeChange record
            : records) {

            if (record == null) {
                throw new IllegalArgumentException(
                    "Null influencer record");
            }

            if (record.getOperation()
                != InfluencerRuntimeChange.Operation.UPSERT) {

                throw new IllegalArgumentException(
                    "Full influencer snapshot "
                        + "may contain only UPSERT");
            }

            long influencerId =
                record.getInfluencerId();

            if (stage.byRuntimeId.containsKey(
                influencerId)) {

                throw new IllegalArgumentException(
                    "Duplicate influencer id: "
                        + influencerId);
            }

            String definitionId =
                record.getDefinitionId();

            InfluencerDefinition definition =
                influencerDefinitionSelector
                    .getDefinition(definitionId);

            if (definition == null) {
                throw new IllegalArgumentException(
                    "Unknown influencer definition: "
                        + definitionId);
            }

            InfluencerStateSnapshot snapshot =
                record.getState();

            if (snapshot == null) {
                throw new IllegalArgumentException(
                    "Influencer snapshot is null");
            }

            InfluencerStateAdapter adapter =
                definition.getStateAdapter();

            PropagationInfluencer influencer =
                (PropagationInfluencer)
                    adapter.createReplica(
                        dimension,
                        snapshot);

            if (influencer == null) {
                throw new IllegalStateException(
                    "Influencer adapter returned null");
            }

            if (!definition
                .getInfluencerType()
                .isInstance(influencer)) {

                throw new IllegalStateException(
                    "Replica influencer type mismatch: "
                        + definitionId);
            }

            if (!definitionId.equals(
                influencer
                    .getInfluencerDefinitionId())) {

                throw new IllegalStateException(
                    "Replica influencer definition mismatch");
            }

            Vec3 position =
                influencer.getPosition();

            validatePosition(
                position,
                "replica influencer");

            double range =
                influencer.getRange();

            if (!Double.isFinite(range)
                || range < 0.0D) {

                throw new IllegalArgumentException(
                    "Invalid replica influencer range: "
                        + range);
            }

            stage.influencers.add(
                influencer);

            stage.spatialIndex.add(
                influencer);

            stage.runtimeStates.put(
                influencer,
                new InfluencerRuntimeState(
                    influencerId,
                    definitionId));

            stage.byRuntimeId.put(
                influencerId,
                influencer);
        }

        return stage;
    }

    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    private ReplicaEmitterStage<E> buildReplicaEmitterStage(List<EmitterRuntimeChange> records) {

        ReplicaEmitterStage<E> stage =
            new ReplicaEmitterStage<>();

        for (int i = 0; i < records.size(); i++) {
            EmitterRuntimeChange record =
                records.get(i);

            if (record == null) {
                throw new IllegalArgumentException(
                    "Null emitter record at index " + i);
            }

            if (record.getOperation()
                != EmitterRuntimeChange.Operation.UPSERT) {

                throw new IllegalArgumentException(
                    "Full snapshot contains non-UPSERT record at index "
                        + i);
            }

            long emitterId =
                record.getEmitterId();

            if (stage.emittersById.containsKey(
                emitterId)) {

                throw new IllegalArgumentException(
                    "Duplicate emitter id in full snapshot: "
                        + emitterId);
            }

            String definitionId =
                record.getDefinitionId();

            EmitterDefinition definition =
                sourceDefinitionSelector.getDefinition(
                    definitionId);

            if (definition == null) {
                throw new IllegalArgumentException(
                    "Unknown emitter definition: "
                        + definitionId);
            }

            EmitterStateSnapshot snapshot =
                record.getState();

            if (snapshot == null) {
                throw new IllegalArgumentException(
                    "Missing state for emitter "
                        + emitterId);
            }

            try {
                EmitterStateAdapter adapter =
                    definition.getStateAdapter();

                Object groupKey =
                    adapter.getGroupKey(snapshot);

                if (groupKey == null) {
                    throw new IllegalStateException(
                        "Emitter group key is null");
                }

                EmitterGroupRef groupRef =
                    new EmitterGroupRef(
                        definitionId,
                        groupKey);

                if (stage.emitterGroups.containsKey(
                    groupRef)) {

                    throw new IllegalArgumentException(
                        "Duplicate emitter group: "
                            + definitionId);
                }

                E emitter =
                    (E) adapter.createReplica(
                        dimension,
                        groupKey,
                        snapshot);

                validateEmitterBasics(emitter);

                if (stage.emitterGroupRefs.containsKey(
                    emitter)) {

                    throw new IllegalStateException(
                        "State adapter reused emitter instance");
                }

                stage.spatialIndex.add(emitter);

                stage.emitters.add(emitter);

                stage.emitterGroups.put(
                    groupRef,
                    emitter);

                stage.emitterGroupRefs.put(
                    emitter,
                    groupRef);

                stage.emittersById.put(
                    emitterId,
                    emitter);

                stage.runtimeStates.put(
                    emitter,
                    new EmitterRuntimeState(
                        emitterId,
                        0L,
                        emitter.getPropagationRevision()));

            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                    "Invalid full snapshot emitter at index "
                        + i
                        + " (id="
                        + emitterId
                        + ", definition="
                        + definitionId
                        + ")",
                    e);
            }
        }

        return stage;
    }

    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    private InfluencerStateSnapshot captureInfluencerState(PropagationInfluencer influencer, String definitionId) {

        InfluencerDefinition definition =
            influencerDefinitionSelector
                .getDefinition(definitionId);

        if (definition == null) {
            throw new IllegalStateException(
                "Unknown influencer definition: "
                    + definitionId);
        }

        if (!definition
            .getInfluencerType()
            .isInstance(influencer)) {

            throw new IllegalStateException(
                "Influencer type mismatch for "
                    + definitionId);
        }

        return ((InfluencerStateAdapter)
            definition.getStateAdapter())
            .capture(influencer);
    }

    public final List<InfluencerRuntimeChange> captureInfluencerFullSnapshot() {

        requireAuthoritative(
            "captureInfluencerFullSnapshot");

        if (influencers.isEmpty()) {
            return Collections.emptyList();
        }

        List<InfluencerRuntimeChange> result =
            new ArrayList<>(
                influencers.size());

        for (PropagationInfluencer influencer
            : influencers) {

            InfluencerRuntimeState runtime =
                requireInfluencerRuntimeState(
                    influencer);

            result.add(
                InfluencerRuntimeChange.upsert(
                    runtime.getInfluencerId(),
                    runtime.getDefinitionId(),
                    captureInfluencerState(
                        influencer,
                        runtime.getDefinitionId())));
        }

        return result;
    }

    public final List<EmitterRuntimeChange> captureEmitterFullSnapshot() {

        requireAuthoritative(
            "captureEmitterFullSnapshot");

        if (emitters.isEmpty()) {
            return Collections.emptyList();
        }

        List<EmitterRuntimeChange> result =
            new ArrayList<>(
                emitters.size());

        for (E emitter : emitters) {
            EmitterRuntimeState runtimeState =
                requireEmitterRuntimeState(
                    emitter);

            EmitterGroupRef<?> groupRef =
                getEmitterGroupRef(emitter);

            if (groupRef == null) {
                throw new IllegalStateException(
                    "Emitter has no group binding");
            }

            EmitterStateSnapshot state =
                captureEmitterState(
                    emitter,
                    groupRef);

            result.add(
                EmitterRuntimeChange.upsert(
                    runtimeState.getEmitterId(),
                    groupRef.getDefinitionId(),
                    runtimeState.getEmitterRevision(),
                    state));
        }

        return Collections.unmodifiableList(
            result);
    }

    private void applyReplicaFullSnapshotNow(
        List<EmitterRuntimeChange> emitterRecords,
        List<InfluencerRuntimeChange> influencerRecords) {

        requireReplica(
            "applyReplicaFullSnapshotNow");

        /*
         * Validate/build EVERYTHING before touching live state.
         */
        ReplicaEmitterStage<E> emitterStage =
            buildReplicaEmitterStage(
                emitterRecords);

        ReplicaInfluencerStage influencerStage =
            buildReplicaInfluencerStage(
                influencerRecords);

        Map<EmitterGroupRef<?>, E>
            oldEmitterGroups =
            emitterGroups;

        Map<E, EmitterGroupRef<?>>
            oldEmitterGroupRefs =
            emitterGroupRefs;

        List<E> oldEmitters =
            emitters;

        PropagationSpatialIndex<E>
            oldSpatialIndex =
            spatialIndex;

        Map<E, EmitterRuntimeState>
            oldEmitterRuntimeStates =
            emitterRuntimeStates;

        Map<Long, E>
            oldEmittersByRuntimeId =
            emittersByRuntimeId;

        List<PropagationInfluencer>
            oldInfluencers =
            influencers;

        PropagationInfluencerSpatialIndex
            oldInfluencerSpatialIndex =
            influencerSpatialIndex;

        Map<
            PropagationInfluencer,
            InfluencerRuntimeState>
            oldInfluencerRuntimeStates =
            influencerRuntimeStates;

        Map<Long, PropagationInfluencer>
            oldInfluencersByRuntimeId =
            influencersByRuntimeId;

        try {
            emitterGroups =
                emitterStage.emitterGroups;

            emitterGroupRefs =
                emitterStage.emitterGroupRefs;

            emitters =
                emitterStage.emitters;

            spatialIndex =
                emitterStage.spatialIndex;

            emitterRuntimeStates =
                emitterStage.runtimeStates;

            emittersByRuntimeId =
                emitterStage.emittersById;

            influencers =
                influencerStage.influencers;

            influencerSpatialIndex =
                influencerStage.spatialIndex;

            influencerRuntimeStates =
                influencerStage.runtimeStates;

            influencersByRuntimeId =
                influencerStage.byRuntimeId;

            emitterUpdateCursor = 0;
            emitterUpdateAccumulator = 0;

            influencerUpdateCursor = 0;
            influencerUpdateAccumulator = 0;

            /*
             * Detached emitter stage deliberately contained
             * no influencer bindings.
             * Build them now from the new influencer index.
             */
            for (E emitter : emitters) {
                updateInfluencerCandidates(
                    emitter);
            }

            onReplicaEmitterSnapshotReplaced();

        } catch (RuntimeException e) {
            emitterGroups =
                oldEmitterGroups;

            emitterGroupRefs =
                oldEmitterGroupRefs;

            emitters =
                oldEmitters;

            spatialIndex =
                oldSpatialIndex;

            emitterRuntimeStates =
                oldEmitterRuntimeStates;

            emittersByRuntimeId =
                oldEmittersByRuntimeId;

            influencers =
                oldInfluencers;

            influencerSpatialIndex =
                oldInfluencerSpatialIndex;

            influencerRuntimeStates =
                oldInfluencerRuntimeStates;

            influencersByRuntimeId =
                oldInfluencersByRuntimeId;

            emitterUpdateCursor = 0;
            influencerUpdateCursor = 0;

            /*
             * Rebuild model-specific replica registries
             * for restored emitter state.
             */
            onReplicaEmitterSnapshotReplaced();

            throw e;
        }
    }

    protected void onReplicaEmitterSnapshotReplaced() {}

    private static final class ReplicaInfluencerStage {

        private final List<PropagationInfluencer>
            influencers =
            new ArrayList<>();

        private final PropagationInfluencerSpatialIndex
            spatialIndex =
            new PropagationInfluencerSpatialIndex();

        private final Map<
            PropagationInfluencer,
            InfluencerRuntimeState>
            runtimeStates =
            new IdentityHashMap<>();

        private final Map<
            Long,
            PropagationInfluencer>
            byRuntimeId =
            new HashMap<>();
    }

    private static final class ReplicaEmitterStage<
        E extends PropagationEmitter> {

        private final Map<EmitterGroupRef<?>, E>
            emitterGroups =
            new HashMap<>();

        private final Map<E, EmitterGroupRef<?>>
            emitterGroupRefs =
            new IdentityHashMap<>();

        private final List<E> emitters =
            new ArrayList<>();

        private final PropagationSpatialIndex<E>
            spatialIndex =
            new PropagationSpatialIndex<>();

        private final Map<E, EmitterRuntimeState>
            runtimeStates =
            new IdentityHashMap<>();

        private final Map<Long, E> emittersById =
            new HashMap<>();
    }
}
