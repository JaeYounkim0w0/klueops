package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.ai.AiProviderProfile;
import io.strato.aiops.domain.ai.TenantAiRoutingPolicy;
import io.strato.aiops.domain.ai.LocalAiModel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiProviderConfigurationRepositoryPort {
    /** AiProviderConfigurationRepositoryPort의 saveProfile 처리에 필요한 데이터를 생성하거나 저장한다. */
    AiProviderProfile saveProfile(AiProviderProfile profile);
    /** AiProviderConfigurationRepositoryPort의 findVisibleProfiles 처리 결과를 조회해 반환한다. */
    List<AiProviderProfile> findVisibleProfiles(UUID tenantId, int limit);
    /** AiProviderConfigurationRepositoryPort의 findProfile 처리 결과를 조회해 반환한다. */
    Optional<AiProviderProfile> findProfile(UUID profileId);
    /** AiProviderConfigurationRepositoryPort의 deleteProfile 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteProfile(UUID profileId);
    /** AiProviderConfigurationRepositoryPort의 saveRouting 처리에 필요한 데이터를 생성하거나 저장한다. */
    TenantAiRoutingPolicy saveRouting(TenantAiRoutingPolicy policy);
    /** AiProviderConfigurationRepositoryPort의 findRouting 처리 결과를 조회해 반환한다. */
    List<TenantAiRoutingPolicy> findRouting(UUID tenantId);
    /** AiProviderConfigurationRepositoryPort의 saveLocalModel 처리에 필요한 데이터를 생성하거나 저장한다. */
    LocalAiModel saveLocalModel(LocalAiModel model);
    /** AiProviderConfigurationRepositoryPort의 findLocalModels 처리 결과를 조회해 반환한다. */
    List<LocalAiModel> findLocalModels(UUID profileId);
    /** 지정 모델이 Tenant routing의 primary/fallback에서 사용 중인지 확인한다. */
    boolean isModelRouted(UUID profileId, String modelTag);
    /** 로컬 런타임에서 제거가 끝난 모델 inventory 행을 삭제한다. */
    void deleteLocalModel(UUID profileId, String modelTag);
}
