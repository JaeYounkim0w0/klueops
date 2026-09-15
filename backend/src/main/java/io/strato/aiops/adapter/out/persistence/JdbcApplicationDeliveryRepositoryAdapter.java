package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ApplicationDeliveryRepositoryPort;
import io.strato.aiops.domain.applicationdelivery.ChartSourceType;
import io.strato.aiops.domain.applicationdelivery.ChartSource;
import io.strato.aiops.domain.applicationdelivery.ChartTrustStatus;
import io.strato.aiops.domain.applicationdelivery.ChartVersion;
import io.strato.aiops.domain.applicationdelivery.TenantChart;
import io.strato.aiops.domain.applicationdelivery.ValuesProfile;
import io.strato.aiops.domain.applicationdelivery.ValuesRevision;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcApplicationDeliveryRepositoryAdapter implements ApplicationDeliveryRepositoryPort {
    private final JdbcTemplate jdbc;

    public JdbcApplicationDeliveryRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ChartSource saveSource(ChartSource source) {
        EncryptedSecret credential = source.credential();
        int updated = jdbc.update("""
                update chart_sources set name=?,endpoint=?,credential_ciphertext=?,credential_key_id=?,
                  credential_algorithm=?,credential_nonce=?,tls_policy=?,enabled=?,updated_at=?
                where tenant_id=? and id=?
                """, source.name(), source.endpoint(), ciphertext(credential), keyId(credential),
                algorithm(credential), nonce(credential), source.tlsPolicy(), source.enabled(),
                Timestamp.from(source.updatedAt()), source.tenantId(), source.id());
        if (updated == 0) {
            jdbc.update("""
                    insert into chart_sources(id,tenant_id,source_type,name,endpoint,credential_ciphertext,
                      credential_key_id,credential_algorithm,credential_nonce,tls_policy,enabled,created_by,created_at,updated_at)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, source.id(), source.tenantId(), source.sourceType().name(), source.name(), source.endpoint(),
                    ciphertext(credential), keyId(credential), algorithm(credential), nonce(credential),
                    source.tlsPolicy(), source.enabled(), source.createdBy(), Timestamp.from(source.createdAt()),
                    Timestamp.from(source.updatedAt()));
        }
        return source;
    }

    @Override
    public List<ChartSource> findSources(UUID tenantId, int limit) {
        return jdbc.query("select * from chart_sources where tenant_id=? order by name limit ?",
                this::source, tenantId, bounded(limit, 200));
    }

    @Override
    public Optional<ChartSource> findSource(UUID tenantId, UUID sourceId) {
        return first(jdbc.query("select * from chart_sources where tenant_id=? and id=?", this::source,
                tenantId, sourceId));
    }

    @Override
    public void deleteSource(UUID tenantId, UUID sourceId) {
        if (jdbc.update("delete from chart_sources where tenant_id=? and id=?", tenantId, sourceId) == 0) {
            throw new java.util.NoSuchElementException("Chart source not found: " + sourceId);
        }
    }

    @Override
    public Artifact saveArtifact(String digestSha256, byte[] payload, Instant now) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into chart_artifacts(id,digest_sha256,payload,size_bytes,created_at) values (?,?,?,?,?)",
                    id, digestSha256, payload, payload.length, Timestamp.from(now));
        } catch (DuplicateKeyException ignored) {
            // 같은 payload는 Tenant와 무관하게 digest 기준으로 한 번만 보관한다.
        }
        return jdbc.queryForObject("select id,digest_sha256,size_bytes,created_at from chart_artifacts where digest_sha256=?",
                (rs, row) -> new Artifact(rs.getObject("id", UUID.class), rs.getString("digest_sha256"),
                        rs.getLong("size_bytes"), rs.getTimestamp("created_at").toInstant()), digestSha256);
    }

    @Override
    public Optional<TenantChart> findChartByCoordinate(UUID tenantId, ChartSourceType sourceType, String sourceName,
                                                       String packageName) {
        return first(jdbc.query("""
                select * from tenant_charts where tenant_id=? and source_type=?
                  and coalesce(source_name,'')=coalesce(?,'') and package_name=?
                """, this::chart, tenantId, sourceType.name(), sourceName, packageName));
    }

    @Override
    public TenantChart saveChart(TenantChart chart) {
        jdbc.update("""
                insert into tenant_charts(id,tenant_id,name,description,source_type,source_name,repository_url,
                  package_name,trust_status,archived_at,created_by,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, chart.id(), chart.tenantId(), chart.name(), chart.description(), chart.sourceType().name(),
                chart.sourceName(), chart.repositoryUrl(), chart.packageName(), chart.trustStatus().name(),
                timestamp(chart.archivedAt()), chart.createdBy(), Timestamp.from(chart.createdAt()),
                Timestamp.from(chart.updatedAt()));
        return chart;
    }

    @Override
    public Optional<ChartVersion> findVersionByChartAndVersion(UUID tenantId, UUID chartId, String version) {
        return first(jdbc.query("""
                select v.* from chart_versions v join tenant_charts c on c.id=v.tenant_chart_id
                where c.tenant_id=? and c.id=? and v.chart_version=?
                """, this::version, tenantId, chartId, version));
    }

    @Override
    public ChartVersion saveVersion(ChartVersion version) {
        jdbc.update("""
                insert into chart_versions(id,tenant_chart_id,chart_version,app_version,source_reference,
                  digest_sha256,provenance_status,artifact_id,metadata_json,imported_by,imported_at)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """, version.id(), version.tenantChartId(), version.chartVersion(), version.appVersion(),
                version.sourceReference(), version.digestSha256(), version.provenanceStatus().name(),
                version.artifactId(), version.metadataJson(), version.importedBy(), Timestamp.from(version.importedAt()));
        return version;
    }

    @Override
    public List<TenantChart> findCharts(UUID tenantId, boolean includeArchived, int limit) {
        return jdbc.query("""
                select * from tenant_charts where tenant_id=? and (? or archived_at is null)
                order by updated_at desc limit ?
                """, this::chart, tenantId, includeArchived, bounded(limit, 200));
    }

    @Override
    public List<ChartVersion> findVersions(UUID tenantId, UUID chartId, int limit) {
        return jdbc.query("""
                select v.* from chart_versions v join tenant_charts c on c.id=v.tenant_chart_id
                where c.tenant_id=? and c.id=? order by v.imported_at desc limit ?
                """, this::version, tenantId, chartId, bounded(limit, 100));
    }

    @Override
    public List<ChartVersion> findVersions(UUID tenantId, Collection<UUID> chartIds, int perChartLimit) {
        if (chartIds == null || chartIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(chartIds.size(), "?"));
        String sql = """
                select ranked.* from (
                  select v.*, row_number() over(partition by v.tenant_chart_id order by v.imported_at desc) rank
                  from chart_versions v join tenant_charts c on c.id=v.tenant_chart_id
                  where c.tenant_id=? and v.tenant_chart_id in (%s)
                ) ranked where ranked.rank <= ? order by ranked.imported_at desc
                """.formatted(placeholders);
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(tenantId);
        parameters.addAll(chartIds);
        parameters.add(bounded(perChartLimit, 20));
        return jdbc.query(sql, this::version, parameters.toArray());
    }

    @Override
    public Optional<ChartVersion> findVersion(UUID tenantId, UUID versionId) {
        return first(jdbc.query("""
                select v.* from chart_versions v join tenant_charts c on c.id=v.tenant_chart_id
                where c.tenant_id=? and v.id=?
                """, this::version, tenantId, versionId));
    }

    @Override
    public byte[] loadArtifact(UUID tenantId, UUID versionId) {
        return jdbc.queryForObject("""
                select a.payload from chart_artifacts a join chart_versions v on v.artifact_id=a.id
                  join tenant_charts c on c.id=v.tenant_chart_id where c.tenant_id=? and v.id=?
                """, byte[].class, tenantId, versionId);
    }

    @Override
    public ValuesProfile saveProfile(ValuesProfile profile) {
        jdbc.update("""
                insert into values_profiles(id,tenant_id,chart_version_id,name,description,created_by,created_at,updated_at)
                values (?,?,?,?,?,?,?,?)
                """, profile.id(), profile.tenantId(), profile.chartVersionId(), profile.name(), profile.description(),
                profile.createdBy(), Timestamp.from(profile.createdAt()), Timestamp.from(profile.updatedAt()));
        return profile;
    }

    @Override
    public List<ValuesProfile> findProfiles(UUID tenantId, UUID chartVersionId, int limit) {
        return jdbc.query("""
                select * from values_profiles where tenant_id=? and chart_version_id=?
                order by updated_at desc limit ?
                """, this::profile, tenantId, chartVersionId, bounded(limit, 100));
    }

    @Override
    public Optional<ValuesProfile> findProfile(UUID tenantId, UUID profileId) {
        return first(jdbc.query("select * from values_profiles where tenant_id=? and id=?",
                this::profile, tenantId, profileId));
    }

    @Override
    public int nextRevision(UUID profileId) {
        Integer value = jdbc.queryForObject("select coalesce(max(revision),0)+1 from values_revisions where profile_id=?",
                Integer.class, profileId);
        return value == null ? 1 : value;
    }

    @Override
    public ValuesRevision saveRevision(ValuesRevision revision) {
        EncryptedSecret secret = revision.encryptedValues();
        jdbc.update("""
                insert into values_revisions(id,profile_id,revision,values_ciphertext,values_key_id,values_algorithm,
                  values_nonce,values_sha256,parent_revision,created_by,created_at) values (?,?,?,?,?,?,?,?,?,?,?)
                """, revision.id(), revision.profileId(), revision.revision(), secret.ciphertext(), secret.keyId(),
                secret.algorithm(), secret.nonce(), revision.valuesSha256(), revision.parentRevision(),
                revision.createdBy(), Timestamp.from(revision.createdAt()));
        return revision;
    }

    @Override
    public List<ValuesRevision> findRevisions(UUID tenantId, UUID profileId, int limit) {
        return jdbc.query("""
                select r.* from values_revisions r join values_profiles p on p.id=r.profile_id
                where p.tenant_id=? and p.id=? order by r.revision desc limit ?
                """, this::revision, tenantId, profileId, bounded(limit, 100));
    }

    @Override
    public Optional<ValuesRevision> findRevision(UUID tenantId, UUID revisionId) {
        return first(jdbc.query("""
                select r.* from values_revisions r join values_profiles p on p.id=r.profile_id
                where p.tenant_id=? and r.id=?
                """, this::revision, tenantId, revisionId));
    }

    private TenantChart chart(ResultSet rs, int row) throws SQLException {
        return new TenantChart(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("name"), rs.getString("description"), ChartSourceType.valueOf(rs.getString("source_type")),
                rs.getString("source_name"), rs.getString("repository_url"), rs.getString("package_name"),
                ChartTrustStatus.valueOf(rs.getString("trust_status")), instant(rs, "archived_at"),
                rs.getString("created_by"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private ChartSource source(ResultSet rs, int row) throws SQLException {
        String ciphertext = rs.getString("credential_ciphertext");
        EncryptedSecret credential = ciphertext == null ? null : new EncryptedSecret(ciphertext,
                rs.getString("credential_key_id"), rs.getString("credential_algorithm"),
                rs.getString("credential_nonce"));
        return new ChartSource(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                ChartSourceType.valueOf(rs.getString("source_type")), rs.getString("name"), rs.getString("endpoint"),
                credential, rs.getString("tls_policy"), rs.getBoolean("enabled"), rs.getString("created_by"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private String ciphertext(EncryptedSecret value) { return value == null ? null : value.ciphertext(); }
    private String keyId(EncryptedSecret value) { return value == null ? null : value.keyId(); }
    private String algorithm(EncryptedSecret value) { return value == null ? null : value.algorithm(); }
    private String nonce(EncryptedSecret value) { return value == null ? null : value.nonce(); }

    private ChartVersion version(ResultSet rs, int row) throws SQLException {
        return new ChartVersion(rs.getObject("id", UUID.class), rs.getObject("tenant_chart_id", UUID.class),
                rs.getString("chart_version"), rs.getString("app_version"), rs.getString("source_reference"),
                rs.getString("digest_sha256"), ChartTrustStatus.valueOf(rs.getString("provenance_status")),
                rs.getObject("artifact_id", UUID.class), rs.getString("metadata_json"), rs.getString("imported_by"),
                instant(rs, "imported_at"));
    }

    private ValuesProfile profile(ResultSet rs, int row) throws SQLException {
        return new ValuesProfile(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("chart_version_id", UUID.class), rs.getString("name"), rs.getString("description"),
                rs.getString("created_by"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private ValuesRevision revision(ResultSet rs, int row) throws SQLException {
        return new ValuesRevision(rs.getObject("id", UUID.class), rs.getObject("profile_id", UUID.class),
                rs.getInt("revision"), new EncryptedSecret(rs.getString("values_ciphertext"),
                rs.getString("values_key_id"), rs.getString("values_algorithm"), rs.getString("values_nonce")),
                rs.getString("values_sha256"), (Integer) rs.getObject("parent_revision"), rs.getString("created_by"),
                instant(rs, "created_at"));
    }

    private int bounded(int requested, int maximum) {
        return Math.max(1, Math.min(requested, maximum));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }

}
