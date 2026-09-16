package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.ProductionEvidenceRepositoryPort;
import io.strato.aiops.domain.operations.ProductionEvidence;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ProductionEvidenceService {

    private final ProductionEvidenceRepositoryPort repository;
    private final Clock clock;

    /** ProductionEvidenceService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    @Autowired
    public ProductionEvidenceService(ProductionEvidenceRepositoryPort repository) {
        this(repository, Clock.systemUTC());
    }

    /** ProductionEvidenceService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    ProductionEvidenceService(ProductionEvidenceRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** ProductionEvidenceService의 importEvidence 처리에 필요한 업무 로직을 수행한다. */
    @Transactional
    public ProductionEvidence.Run importEvidence(String releaseName, String environment, String actor,
                                                  List<CheckInput> inputs) {
        return importEvidence(UUID.randomUUID().toString(), releaseName, environment, actor, inputs);
    }

    /** ProductionEvidenceService의 importEvidence 처리에 필요한 업무 로직을 수행한다. */
    @Transactional
    public ProductionEvidence.Run importEvidence(String importKey, String releaseName, String environment, String actor,
                                                  List<CheckInput> inputs) {
        String normalizedKey = required(importKey);
        if (normalizedKey.length() > 128) throw new IllegalArgumentException("Idempotency key is too long");
        var existing = repository.findByImportKey(normalizedKey);
        if (existing.isPresent()) return existing.get();
        Instant now = clock.instant();
        ProductionEvidence.Run running = ProductionEvidence.Run.start(UUID.randomUUID(), required(releaseName),
                required(environment), required(actor), now);
        List<ProductionEvidence.Check> checks = inputs.stream().map(input -> check(running.id(), input, now)).toList();
        ProductionEvidence.State finalState = checks.stream().anyMatch(item -> item.state() == ProductionEvidence.State.FAILED)
                ? ProductionEvidence.State.FAILED
                : checks.stream().anyMatch(item -> item.state() == ProductionEvidence.State.BLOCKED)
                    ? ProductionEvidence.State.BLOCKED
                    : checks.isEmpty() ? ProductionEvidence.State.BLOCKED : ProductionEvidence.State.PASSED;
        ProductionEvidence.Run saved = repository.save(running.complete(finalState, now, checks));
        repository.saveImportKey(normalizedKey, saved.id());
        return saved;
    }

    /** ProductionEvidenceService의 recent 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<ProductionEvidence.Run> recent(int limit) {
        return repository.findRecent(Math.max(1, Math.min(limit, 100)));
    }

    /** ProductionEvidenceService의 get 처리 결과를 조회해 반환한다. */
    @Transactional(readOnly = true)
    public ProductionEvidence.Run get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Evidence run not found: " + id));
    }

    /** ProductionEvidenceService의 check 처리 입력과 현재 상태의 유효성을 검증한다. */
    private ProductionEvidence.Check check(UUID runId, CheckInput input, Instant now) {
        UUID checkId = UUID.randomUUID();
        List<ProductionEvidence.Artifact> artifacts = input.artifacts() == null ? List.of()
                : input.artifacts().stream().map(value -> ProductionEvidence.Artifact.metadata(checkId,
                        required(value.fileName()), required(value.mediaType()), value.content(),
                        value.reference(), now)).toList();
        ProductionEvidence.State state = ProductionEvidence.State.valueOf(required(input.state()).toUpperCase());
        if (state == ProductionEvidence.State.RUNNING || state == ProductionEvidence.State.EXPIRED) {
            throw new IllegalArgumentException("Imported check must use a completed or not-run state");
        }
        return new ProductionEvidence.Check(checkId, runId, required(input.category()), required(input.code()), state,
                required(input.title()), ProductionEvidence.sanitize(input.detail()),
                ProductionEvidence.sanitize(input.observedValue()), input.action(), Math.max(0, input.durationMs()),
                now, artifacts);
    }

    /** ProductionEvidenceService의 required 처리 입력과 현재 상태의 유효성을 검증한다. */
    private String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Evidence value is required");
        return value.trim();
    }

    public record CheckInput(String category, String code, String state, String title, String detail,
                             String observedValue, String action, long durationMs,
                             List<ArtifactInput> artifacts) {
    }

    public record ArtifactInput(String fileName, String mediaType, String content, String reference) {
    }
}
