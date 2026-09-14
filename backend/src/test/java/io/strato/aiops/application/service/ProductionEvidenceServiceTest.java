package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.ProductionEvidenceRepositoryPort;
import io.strato.aiops.domain.operations.ProductionEvidence;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionEvidenceServiceTest {

    @Test
    void importsChecksWithMaskedDetailsAndDerivesFailedRunState() {
        var repository = new MemoryRepository();
        var service = new ProductionEvidenceService(repository,
                Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));

        var result = service.importEvidence("v1", "staging", "operator", List.of(
                new ProductionEvidenceService.CheckInput("DATABASE", "POSTGRES_RESTORE", "PASSED",
                        "Restore", "password=plain restored", "database=aiops", "none", 1200, List.of()),
                new ProductionEvidenceService.CheckInput("IDENTITY", "OIDC_LOGIN", "FAILED",
                        "OIDC", "Bearer abc.def callback failed", "status=500", "verify callback", 500, List.of())
        ));

        assertThat(result.state()).isEqualTo(ProductionEvidence.State.FAILED);
        assertThat(result.checks()).extracting(ProductionEvidence.Check::detail)
                .containsExactly("password=*** restored", "Bearer *** callback failed");
    }

    @Test
    void returnsExistingRunForTheSameImportKey() {
        var repository = new MemoryRepository();
        var service = new ProductionEvidenceService(repository,
                Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));
        var checks = List.of(new ProductionEvidenceService.CheckInput("RUNTIME", "READY", "PASSED",
                "Ready", "ok", "200", "", 10, List.of()));

        var first = service.importEvidence("same-payload", "v1", "staging", "operator", checks);
        var repeated = service.importEvidence("same-payload", "v1", "staging", "operator", checks);

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(repository.values).hasSize(1);
    }

    private static final class MemoryRepository implements ProductionEvidenceRepositoryPort {
        private final List<ProductionEvidence.Run> values = new ArrayList<>();
        private final java.util.Map<String, UUID> importKeys = new java.util.HashMap<>();

        @Override public ProductionEvidence.Run save(ProductionEvidence.Run run) {
            values.removeIf(item -> item.id().equals(run.id()));
            values.add(run);
            return run;
        }
        @Override public Optional<ProductionEvidence.Run> findById(UUID id) {
            return values.stream().filter(item -> item.id().equals(id)).findFirst();
        }
        @Override public List<ProductionEvidence.Run> findRecent(int limit) { return List.copyOf(values); }
        @Override public Optional<ProductionEvidence.Run> findByImportKey(String importKey) {
            return Optional.ofNullable(importKeys.get(importKey)).flatMap(this::findById);
        }
        @Override public void saveImportKey(String importKey, UUID runId) { importKeys.put(importKey, runId); }
    }
}
