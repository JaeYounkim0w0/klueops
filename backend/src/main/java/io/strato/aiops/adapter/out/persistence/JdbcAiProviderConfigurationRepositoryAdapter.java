package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AiProviderConfigurationRepositoryPort;
import io.strato.aiops.domain.ai.AiProviderProfile;
import io.strato.aiops.domain.ai.TenantAiRoutingPolicy;
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

    public JdbcAiProviderConfigurationRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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

    @Override
    public List<AiProviderProfile> findVisibleProfiles(UUID tenantId, int limit) {
        return jdbc.query("""
                select * from ai_provider_profiles where tenant_id is null or tenant_id=?
                order by case when tenant_id is null then 0 else 1 end,name limit ?
                """, this::profile, tenantId, Math.max(1, Math.min(limit, 200)));
    }

    @Override
    public Optional<AiProviderProfile> findProfile(UUID profileId) {
        return jdbc.query("select * from ai_provider_profiles where id=?", this::profile, profileId).stream().findFirst();
    }

    @Override
    public void deleteProfile(UUID profileId) {
        if (jdbc.update("delete from ai_provider_profiles where id=?", profileId) == 0)
            throw new java.util.NoSuchElementException("AI provider profile not found");
    }

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

    @Override
    public List<TenantAiRoutingPolicy> findRouting(UUID tenantId) {
        return jdbc.query("select * from tenant_ai_routing_policies where tenant_id=? order by purpose", this::routing, tenantId);
    }

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

    private TenantAiRoutingPolicy routing(ResultSet rs, int row) throws SQLException {
        return new TenantAiRoutingPolicy(rs.getObject("tenant_id", UUID.class), rs.getString("purpose"),
                rs.getObject("primary_profile_id", UUID.class), rs.getString("model"),
                rs.getObject("fallback_profile_id", UUID.class), rs.getString("fallback_model"),
                rs.getBoolean("external_transfer_allowed"), rs.getInt("maximum_context_chars"),
                rs.getInt("maximum_output_tokens"), rs.getString("updated_by"), instant(rs, "updated_at"));
    }

    private String value(EncryptedSecret secret, int field) {
        if (secret == null) return null;
        return switch (field) { case 0 -> secret.ciphertext(); case 1 -> secret.keyId(); case 2 -> secret.algorithm(); default -> secret.nonce(); };
    }
    private Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
}
