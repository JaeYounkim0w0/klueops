create table sync_jobs (
    id uuid primary key,
    async_job_id uuid not null,
    cluster_id uuid not null,
    sync_type varchar(50) not null,
    status varchar(50) not null,
    requested_by varchar(255) not null,
    resource_count integer not null,
    event_count integer not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    error_message varchar(1000),
    created_at timestamp with time zone not null
);

create unique index idx_sync_jobs_async_job_id on sync_jobs(async_job_id);
create index idx_sync_jobs_cluster_created_at on sync_jobs(cluster_id, created_at);

create table kubernetes_resource_snapshots (
    id uuid primary key,
    cluster_id uuid not null,
    sync_job_id uuid not null,
    namespace varchar(255),
    resource_type varchar(100) not null,
    resource_name varchar(255) not null,
    resource_uid varchar(255),
    status varchar(100),
    summary_json text not null,
    raw_json text,
    truncated boolean not null,
    collected_at timestamp with time zone not null
);

create index idx_kubernetes_resource_snapshots_cluster_type on kubernetes_resource_snapshots(cluster_id, resource_type);
create index idx_kubernetes_resource_snapshots_namespace on kubernetes_resource_snapshots(cluster_id, namespace);
create index idx_kubernetes_resource_snapshots_sync_job on kubernetes_resource_snapshots(sync_job_id);

create table kubernetes_event_snapshots (
    id uuid primary key,
    cluster_id uuid not null,
    sync_job_id uuid not null,
    namespace varchar(255),
    involved_kind varchar(100),
    involved_name varchar(255),
    reason varchar(255),
    type varchar(50),
    message varchar(1000),
    event_time timestamp with time zone,
    count integer,
    collected_at timestamp with time zone not null
);

create index idx_kubernetes_event_snapshots_cluster_event_time on kubernetes_event_snapshots(cluster_id, event_time);
create index idx_kubernetes_event_snapshots_sync_job on kubernetes_event_snapshots(sync_job_id);
