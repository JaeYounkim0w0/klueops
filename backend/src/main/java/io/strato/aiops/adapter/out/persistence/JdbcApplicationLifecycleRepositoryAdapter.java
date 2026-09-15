package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ApplicationLifecycleRepositoryPort;
import io.strato.aiops.domain.applicationdelivery.DeploymentPlan;
import io.strato.aiops.domain.applicationdelivery.ApplicationRelease;
import io.strato.aiops.domain.applicationdelivery.ReleaseOperation;
import io.strato.aiops.domain.applicationdelivery.ApplicationEndpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcApplicationLifecycleRepositoryAdapter implements ApplicationLifecycleRepositoryPort {
    private final JdbcTemplate jdbc;

    public JdbcApplicationLifecycleRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DeploymentPlan savePlan(DeploymentPlan plan) {
        jdbc.update("""
                insert into deployment_plans(id,application_id,cluster_id,chart_version_id,values_revision_id,namespace,release_name,create_namespace,
                  exposure_type,hostname,exposure_path,backend_service_name,backend_service_port,gateway_name,
                  gateway_namespace,rendered_manifest,manifest_sha256,warnings_json,confirmation_text,
                  created_by,created_at,expires_at,consumed_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, plan.id(), plan.applicationId(), plan.clusterId(), plan.chartVersionId(), plan.valuesRevisionId(), plan.namespace(),
                plan.releaseName(), plan.createNamespace(), plan.exposureType(), plan.hostname(), plan.exposurePath(), plan.backendServiceName(),
                plan.backendServicePort(), plan.gatewayName(), plan.gatewayNamespace(), plan.renderedManifest(), plan.manifestSha256(),
                plan.warningsJson(), plan.confirmationText(), plan.createdBy(), timestamp(plan.createdAt()),
                timestamp(plan.expiresAt()), timestamp(plan.consumedAt()));
        return plan;
    }

    @Override
    public Optional<DeploymentPlan> findPlan(UUID tenantId, UUID planId) {
        List<DeploymentPlan> values = jdbc.query("""
                select p.* from deployment_plans p join clusters c on c.id=p.cluster_id
                where c.tenant_id=? and p.id=?
                """, this::plan, tenantId, planId);
        return values.stream().findFirst();
    }

    @Override
    public boolean consumePlan(UUID planId, Instant consumedAt) {
        return jdbc.update("update deployment_plans set consumed_at=? where id=? and consumed_at is null and expires_at>?",
                timestamp(consumedAt), planId, timestamp(consumedAt)) == 1;
    }

    @Override
    public ReleaseOperation saveOperation(ReleaseOperation operation) {
        int updated = jdbc.update("""
                update release_operations set status=?,release_revision=?,output_summary=?,error_message=?,completed_at=?
                where id=?
                """, operation.status(), operation.releaseRevision(), operation.outputSummary(), operation.errorMessage(),
                timestamp(operation.completedAt()), operation.id());
        if (updated == 0) {
            jdbc.update("""
                    insert into release_operations(id,application_id,async_job_id,operation_type,status,release_revision,
                      output_summary,error_message,requested_by,requested_at,completed_at) values (?,?,?,?,?,?,?,?,?,?,?)
                    """, operation.id(), operation.applicationId(), operation.asyncJobId(), operation.operationType(),
                    operation.status(), operation.releaseRevision(), operation.outputSummary(), operation.errorMessage(),
                    operation.requestedBy(), timestamp(operation.requestedAt()), timestamp(operation.completedAt()));
        }
        return operation;
    }

    @Override
    public List<ReleaseOperation> findOperations(UUID tenantId, UUID applicationId, int limit) {
        return jdbc.query("""
                select o.* from release_operations o join managed_applications a on a.id=o.application_id
                  join clusters c on c.id=a.cluster_id
                where c.tenant_id=? and a.id=? order by o.requested_at desc limit ?
                """, this::operation, tenantId, applicationId, Math.max(1, Math.min(limit, 200)));
    }

    @Override
    public ApplicationRelease saveRelease(ApplicationRelease release) {
        jdbc.update("""
                insert into application_releases(id,application_id,revision,chart_version_id,values_revision_id,
                  manifest_sha256,status,created_by,created_at) values (?,?,?,?,?,?,?,?,?)
                """, release.id(), release.applicationId(), release.revision(), release.chartVersionId(),
                release.valuesRevisionId(), release.manifestSha256(), release.status(), release.createdBy(),
                timestamp(release.createdAt()));
        jdbc.update("update managed_applications set current_release_revision=?,chart_version_id=?,values_revision_id=? where id=?",
                release.revision(), release.chartVersionId(), release.valuesRevisionId(), release.applicationId());
        return release;
    }

    @Override
    public List<ApplicationRelease> findReleases(UUID tenantId, UUID applicationId, int limit) {
        return jdbc.query("""
                select r.* from application_releases r join managed_applications a on a.id=r.application_id
                  join clusters c on c.id=a.cluster_id where c.tenant_id=? and a.id=?
                order by r.revision desc limit ?
                """, this::release, tenantId, applicationId, Math.max(1, Math.min(limit, 100)));
    }

    @Override
    public ApplicationEndpoint saveEndpoint(ApplicationEndpoint endpoint) {
        jdbc.update("""
                insert into application_endpoints(id,application_id,endpoint_type,url,hostname,status,created_at,updated_at)
                values (?,?,?,?,?,?,?,?) on conflict(application_id,endpoint_type,hostname) do update set
                  url=excluded.url,status=excluded.status,updated_at=excluded.updated_at
                """, endpoint.id(), endpoint.applicationId(), endpoint.endpointType(), endpoint.url(), endpoint.hostname(),
                endpoint.status(), timestamp(endpoint.createdAt()), timestamp(endpoint.updatedAt()));
        return endpoint;
    }

    @Override
    public List<ApplicationEndpoint> findEndpoints(UUID tenantId, UUID applicationId) {
        return jdbc.query("""
                select e.* from application_endpoints e join managed_applications a on a.id=e.application_id
                  join clusters c on c.id=a.cluster_id where c.tenant_id=? and a.id=? order by e.created_at
                """, (rs, row) -> new ApplicationEndpoint(rs.getObject("id", UUID.class),
                rs.getObject("application_id", UUID.class), rs.getString("endpoint_type"), rs.getString("url"),
                rs.getString("hostname"), rs.getString("status"), instant(rs, "created_at"),
                instant(rs, "updated_at")), tenantId, applicationId);
    }

    @Override
    @Transactional
    public void deleteApplicationGraph(UUID applicationId) {
        // Uninstall 성공 후 화면에 유령 Application이 남지 않도록 종속 데이터를 FK 순서로 원자적으로 정리한다.
        jdbc.update("delete from application_endpoints where application_id=?", applicationId);
        jdbc.update("delete from application_releases where application_id=?", applicationId);
        jdbc.update("delete from release_operations where application_id=?", applicationId);
        jdbc.update("delete from deployment_plans where application_id=?", applicationId);
        jdbc.update("delete from managed_applications where id=?", applicationId);
    }

    @Override
    public void recoverTimedOutOperation(UUID jobId, Instant completedAt, String errorMessage) {
        // Backend 재시작 등으로 worker가 사라진 Helm 작업과 Application 상태를 함께 종결한다.
        jdbc.update("""
                update managed_applications set status='FAILED',last_sync_status='HELM_JOB_TIMEOUT',
                  last_sync_error=?,updated_at=? where status in ('DEPLOYING','UPGRADING','ROLLING_BACK','UNINSTALLING')
                  and id in (select application_id from release_operations where async_job_id=?)
                """, errorMessage, timestamp(completedAt), jobId);
        jdbc.update("""
                update release_operations set status='FAILED',error_message=?,completed_at=?
                where async_job_id=? and status in ('PENDING','RUNNING')
                """, errorMessage, timestamp(completedAt), jobId);
    }

    @Override
    public int recoverOrphanedOperations(Instant completedAt) {
        // 이전 프로세스에서 Job만 종료되고 작업 이력이 남은 경우에도 최종 상태로 수렴시킨다.
        int recovered = jdbc.update("""
                update release_operations ro set status='FAILED',
                  error_message=coalesce(ro.error_message,'Linked async job ended before the Helm operation completed'),
                  completed_at=coalesce(ro.completed_at,?)
                from async_jobs j where ro.async_job_id=j.id and ro.status in ('PENDING','RUNNING')
                  and j.status in ('FAILED','CANCELED','TIMEOUT')
                """, timestamp(completedAt));
        jdbc.update("""
                update managed_applications a set status='FAILED',last_sync_status='HELM_JOB_TERMINATED',
                  last_sync_error='Linked async job ended before the Helm operation completed',updated_at=?
                where a.status in ('DEPLOYING','UPGRADING','ROLLING_BACK','UNINSTALLING')
                  and exists (select 1 from release_operations ro join async_jobs j on j.id=ro.async_job_id
                    where ro.application_id=a.id and ro.status='FAILED'
                      and j.status in ('FAILED','CANCELED','TIMEOUT'))
                """, timestamp(completedAt));
        return recovered;
    }

    private DeploymentPlan plan(ResultSet rs, int row) throws SQLException {
        return new DeploymentPlan(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class), rs.getObject("cluster_id", UUID.class),
                rs.getObject("chart_version_id", UUID.class), rs.getObject("values_revision_id", UUID.class),
                rs.getString("namespace"), rs.getString("release_name"), rs.getBoolean("create_namespace"), rs.getString("exposure_type"),
                rs.getString("hostname"), rs.getString("exposure_path"), rs.getString("backend_service_name"),
                (Integer) rs.getObject("backend_service_port"), rs.getString("gateway_name"),
                rs.getString("gateway_namespace"), rs.getString("rendered_manifest"), rs.getString("manifest_sha256"),
                rs.getString("warnings_json"), rs.getString("confirmation_text"), rs.getString("created_by"),
                instant(rs, "created_at"), instant(rs, "expires_at"), instant(rs, "consumed_at"));
    }

    private ReleaseOperation operation(ResultSet rs, int row) throws SQLException {
        return new ReleaseOperation(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
                rs.getObject("async_job_id", UUID.class), rs.getString("operation_type"), rs.getString("status"),
                (Integer) rs.getObject("release_revision"), rs.getString("output_summary"), rs.getString("error_message"),
                rs.getString("requested_by"), instant(rs, "requested_at"), instant(rs, "completed_at"));
    }

    private ApplicationRelease release(ResultSet rs, int row) throws SQLException {
        return new ApplicationRelease(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
                rs.getInt("revision"), rs.getObject("chart_version_id", UUID.class),
                rs.getObject("values_revision_id", UUID.class), rs.getString("manifest_sha256"),
                rs.getString("status"), rs.getString("created_by"), instant(rs, "created_at"));
    }

    private Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
