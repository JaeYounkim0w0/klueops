package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.ai.AiProviderProfile;
import io.strato.aiops.domain.ai.TenantAiRoutingPolicy;
import io.strato.aiops.domain.ai.LocalAiModel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiProviderConfigurationRepositoryPort {
    AiProviderProfile saveProfile(AiProviderProfile profile);
    List<AiProviderProfile> findVisibleProfiles(UUID tenantId, int limit);
    Optional<AiProviderProfile> findProfile(UUID profileId);
    void deleteProfile(UUID profileId);
    TenantAiRoutingPolicy saveRouting(TenantAiRoutingPolicy policy);
    List<TenantAiRoutingPolicy> findRouting(UUID tenantId);
    LocalAiModel saveLocalModel(LocalAiModel model);
    List<LocalAiModel> findLocalModels(UUID profileId);
}
