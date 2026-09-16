package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.operations.ProductionEvidence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionEvidenceRepositoryPort {
    /** ProductionEvidenceRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    ProductionEvidence.Run save(ProductionEvidence.Run run);
    /** ProductionEvidenceRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    Optional<ProductionEvidence.Run> findById(UUID id);
    /** ProductionEvidenceRepositoryPort의 findRecent 처리 결과를 조회해 반환한다. */
    List<ProductionEvidence.Run> findRecent(int limit);
    /** ProductionEvidenceRepositoryPort의 findByImportKey 처리 결과를 조회해 반환한다. */
    Optional<ProductionEvidence.Run> findByImportKey(String importKey);
    /** ProductionEvidenceRepositoryPort의 saveImportKey 처리에 필요한 데이터를 생성하거나 저장한다. */
    void saveImportKey(String importKey, UUID runId);
}
