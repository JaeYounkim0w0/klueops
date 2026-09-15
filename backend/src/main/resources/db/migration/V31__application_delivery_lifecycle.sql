create table deployment_plans (
    id uuid primary key,
    cluster_id uuid not null references clusters(id),
    chart_version_id uuid not null references chart_versions(id),
    values_revision_id uuid references values_revisions(id),
    namespace varchar(253) not null,
    release_name varchar(53) not null,
    exposure_type varchar(30) not null,
    hostname varchar(253),
    rendered_manifest text not null,
    manifest_sha256 varchar(64) not null,
    warnings_json text not null,
    confirmation_text varchar(500) not null,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone
);
create index idx_deployment_plans_cluster_expiry on deployment_plans(cluster_id, expires_at desc);

create table application_releases (
    id uuid primary key,
    application_id uuid not null references managed_applications(id),
    revision integer not null,
    chart_version_id uuid not null references chart_versions(id),
    values_revision_id uuid references values_revisions(id),
    manifest_sha256 varchar(64) not null,
    status varchar(40) not null,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    constraint uk_application_releases_revision unique (application_id, revision)
);

create table release_operations (
    id uuid primary key,
    application_id uuid not null references managed_applications(id),
    async_job_id uuid references async_jobs(id),
    operation_type varchar(40) not null,
    status varchar(40) not null,
    release_revision integer,
    output_summary text,
    error_message varchar(2000),
    requested_by varchar(255) not null,
    requested_at timestamp with time zone not null,
    completed_at timestamp with time zone
);
create index idx_release_operations_application on release_operations(application_id, requested_at desc);

create table application_endpoints (
    id uuid primary key,
    application_id uuid not null references managed_applications(id),
    endpoint_type varchar(40) not null,
    url varchar(1000),
    hostname varchar(253),
    status varchar(40) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_application_endpoints_type unique (application_id, endpoint_type, hostname)
);

alter table managed_applications add column current_release_revision integer;
alter table managed_applications add column chart_version_id uuid references chart_versions(id);
alter table managed_applications add column values_revision_id uuid references values_revisions(id);
alter table managed_applications add column archived_at timestamp with time zone;
