create table managed_applications (
    id uuid primary key,
    cluster_id uuid not null references clusters(id),
    namespace varchar(255) not null,
    name varchar(255) not null,
    deployment_type varchar(64) not null,
    image varchar(500),
    helm_release_name varchar(255),
    helm_chart varchar(500),
    status varchar(64) not null,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    last_synced_at timestamp with time zone,
    last_sync_status varchar(64),
    last_sync_error varchar(1000)
);

create index idx_managed_applications_cluster_namespace on managed_applications(cluster_id, namespace);
create index idx_managed_applications_status_created_at on managed_applications(status, created_at);
create unique index idx_managed_applications_cluster_namespace_name on managed_applications(cluster_id, namespace, name);

create table analysis_sessions (
    id uuid primary key,
    cluster_id uuid not null references clusters(id),
    application_id uuid,
    namespace varchar(255),
    status varchar(64) not null,
    ai_provider varchar(64) not null,
    ai_model varchar(255) not null,
    prompt_version varchar(64) not null,
    schema_version varchar(64) not null,
    result_summary varchar(1000),
    result_json text,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null
);

create index idx_analysis_sessions_cluster_created_at on analysis_sessions(cluster_id, created_at);
create index idx_analysis_sessions_application_created_at on analysis_sessions(application_id, created_at);
create index idx_analysis_sessions_status_created_at on analysis_sessions(status, created_at);
