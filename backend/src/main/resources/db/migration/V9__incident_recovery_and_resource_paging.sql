create table incident_recovery_observations (
    incident_id uuid primary key,
    consecutive_healthy_count integer not null,
    first_healthy_at timestamp with time zone,
    last_observed_at timestamp with time zone not null,
    last_observed_status varchar(255),
    constraint fk_incident_recovery_incident foreign key (incident_id) references incidents(id) on delete cascade
);

create index idx_kubernetes_resource_snapshots_sync_scope
    on kubernetes_resource_snapshots(sync_job_id, namespace, resource_type, resource_name);

create index idx_kubernetes_resource_snapshots_sync_status
    on kubernetes_resource_snapshots(sync_job_id, status);
