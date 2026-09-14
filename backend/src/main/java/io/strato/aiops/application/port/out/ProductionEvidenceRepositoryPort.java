package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.operations.ProductionEvidence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionEvidenceRepositoryPort {
    ProductionEvidence.Run save(ProductionEvidence.Run run);
    Optional<ProductionEvidence.Run> findById(UUID id);
    List<ProductionEvidence.Run> findRecent(int limit);
    Optional<ProductionEvidence.Run> findByImportKey(String importKey);
    void saveImportKey(String importKey, UUID runId);
}
