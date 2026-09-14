create table incidents (
    id uuid primary key,
    fingerprint varchar(64) not null unique,
    cluster_id uuid not null,
    namespace varchar(255),
    resource_kind varchar(100),
    resource_name varchar(255),
    category varchar(100) not null,
    severity varchar(20) not null,
    state varchar(30) not null,
    title varchar(500) not null,
    summary varchar(4000),
    next_action varchar(2000),
    occurrence_count integer not null,
    reopen_count integer not null,
    source_analysis_id uuid,
    first_detected_at timestamp with time zone not null,
    last_detected_at timestamp with time zone not null,
    updated_by varchar(255) not null,
    constraint fk_incident_cluster foreign key (cluster_id) references clusters(id) on delete cascade,
    constraint fk_incident_analysis foreign key (source_analysis_id) references analysis_sessions(id) on delete set null
);

create index idx_incidents_state_severity on incidents(state, severity, last_detected_at);
create index idx_incidents_cluster_namespace on incidents(cluster_id, namespace, last_detected_at);

create table incident_evidence (
    id uuid primary key,
    incident_id uuid not null,
    evidence_key varchar(64) not null,
    evidence_type varchar(50) not null,
    source_ref varchar(1000),
    summary varchar(4000) not null,
    factual boolean not null,
    occurred_at timestamp with time zone not null,
    constraint uk_incident_evidence unique (incident_id, evidence_key),
    constraint fk_incident_evidence foreign key (incident_id) references incidents(id) on delete cascade
);

create index idx_incident_evidence_incident on incident_evidence(incident_id, occurred_at);

create table incident_activities (
    id uuid primary key,
    incident_id uuid not null,
    activity_type varchar(50) not null,
    from_state varchar(30),
    to_state varchar(30),
    note varchar(2000),
    actor varchar(255) not null,
    created_at timestamp with time zone not null,
    constraint fk_incident_activity foreign key (incident_id) references incidents(id) on delete cascade
);

create index idx_incident_activities_incident on incident_activities(incident_id, created_at);

