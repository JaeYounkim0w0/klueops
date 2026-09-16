package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AiProviderConfigurationRepositoryPort;
import io.strato.aiops.domain.ai.AiProviderProfile;
import io.strato.aiops.domain.ai.TenantAiRoutingPolicy;
import io.strato.aiops.domain.ai.LocalAiModel;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAiProviderConfigurationRepositoryAdapter implements AiProviderConfigurationRepositoryPort {
    private final JdbcTemplate jdbc;

    /** JdbcAiProviderConfigurationRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JdbcAiProviderConfigurationRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 saveProfile 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public AiProviderProfile saveProfile(AiProviderProfile profile) {
        EncryptedSecret secret = profile.credential();
        int updated = jdbc.update("""
                update ai_provider_profiles set tenant_id=?,name=?,provider_type=?,base_url=?,credential_ciphertext=?,
                  credential_key_id=?,credential_algorithm=?,credential_nonce=?,default_model=?,allowed_models_json=?,
                  enabled=?,external_data_transfer=?,validation_status=?,last_validated_at=?,updated_at=? where id=?
                """, profile.tenantId(), profile.name(), profile.providerType(), profile.baseUrl(), value(secret, 0),
                value(secret, 1), value(secret, 2), value(secret, 3), profile.defaultModel(), profile.allowedModelsJson(),
                profile.enabled(), profile.externalDataTransfer(), profile.validationStatus(), timestamp(profile.lastValidatedAt()),
                timestamp(profile.updatedAt()), profile.id());
        if (updated == 0) jdbc.update("""
                insert into ai_provider_profiles(id,tenant_id,name,provider_type,base_url,credential_ciphertext,
                  credential_key_id,credential_algorithm,credential_nonce,default_model,allowed_models_json,enabled,
                  external_data_transfer,validation_status,last_validated_at,created_by,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, profile.id(), profile.tenantId(), profile.name(), profile.providerType(), profile.baseUrl(),
                value(secret, 0), value(secret, 1), value(secret, 2), value(secret, 3), profile.defaultModel(),
                profile.allowedModelsJson(), profile.enabled(), profile.externalDataTransfer(), profile.validationStatus(),
                timestamp(profile.lastValidatedAt()), profile.createdBy(), timestamp(profile.createdAt()), timestamp(profile.updatedAt()));
        return profile;
    }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 findVisibleProfiles 처리 결과를 조회해 반환한다. */
    @Override
    public List<AiProviderProfile> findVisibleProfiles(UUID tenantId, int limit) {
        return jdbc.query("""
                select * from ai_provider_profiles where tenant_id is null or tenant_id=?
                order by case when tenant_id is null then 0 else 1 end,name limit ?
                """, this::profile, tenantId, Math.max(1, Math.min(limit, 200)));
    }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 findProfile 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<AiProviderProfile> findProfile(UUID profileId) {
        return jdbc.query("select * from ai_provider_profiles where id=?", this::profile, profileId).stream().findFirst();
    }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 deleteProfile 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void deleteProfile(UUID profileId) {
        if (jdbc.update("delete from ai_provider_profiles where id=?", profileId) == 0)
            throw new java.util.NoSuchElementException("AI provider profile not found");
    }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 saveRouting 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public TenantAiRoutingPolicy saveRouting(TenantAiRoutingPolicy policy) {
        jdbc.update("""
                insert into tenant_ai_routing_policies(tenant_id,purpose,primary_profile_id,model,fallback_profile_id,
                  fallback_model,external_transfer_allowed,maximum_context_chars,maximum_output_tokens,updated_by,updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,purpose) do update set
                  primary_profile_id=excluded.primary_profile_id,model=excluded.model,
                  fallback_profile_id=excluded.fallback_profile_id,fallback_model=excluded.fallback_model,
                  external_transfer_allowed=excluded.external_transfer_allowed,
                  maximum_context_chars=excluded.maximum_context_chars,maximum_output_tokens=excluded.maximum_output_tokens,
                  updated_by=excluded.updated_by,updated_at=excluded.updated_at
                """, policy.tenantId(), policy.purpose(), policy.primaryProfileId(), policy.model(),
                policy.fallbackProfileId(), policy.fallbackModel(), policy.externalTransferAllowed(),
                policy.maximumContextChars(), policy.maximumOutputTokens(), policy.updatedBy(), timestamp(policy.updatedAt()));
        return policy;
    }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 findRouting 처리 결과를 조회해 반환한다. */
    @Override
    public List<TenantAiRoutingPolicy> findRouting(UUID tenantId) {
        return jdbc.query("select * from tenant_ai_routing_policies where tenant_id=? order by purpose", this::routing, tenantId);
    }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 saveLocalModel 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public LocalAiModel saveLocalModel(LocalAiModel model) {
        jdbc.update("""
                insert into local_ai_models(id,provider_profile_id,model_tag,parameter_billions,status,size_bytes,digest,
                  evaluation_score,evaluation_samples,average_latency_ms,evaluated_at,promoted,updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(provider_profile_id,model_tag) do update set
                  parameter_billions=excluded.parameter_billions,status=excluded.status,size_bytes=excluded.size_bytes,
                  digest=excluded.digest,evaluation_score=excluded.evaluation_score,
                  evaluation_samples=excluded.evaluation_samples,average_latency_ms=excluded.average_latency_ms,
                  evaluated_at=excluded.evaluated_at,promoted=excluded.promoted,updated_at=excluded.updated_at
                """, model.id(), model.providerProfileId(), model.modelTag(), model.parameterBillions(), model.status(),
                model.sizeBytes(), model.digest(), model.evaluationScore(), model.evaluationSamples(),
                model.averageLatencyMs(), timestamp(model.evaluatedAt()), model.promoted(), timestamp(model.updatedAt()));
        return model;
    }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 findLocalModels 처리 결과를 조회해 반환한다. */
    @Override
    public List<LocalAiModel> findLocalModels(UUID profileId) {
        return jdbc.query("select * from local_ai_models where provider_profile_id=? order by model_tag", (rs, row) ->
                new LocalAiModel(rs.getObject("id", UUID.class), rs.getObject("provider_profile_id", UUID.class),
                        rs.getString("model_tag"), nullableDouble(rs, "parameter_billions"),
                        rs.getString("status"), rs.getObject("size_bytes", Long.class), rs.getString("digest"),
                        rs.getObject("evaluation_score", Integer.class), rs.getObject("evaluation_samples", Integer.class),
                        rs.getObject("average_latency_ms", Long.class), instant(rs, "evaluated_at"),
                        rs.getBoolean("promoted"), instant(rs, "updated_at")), profileId);
    }

    /** primary 또는 fallback routing에서 모델 참조 여부를 조회한다. */
    @Override
    public boolean isModelRouted(UUID profileId, String modelTag) {
        Integer count = jdbc.queryForObject("""
                select count(*) from tenant_ai_routing_policies
                where (primary_profile_id=? and model=?) or (fallback_profile_id=? and fallback_model=?)
                """, Integer.class, profileId, modelTag, profileId, modelTag);
        return count != null && count > 0;
    }

    /** 로컬 모델 inventory 행을 삭제한다. */
    @Override
    public void deleteLocalModel(UUID profileId, String modelTag) {
        jdbc.update("delete from local_ai_models where provider_profile_id=? and model_tag=?", profileId, modelTag);
    }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 nullableDouble 처리에 필요한 업무 로직을 수행한다. */
    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        // PostgreSQL numeric은 드라이버에서 BigDecimal로 반환되므로 Number를 통해 안전하게 변환한다.
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.doubleValue();
    }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 profile 처리에 필요한 업무 로직을 수행한다. */
    private AiProviderProfile profile(ResultSet rs, int row) throws SQLException {
        String ciphertext = rs.getString("credential_ciphertext");
        EncryptedSecret credential = ciphertext == null ? null : new EncryptedSecret(ciphertext,
                rs.getString("credential_key_id"), rs.getString("credential_algorithm"), rs.getString("credential_nonce"));
        return new AiProviderProfile(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("name"), rs.getString("provider_type"), rs.getString("base_url"), credential,
                rs.getString("default_model"), rs.getString("allowed_models_json"), rs.getBoolean("enabled"),
                rs.getBoolean("external_data_transfer"), rs.getString("validation_status"),
                instant(rs, "last_validated_at"), rs.getString("created_by"), instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 routing 처리에 필요한 업무 로직을 수행한다. */
    private TenantAiRoutingPolicy routing(ResultSet rs, int row) throws SQLException {
        return new TenantAiRoutingPolicy(rs.getObject("tenant_id", UUID.class), rs.getString("purpose"),
                rs.getObject("primary_profile_id", UUID.class), rs.getString("model"),
                rs.getObject("fallback_profile_id", UUID.class), rs.getString("fallback_model"),
                rs.getBoolean("external_transfer_allowed"), rs.getInt("maximum_context_chars"),
                rs.getInt("maximum_output_tokens"), rs.getString("updated_by"), instant(rs, "updated_at"));
    }

    /** JdbcAiProviderConfigurationRepositoryAdapter의 value 처리에 필요한 업무 로직을 수행한다. */
    private String value(EncryptedSecret secret, int field) {
        if (secret == null) return null;
        return switch (field) { case 0 -> secret.ciphertext(); case 1 -> secret.keyId(); case 2 -> secret.algorithm(); default -> secret.nonce(); };
    }
    /** JdbcAiProviderConfigurationRepositoryAdapter의 timestamp 처리에 필요한 업무 로직을 수행한다. */
    private Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    /** JdbcAiProviderConfigurationRepositoryAdapter의 instant 처리에 필요한 업무 로직을 수행한다. */
    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
}
