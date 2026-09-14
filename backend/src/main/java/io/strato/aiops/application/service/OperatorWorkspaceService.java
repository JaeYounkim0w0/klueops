package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.OperatorWorkspaceRepositoryPort;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentActivity;
import io.strato.aiops.domain.operations.OperationsModels.IncidentEvidence;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.operations.OperationsModels.ResourceChange;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.IncidentCollaboration;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.IncidentLink;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.LinkedIncident;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.ManagedRunbook;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.ResourceChangeItem;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.ResourceContext;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.ResourceFieldDiff;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.ResourceRelation;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.RunbookVersion;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.SearchResult;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class OperatorWorkspaceService {

    private static final Set<String> MUTATING_COMMANDS = Set.of(
            " apply ", " delete ", " patch ", " replace ", " edit ", " scale ", " rollout restart ", " drain ", " cordon ");

    private final OperatorWorkspaceRepositoryPort workspaceRepository;
    private final OperationsRepositoryPort operationsRepository;
    private final ClusterRepositoryPort clusterRepository;
    private final AuditLogRepositoryPort auditRepository;
    private final IdentityAccessService accessService;
    private final ObjectMapper objectMapper;

    public OperatorWorkspaceService(OperatorWorkspaceRepositoryPort workspaceRepository,
                                    OperationsRepositoryPort operationsRepository,
                                    ClusterRepositoryPort clusterRepository,
                                    AuditLogRepositoryPort auditRepository,
                                    IdentityAccessService accessService,
                                    ObjectMapper objectMapper) {
        this.workspaceRepository = workspaceRepository;
        this.operationsRepository = operationsRepository;
        this.clusterRepository = clusterRepository;
        this.auditRepository = auditRepository;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    public List<SearchResult> search(String query, UUID clusterId, String namespace, Set<String> types,
                                     int limit, ResolvedAccess access) {
        String cleaned = clean(query, 200);
        if (cleaned == null || cleaned.length() < 2) {
            throw new IllegalArgumentException("Search query must contain at least 2 characters");
        }
        if (clusterId != null) require(access, Capability.CLUSTER_READ, clusterId, namespace);
        Set<String> normalizedTypes = types == null ? Set.of() : types.stream()
                .map(value -> value.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        return workspaceRepository.search(cleaned, Math.max(1, Math.min(100, limit))).stream()
                .filter(item -> clusterId == null || clusterId.equals(item.clusterId()))
                .filter(item -> namespace == null || namespace.isBlank() || namespace.equals(item.namespace()))
                .filter(item -> normalizedTypes.isEmpty() || normalizedTypes.contains(item.type()))
                .filter(item -> item.clusterId() == null || allowed(access, Capability.CLUSTER_READ, item.clusterId(), item.namespace()))
                .limit(limit)
                .toList();
    }

    public ResourceContext resourceContext(UUID clusterId, String namespace, String kind, String name,
                                           ResolvedAccess access) {
        require(access, Capability.CLUSTER_READ, clusterId, namespace);
        KubernetesResourceSnapshot current = workspaceRepository.findLatestResource(clusterId, namespace, kind, name)
                .orElseThrow(() -> new NoSuchElementException("Resource snapshot not found: " + kind + "/" + name));
        List<ResourceRelation> relations = workspaceRepository.findLatestNamespaceResources(clusterId, namespace, 1000)
                .stream().filter(candidate -> !candidate.id().equals(current.id()))
                .map(candidate -> relation(current, candidate))
                .filter(java.util.Objects::nonNull)
                .limit(30).toList();
        List<ResourceChangeItem> changes = operationsRepository.findResourceChanges(clusterId, namespace, 200).stream()
                .filter(change -> equalsIgnoreCase(change.resourceKind(), kind) && name.equals(change.resourceName()))
                .limit(20).map(this::changeItem).toList();
        List<LinkedIncident> incidents = workspaceRepository.findIncidentsForResource(clusterId, namespace, kind, name, 20)
                .stream().map(item -> new LinkedIncident(item.id(), item.severity(), item.state().name(), item.title(), item.lastDetectedAt()))
                .toList();
        List<KubernetesResourceSnapshot> history = workspaceRepository.findResourceHistory(clusterId, namespace, kind, name, 2);
        List<ResourceFieldDiff> fieldDiffs = history.size() < 2 ? List.of()
                : fieldDiffs(history.get(1).summaryJson(), history.get(0).summaryJson());
        return new ResourceContext(clusterId, namespace, current.resourceType(), current.resourceName(), current.status(),
                current.collectedAt(), relations, changes, fieldDiffs, incidents);
    }

    public IncidentCollaboration collaboration(UUID incidentId, ResolvedAccess access) {
        Incident incident = incident(incidentId);
        require(access, Capability.ANALYSIS_READ, incident.clusterId(), incident.namespace());
        return workspaceRepository.findCollaboration(incidentId);
    }

    @Transactional
    public IncidentCollaboration updateCollaboration(UUID incidentId, String assignee, List<String> tags,
                                                     Instant acknowledgeDueAt, Instant resolveDueAt,
                                                     String actor, String requestId, ResolvedAccess access) {
        Incident incident = incident(incidentId);
        require(access, Capability.OPERATION_EXECUTE, incident.clusterId(), incident.namespace());
        if (acknowledgeDueAt != null && resolveDueAt != null && resolveDueAt.isBefore(acknowledgeDueAt)) {
            throw new IllegalArgumentException("Resolve due time must be after acknowledge due time");
        }
        List<String> normalizedTags = normalizeTags(tags);
        Instant now = Instant.now();
        IncidentCollaboration saved = workspaceRepository.saveCollaboration(new IncidentCollaboration(incidentId,
                clean(assignee, 255), normalizedTags, acknowledgeDueAt, resolveDueAt, actor, now, List.of()));
        activity(incidentId, "COLLABORATION_UPDATED", "assignee=" + safe(saved.assignee()) + ", tags=" + normalizedTags, actor);
        audit("INCIDENT_COLLABORATION_UPDATED", "INCIDENT", incidentId.toString(), actor, requestId);
        return saved;
    }

    @Transactional
    public void addComment(UUID incidentId, String comment, String actor, String requestId, ResolvedAccess access) {
        Incident incident = incident(incidentId);
        require(access, Capability.OPERATION_EXECUTE, incident.clusterId(), incident.namespace());
        String cleaned = clean(comment, 2000);
        if (cleaned == null) throw new IllegalArgumentException("Comment is required");
        activity(incidentId, "COMMENT", cleaned, actor);
        audit("INCIDENT_COMMENT_ADDED", "INCIDENT", incidentId.toString(), actor, requestId);
    }

    @Transactional
    public Incident createManualIncident(UUID clusterId, String namespace, String resourceKind, String resourceName,
                                         String severity, String title, String summary, String nextAction,
                                         String actor, String requestId, ResolvedAccess access) {
        require(access, Capability.OPERATION_EXECUTE, clusterId, namespace);
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
        String cleanTitle = required(title, "Title", 500);
        Instant now = Instant.now();
        Incident value = new Incident(UUID.randomUUID(), hash("manual:" + clusterId + ":" + now + ":" + cleanTitle),
                clusterId, cluster.name(), clean(namespace, 255), clean(resourceKind, 100), clean(resourceName, 255),
                "MANUAL", normalizeSeverity(severity), IncidentState.OPEN, cleanTitle, clean(summary, 4000),
                clean(nextAction, 2000), 1, 0, null, now, now, actor);
        Incident saved = operationsRepository.saveIncident(value);
        activity(saved.id(), "MANUAL_CREATED", "Created by operator", actor);
        audit("INCIDENT_MANUAL_CREATED", "INCIDENT", saved.id().toString(), actor, requestId);
        return saved;
    }

    @Transactional
    public IncidentCollaboration linkIncident(UUID incidentId, UUID relatedIncidentId, String relationType,
                                              String actor, String requestId, ResolvedAccess access) {
        Incident source = incident(incidentId);
        Incident target = incident(relatedIncidentId);
        require(access, Capability.OPERATION_EXECUTE, source.clusterId(), source.namespace());
        require(access, Capability.ANALYSIS_READ, target.clusterId(), target.namespace());
        if (incidentId.equals(relatedIncidentId)) throw new IllegalArgumentException("Incident cannot link to itself");
        String relation = required(relationType, "Relation type", 30).toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        workspaceRepository.saveIncidentLink(new IncidentLink(incidentId, relatedIncidentId, relation,
                target.title(), target.state().name(), actor, now));
        workspaceRepository.saveIncidentLink(new IncidentLink(relatedIncidentId, incidentId, inverse(relation),
                source.title(), source.state().name(), actor, now));
        activity(incidentId, "INCIDENT_LINKED", relation + " " + relatedIncidentId, actor);
        audit("INCIDENT_LINKED", "INCIDENT", incidentId.toString(), actor, requestId);
        return workspaceRepository.findCollaboration(incidentId);
    }

    @Transactional
    public void unlinkIncident(UUID incidentId, UUID relatedIncidentId, String actor, String requestId, ResolvedAccess access) {
        Incident source = incident(incidentId);
        require(access, Capability.OPERATION_EXECUTE, source.clusterId(), source.namespace());
        workspaceRepository.deleteIncidentLink(incidentId, relatedIncidentId);
        activity(incidentId, "INCIDENT_UNLINKED", relatedIncidentId.toString(), actor);
        audit("INCIDENT_UNLINKED", "INCIDENT", incidentId.toString(), actor, requestId);
    }

    @Transactional
    public IncidentCollaboration merge(UUID targetId, List<UUID> sourceIds, String note, String actor,
                                       String requestId, ResolvedAccess access) {
        if (sourceIds == null || sourceIds.isEmpty()) throw new IllegalArgumentException("At least one source incident is required");
        Incident target = incident(targetId);
        require(access, Capability.OPERATION_EXECUTE, target.clusterId(), target.namespace());
        for (UUID sourceId : new LinkedHashSet<>(sourceIds)) {
            Incident source = incident(sourceId);
            if (!source.clusterId().equals(target.clusterId())) throw new IllegalArgumentException("Merged incidents must belong to the same cluster");
            linkIncident(targetId, sourceId, "MERGED_FROM", actor, requestId, access);
            Incident resolved = copyState(source, IncidentState.RESOLVED, actor);
            operationsRepository.saveIncident(resolved);
            activity(sourceId, "MERGED_INTO", targetId + " " + safe(clean(note, 1000)), actor);
        }
        activity(targetId, "INCIDENTS_MERGED", sourceIds.toString(), actor);
        audit("INCIDENTS_MERGED", "INCIDENT", targetId.toString(), actor, requestId);
        return workspaceRepository.findCollaboration(targetId);
    }

    @Transactional
    public Incident split(UUID incidentId, List<UUID> evidenceIds, String title, String severity, String actor,
                          String requestId, ResolvedAccess access) {
        Incident source = incident(incidentId);
        require(access, Capability.OPERATION_EXECUTE, source.clusterId(), source.namespace());
        Set<UUID> selected = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
        if (selected.isEmpty()) throw new IllegalArgumentException("At least one evidence item is required");
        List<IncidentEvidence> evidence = operationsRepository.findIncidentEvidence(incidentId).stream()
                .filter(item -> selected.contains(item.id())).toList();
        if (evidence.size() != selected.size()) throw new IllegalArgumentException("One or more evidence items do not belong to the incident");
        Incident created = createManualIncident(source.clusterId(), source.namespace(), source.resourceKind(), source.resourceName(),
                severity, title, "Split from " + source.title(), source.nextAction(), actor, requestId, access);
        for (IncidentEvidence item : evidence) {
            operationsRepository.saveIncidentEvidence(new IncidentEvidence(UUID.randomUUID(), created.id(),
                    hash(created.id() + ":" + item.evidenceKey()), item.evidenceType(), item.sourceRef(), item.summary(),
                    item.factual(), item.occurredAt()));
        }
        linkIncident(created.id(), incidentId, "SPLIT_FROM", actor, requestId, access);
        activity(incidentId, "INCIDENT_SPLIT", created.id().toString(), actor);
        audit("INCIDENT_SPLIT", "INCIDENT", incidentId.toString(), actor, requestId);
        return created;
    }

    public List<ManagedRunbook> runbookLibrary(ResolvedAccess access) {
        requireAny(access, Capability.ANALYSIS_READ);
        return workspaceRepository.findRunbookLibrary();
    }

    @Transactional
    public ManagedRunbook createRunbook(RunbookInput input, String actor, String requestId, ResolvedAccess access) {
        requireAny(access, Capability.OPERATION_EXECUTE);
        validate(input);
        Instant now = Instant.now();
        ManagedRunbook value = input.toDomain("CRB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                1, actor, now, now);
        ManagedRunbook saved = workspaceRepository.saveCustomRunbook(value, clean(input.changeNote(), 1000), actor);
        audit("RUNBOOK_CREATED", "RUNBOOK", saved.id(), actor, requestId);
        return saved;
    }

    @Transactional
    public ManagedRunbook updateRunbook(String id, RunbookInput input, String actor, String requestId, ResolvedAccess access) {
        requireAny(access, Capability.OPERATION_EXECUTE);
        validate(input);
        ManagedRunbook current = customRunbook(id);
        ManagedRunbook saved = workspaceRepository.saveCustomRunbook(input.toDomain(id, current.version() + 1,
                current.owner(), current.createdAt(), Instant.now()), clean(input.changeNote(), 1000), actor);
        audit("RUNBOOK_UPDATED", "RUNBOOK", id, actor, requestId);
        return saved;
    }

    @Transactional
    public ManagedRunbook duplicateRunbook(String id, String actor, String requestId, ResolvedAccess access) {
        requireAny(access, Capability.OPERATION_EXECUTE);
        ManagedRunbook source = workspaceRepository.findManagedRunbook(id)
                .orElseThrow(() -> new NoSuchElementException("Runbook not found: " + id));
        RunbookInput copy = new RunbookInput(source.signal(), source.category(), source.resourceKind(),
                source.title() + " copy", source.beginnerExplanation(), source.verificationCommand(), source.expectedResult(),
                source.safeAction(), source.validationCommand(), source.rollbackGuidance(), source.safetyLevel(), true,
                "Duplicated from " + id);
        return createRunbook(copy, actor, requestId, access);
    }

    @Transactional
    public ManagedRunbook setRunbookEnabled(String id, boolean enabled, String actor, String requestId, ResolvedAccess access) {
        ManagedRunbook current = customRunbook(id);
        RunbookInput input = new RunbookInput(current.signal(), current.category(), current.resourceKind(), current.title(),
                current.beginnerExplanation(), current.verificationCommand(), current.expectedResult(), current.safeAction(),
                current.validationCommand(), current.rollbackGuidance(), current.safetyLevel(), enabled,
                enabled ? "Enabled" : "Disabled");
        return updateRunbook(id, input, actor, requestId, access);
    }

    public List<RunbookVersion> runbookVersions(String id, ResolvedAccess access) {
        requireAny(access, Capability.ANALYSIS_READ);
        customRunbook(id);
        return workspaceRepository.findRunbookVersions(id);
    }

    @Transactional
    public ManagedRunbook restoreRunbook(String id, int version, String actor, String requestId, ResolvedAccess access) {
        requireAny(access, Capability.OPERATION_EXECUTE);
        ManagedRunbook current = customRunbook(id);
        ManagedRunbook snapshot = workspaceRepository.findRunbookVersion(id, version)
                .orElseThrow(() -> new NoSuchElementException("Runbook version not found: " + version));
        RunbookInput input = new RunbookInput(snapshot.signal(), snapshot.category(), snapshot.resourceKind(), snapshot.title(),
                snapshot.beginnerExplanation(), snapshot.verificationCommand(), snapshot.expectedResult(), snapshot.safeAction(),
                snapshot.validationCommand(), snapshot.rollbackGuidance(), snapshot.safetyLevel(), snapshot.enabled(),
                "Restored from version " + version);
        return updateRunbook(id, input, actor, requestId, access);
    }

    @Transactional
    public void deleteRunbook(String id, String actor, String requestId, ResolvedAccess access) {
        requireAny(access, Capability.OPERATION_EXECUTE);
        customRunbook(id);
        workspaceRepository.deleteCustomRunbook(id);
        audit("RUNBOOK_DELETED", "RUNBOOK", id, actor, requestId);
    }

    private ManagedRunbook customRunbook(String id) {
        ManagedRunbook value = workspaceRepository.findManagedRunbook(id)
                .orElseThrow(() -> new NoSuchElementException("Runbook not found: " + id));
        if (!"CUSTOM".equals(value.sourceType())) throw new IllegalArgumentException("System runbooks are read-only; duplicate before editing");
        return value;
    }

    private void validate(RunbookInput input) {
        required(input.signal(), "Signal", 100);
        required(input.category(), "Category", 100);
        required(input.title(), "Title", 255);
        required(input.beginnerExplanation(), "Beginner explanation", 2000);
        required(input.verificationCommand(), "Verification command", 2000);
        required(input.expectedResult(), "Expected result", 2000);
        required(input.validationCommand(), "Validation command", 2000);
        String safety = required(input.safetyLevel(), "Safety level", 30).toUpperCase(Locale.ROOT);
        if (!Set.of("READ_ONLY", "CHANGE_REQUIRES_REVIEW", "DESTRUCTIVE").contains(safety)) {
            throw new IllegalArgumentException("Unsupported safety level: " + safety);
        }
        if ("READ_ONLY".equals(safety) && (mutating(input.verificationCommand()) || mutating(input.validationCommand())
                || (input.safeAction() != null && mutating(input.safeAction())))) {
            throw new IllegalArgumentException("READ_ONLY runbook contains a mutating kubectl command");
        }
    }

    private boolean mutating(String command) {
        String value = " " + command.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + " ";
        return MUTATING_COMMANDS.stream().anyMatch(value::contains);
    }

    private ResourceRelation relation(KubernetesResourceSnapshot current, KubernetesResourceSnapshot candidate) {
        String currentJson = safe(current.rawJson()) + " " + safe(current.summaryJson());
        String candidateJson = safe(candidate.rawJson()) + " " + safe(candidate.summaryJson());
        String candidateToken = "\"" + candidate.resourceName() + "\"";
        String currentToken = "\"" + current.resourceName() + "\"";
        if (currentJson.contains(candidateToken)) {
            return new ResourceRelation("REFERENCES", candidate.resourceType(), candidate.resourceName(), candidate.status(),
                    true, current.resourceType() + " manifest references this resource");
        }
        if (candidateJson.contains(currentToken)) {
            return new ResourceRelation("REFERENCED_BY", candidate.resourceType(), candidate.resourceName(), candidate.status(),
                    true, candidate.resourceType() + " manifest references the selected resource");
        }
        return null;
    }

    private ResourceChangeItem changeItem(ResourceChange value) {
        return new ResourceChangeItem(value.id(), value.changeType(), value.previousStatus(), value.currentStatus(),
                value.summary(), value.detectedAt());
    }

    private List<ResourceFieldDiff> fieldDiffs(String previousJson, String currentJson) {
        try {
            JsonNode previous = objectMapper.readTree(previousJson == null ? "{}" : previousJson);
            JsonNode current = objectMapper.readTree(currentJson == null ? "{}" : currentJson);
            java.util.Map<String, String> before = new java.util.LinkedHashMap<>();
            java.util.Map<String, String> after = new java.util.LinkedHashMap<>();
            flattenSummary("", previous, before, 0);
            flattenSummary("", current, after, 0);
            LinkedHashSet<String> fields = new LinkedHashSet<>(before.keySet());
            fields.addAll(after.keySet());
            return fields.stream()
                    .filter(field -> !java.util.Objects.equals(before.get(field), after.get(field)))
                    .limit(30)
                    .map(field -> new ResourceFieldDiff(field, before.get(field), after.get(field)))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void flattenSummary(String prefix, JsonNode node, java.util.Map<String, String> target, int depth) {
        if (node == null || node.isNull()) return;
        if (node.isValueNode() || depth >= 3) {
            target.put(prefix.isBlank() ? "value" : prefix, abbreviate(node.toString(), 500));
            return;
        }
        if (node.isArray()) {
            target.put(prefix.isBlank() ? "items" : prefix, abbreviate(node.toString(), 500));
            return;
        }
        Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            flattenSummary(prefix.isBlank() ? field.getKey() : prefix + "." + field.getKey(),
                    field.getValue(), target, depth + 1);
        }
    }

    private static String abbreviate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) + "..." : value;
    }

    private Incident incident(UUID id) {
        return operationsRepository.findIncidentById(id)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + id));
    }

    private Incident copyState(Incident value, IncidentState state, String actor) {
        return new Incident(value.id(), value.fingerprint(), value.clusterId(), value.clusterName(), value.namespace(),
                value.resourceKind(), value.resourceName(), value.category(), value.severity(), state, value.title(),
                value.summary(), value.nextAction(), value.occurrenceCount(), value.reopenCount(), value.sourceAnalysisId(),
                value.firstDetectedAt(), value.lastDetectedAt(), actor);
    }

    private void activity(UUID incidentId, String type, String note, String actor) {
        operationsRepository.saveIncidentActivity(new IncidentActivity(UUID.randomUUID(), incidentId, type,
                null, null, clean(note, 2000), actor, Instant.now()));
    }

    private void audit(String action, String type, String id, String actor, String requestId) {
        auditRepository.save(AuditLog.create(action, type, id, actor, requestId));
    }

    private boolean allowed(ResolvedAccess access, Capability capability, UUID clusterId, String namespace) {
        return access == null || accessService.allows(access, capability, clusterId, namespace);
    }

    private void require(ResolvedAccess access, Capability capability, UUID clusterId, String namespace) {
        if (!allowed(access, capability, clusterId, namespace)) {
            throw new AccessDeniedException(capability.value() + " capability is not granted for this scope");
        }
    }

    private void requireAny(ResolvedAccess access, Capability capability) {
        if (access != null && !accessService.hasAccessAtAnyScope(access, capability)) {
            throw new AccessDeniedException(capability.value() + " capability is not granted");
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) return List.of();
        if (tags.size() > 20) throw new IllegalArgumentException("At most 20 tags are allowed");
        return tags.stream().map(value -> clean(value, 50)).filter(java.util.Objects::nonNull).distinct().toList();
    }

    private String normalizeSeverity(String value) {
        String severity = value == null ? "MEDIUM" : value.toUpperCase(Locale.ROOT);
        if (!Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO").contains(severity)) {
            throw new IllegalArgumentException("Unsupported severity: " + severity);
        }
        return severity;
    }

    private String inverse(String relation) {
        return switch (relation) {
            case "MERGED_FROM" -> "MERGED_INTO";
            case "SPLIT_FROM" -> "SPLIT_TO";
            default -> "RELATED";
        };
    }

    private static String required(String value, String label, int max) {
        String cleaned = clean(value, max);
        if (cleaned == null) throw new IllegalArgumentException(label + " is required");
        return cleaned;
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record RunbookInput(
            String signal, String category, String resourceKind, String title, String beginnerExplanation,
            String verificationCommand, String expectedResult, String safeAction, String validationCommand,
            String rollbackGuidance, String safetyLevel, boolean enabled, String changeNote
    ) {
        ManagedRunbook toDomain(String id, int version, String owner, Instant createdAt, Instant updatedAt) {
            return new ManagedRunbook(id, "CUSTOM", signal, category, resourceKind, title, beginnerExplanation,
                    verificationCommand, expectedResult, safeAction, validationCommand, rollbackGuidance,
                    safetyLevel.toUpperCase(Locale.ROOT), version, enabled, owner, createdAt, updatedAt);
        }
    }
}