create table operation_notifications (
    id uuid primary key,
    dedup_key varchar(64) not null unique,
    notification_type varchar(50) not null,
    severity varchar(20) not null,
    title varchar(500) not null,
    message varchar(2000),
    target_path varchar(1000),
    is_read boolean not null,
    occurrence_count integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_operation_notifications_read on operation_notifications(is_read, updated_at);

create table policy_definitions (
    id varchar(100) primary key,
    name varchar(255) not null,
    description varchar(2000) not null,
    category varchar(100) not null,
    severity varchar(20) not null,
    enabled boolean not null
);

create table policy_evaluations (
    id uuid primary key,
    policy_id varchar(100) not null,
    cluster_id uuid not null,
    namespace varchar(255),
    resource_kind varchar(100),
    resource_name varchar(255),
    result varchar(30) not null,
    evidence varchar(4000),
    recommendation varchar(2000),
    evaluated_at timestamp with time zone not null,
    constraint fk_policy_evaluation_policy foreign key (policy_id) references policy_definitions(id),
    constraint fk_policy_evaluation_cluster foreign key (cluster_id) references clusters(id) on delete cascade
);

create index idx_policy_evaluations_scope on policy_evaluations(cluster_id, namespace, result, evaluated_at);

create table resource_baselines (
    id uuid primary key,
    cluster_id uuid not null,
    namespace_key varchar(255) not null,
    resource_kind varchar(100) not null,
    resource_name varchar(255) not null,
    status varchar(255),
    summary_hash varchar(64) not null,
    summary_json varchar(8000),
    collected_at timestamp with time zone not null,
    constraint uk_resource_baseline unique (cluster_id, namespace_key, resource_kind, resource_name),
    constraint fk_resource_baseline_cluster foreign key (cluster_id) references clusters(id) on delete cascade
);

create table resource_change_events (
    id uuid primary key,
    cluster_id uuid not null,
    namespace varchar(255),
    resource_kind varchar(100) not null,
    resource_name varchar(255) not null,
    change_type varchar(30) not null,
    previous_status varchar(255),
    current_status varchar(255),
    previous_hash varchar(64),
    current_hash varchar(64),
    summary varchar(2000),
    detected_at timestamp with time zone not null,
    constraint fk_resource_change_cluster foreign key (cluster_id) references clusters(id) on delete cascade
);

create index idx_resource_changes_scope on resource_change_events(cluster_id, namespace, detected_at);

create table runbook_templates (
    id varchar(100) primary key,
    signal varchar(100) not null,
    category varchar(100) not null,
    resource_kind varchar(100),
    title varchar(255) not null,
    beginner_explanation varchar(2000) not null,
    verification_command varchar(2000) not null,
    expected_result varchar(2000) not null,
    safe_action varchar(2000),
    validation_command varchar(2000) not null,
    rollback_guidance varchar(2000),
    safety_level varchar(30) not null,
    version integer not null,
    enabled boolean not null
);

create table analysis_feedback (
    analysis_id uuid primary key,
    accuracy varchar(30) not null,
    outcome varchar(30) not null,
    dangerous_suggestion boolean not null,
    comment varchar(2000),
    submitted_by varchar(255) not null,
    updated_at timestamp with time zone not null,
    constraint fk_analysis_feedback foreign key (analysis_id) references analysis_sessions(id) on delete cascade
);

create table operation_settings (
    id integer primary key,
    event_retention_days integer not null,
    analysis_retention_days integer not null,
    job_retention_days integer not null,
    notification_retention_days integer not null,
    resolved_incident_retention_days integer not null,
    change_retention_days integer not null,
    notification_suppress_minutes integer not null,
    stale_sync_minutes integer not null,
    long_running_job_seconds integer not null,
    updated_by varchar(255) not null,
    updated_at timestamp with time zone not null
);

insert into operation_settings values (1, 7, 90, 30, 30, 180, 30, 30, 15, 180, 'system', current_timestamp);

insert into policy_definitions values
('WORKLOAD_AVAILABILITY', 'Workload availability', 'Desired replicas are not currently available.', 'AVAILABILITY', 'HIGH', true),
('POD_UNHEALTHY', 'Unhealthy Pod', 'Pod is pending, failed, or restarting repeatedly.', 'RELIABILITY', 'HIGH', true),
('PVC_PENDING', 'PVC pending', 'PersistentVolumeClaim is not bound.', 'STORAGE', 'HIGH', true),
('SINGLE_REPLICA', 'Single replica workload', 'A single replica can cause an availability interruption during failure or maintenance.', 'RESILIENCE', 'MEDIUM', true),
('NETWORK_POLICY_MISSING', 'NetworkPolicy missing', 'Namespace traffic isolation could not be confirmed from the synchronized inventory.', 'SECURITY', 'LOW', true),
('REQUESTS_MISSING', 'Resource requests missing', 'One or more workload containers do not define CPU or memory requests.', 'CAPACITY', 'MEDIUM', true),
('LIMITS_MISSING', 'Resource limits missing', 'One or more workload containers do not define CPU or memory limits.', 'CAPACITY', 'MEDIUM', true),
('READINESS_PROBE_MISSING', 'Readiness probe missing', 'One or more workload containers do not define a readiness probe.', 'RELIABILITY', 'MEDIUM', true),
('LIVENESS_PROBE_MISSING', 'Liveness probe missing', 'One or more workload containers do not define a liveness probe.', 'RELIABILITY', 'LOW', true),
('LATEST_IMAGE', 'Mutable image tag', 'A workload container uses the latest tag, which makes rollouts difficult to reproduce.', 'RELEASE', 'MEDIUM', true),
('SERVICE_ENDPOINT_EMPTY', 'Service has no ready endpoint', 'A Service exists but the synchronized Endpoint has no ready address.', 'TRAFFIC', 'HIGH', true),
('PORT_MISMATCH', 'Service target port mismatch', 'Service targetPort does not match declared container ports selected by the Service.', 'TRAFFIC', 'HIGH', true),
('REFERENCE_MISSING', 'Referenced resource missing', 'A Pod or workload volume references a ConfigMap, Secret, or PVC that is not present.', 'STORAGE', 'HIGH', true),
('HPA_COVERAGE', 'Autoscaling coverage', 'A multi-replica workload does not have a matching HorizontalPodAutoscaler.', 'CAPACITY', 'LOW', true),
('PDB_COVERAGE', 'Disruption budget coverage', 'A multi-replica workload does not have a matching PodDisruptionBudget.', 'RESILIENCE', 'LOW', true),
('RESOURCE_QUOTA_MISSING', 'ResourceQuota missing', 'Namespace resource consumption is not bounded by a ResourceQuota.', 'GOVERNANCE', 'LOW', true),
('LIMIT_RANGE_MISSING', 'LimitRange missing', 'Namespace does not provide default request/limit governance.', 'GOVERNANCE', 'LOW', true),
('RESOURCE_GOVERNANCE_UNKNOWN', 'Resource governance evidence', 'Requests, limits, probes, HPA, PDB, quota, and limit range require detailed evidence.', 'GOVERNANCE', 'MEDIUM', true);

insert into runbook_templates values
('RB-FAILEDMOUNT', 'FailedMount', 'STORAGE_CONFIG', 'Pod', 'FailedMount diagnosis', 'Pod가 참조하는 ConfigMap, Secret 또는 PVC를 찾지 못했거나 volume 설정이 일치하지 않습니다.', 'kubectl describe pod/{resourceName} -n {namespace}', 'Events에 누락된 참조 이름 또는 mount 실패 이유가 표시됩니다.', null, 'kubectl get pod/{resourceName} -n {namespace}', '변경 전 workload manifest와 참조 리소스 이름을 기록하고 원래 값으로 되돌릴 수 있어야 합니다.', 'READ_ONLY', 1, true),
('RB-CRASHLOOP', 'CrashLoopBackOff', 'APPLICATION_STARTUP', 'Pod', 'CrashLoopBackOff diagnosis', '컨테이너가 시작된 뒤 반복 종료되고 있습니다. 현재 로그와 직전 컨테이너 로그를 함께 확인해야 합니다.', 'kubectl logs pod/{resourceName} -n {namespace} --tail=300', '종료 직전 오류, 포트, 설정, 권한 또는 의존성 실패가 확인됩니다.', null, 'kubectl get pod/{resourceName} -n {namespace}', '변경한 image, command, env, probe 값을 이전 revision으로 복원합니다.', 'READ_ONLY', 1, true),
('RB-IMAGEPULL', 'ImagePullBackOff', 'IMAGE', 'Pod', 'Image pull diagnosis', '이미지 이름, tag, registry 인증 또는 node의 registry 연결을 확인해야 합니다.', 'kubectl describe pod/{resourceName} -n {namespace}', 'Failed/BackOff event에 registry 응답과 image 이름이 표시됩니다.', null, 'kubectl get pod/{resourceName} -n {namespace}', '변경한 image를 직전 정상 image로 되돌립니다.', 'READ_ONLY', 1, true),
('RB-PROBE', 'Unhealthy', 'PROBE', 'Pod', 'Probe failure diagnosis', '애플리케이션은 실행 중이지만 health probe의 port, path 또는 timeout 조건을 만족하지 못하고 있습니다.', 'kubectl describe pod/{resourceName} -n {namespace}', 'Liveness 또는 Readiness probe 실패 이유와 endpoint가 표시됩니다.', null, 'kubectl get pod/{resourceName} -n {namespace}', 'probe 변경 전 path, port, delay, timeout 값을 기록하고 되돌립니다.', 'READ_ONLY', 1, true),
('RB-ENDPOINT', 'EndpointUnavailable', 'TRAFFIC', 'Service', 'Service endpoint diagnosis', 'Service selector와 Pod label 또는 targetPort가 일치하지 않아 트래픽 대상이 없을 수 있습니다.', 'kubectl get service/{resourceName} -n {namespace}', 'selector, service port, targetPort를 확인할 수 있습니다.', null, 'kubectl get endpoints/{resourceName} -n {namespace}', 'selector와 port 변경 전 값을 기록하고 원래 Service spec으로 복원합니다.', 'READ_ONLY', 1, true),
('RB-PORT', 'PortMismatch', 'TRAFFIC', 'Service', 'Port mismatch diagnosis', 'Service targetPort, containerPort, probe port와 애플리케이션 실제 listen port를 함께 비교해야 합니다.', 'kubectl get service/{resourceName} -n {namespace}', 'Service port와 targetPort가 표시됩니다.', null, 'kubectl get endpoints/{resourceName} -n {namespace}', '변경 전 Service와 workload port 설정으로 되돌립니다.', 'READ_ONLY', 1, true),
('RB-PVC', 'FailedBinding', 'STORAGE', 'PersistentVolumeClaim', 'PVC pending diagnosis', 'StorageClass, 요청 용량, access mode와 사용 가능한 PV를 확인해야 합니다.', 'kubectl describe pvc/{resourceName} -n {namespace}', 'binding 실패 원인과 StorageClass event가 표시됩니다.', null, 'kubectl get pvc/{resourceName} -n {namespace}', 'PVC 재생성은 데이터 위험이 있으므로 자동 조치하지 않고 storage 운영 절차를 따릅니다.', 'READ_ONLY', 1, true),
('RB-SCHEDULING', 'FailedScheduling', 'CAPACITY', 'Pod', 'Scheduling failure diagnosis', 'node 자원 예약, taint/toleration, affinity, PVC binding 조건을 순서대로 확인해야 합니다.', 'kubectl describe pod/{resourceName} -n {namespace}', 'scheduler가 선택 가능한 node를 찾지 못한 이유가 표시됩니다.', null, 'kubectl get pod/{resourceName} -n {namespace}', 'requests나 scheduling constraint 변경 전 값을 기록합니다.', 'READ_ONLY', 1, true),
('RB-ROLLOUT', 'ProgressDeadlineExceeded', 'ROLLOUT', 'Deployment', 'Rollout failure diagnosis', '새 ReplicaSet의 Pod 준비 실패와 Deployment condition을 확인해야 합니다.', 'kubectl describe deployment/{resourceName} -n {namespace}', 'Deployment condition과 새 ReplicaSet 상태가 표시됩니다.', null, 'kubectl get deployment/{resourceName} -n {namespace}', '명시적 revision rollback guard를 통과한 경우에만 이전 revision으로 되돌립니다.', 'CHANGE_REQUIRES_REVIEW', 1, true);
