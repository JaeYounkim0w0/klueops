create table chart_sources (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    source_type varchar(40) not null,
    name varchar(255) not null,
    endpoint varchar(1000) not null,
    credential_ciphertext text,
    credential_key_id varchar(100),
    credential_algorithm varchar(50),
    credential_nonce varchar(255),
    tls_policy varchar(30) not null default 'STRICT',
    enabled boolean not null,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_chart_sources_tenant_name unique (tenant_id, name)
);
create index idx_chart_sources_tenant_enabled on chart_sources(tenant_id, enabled, name);

create table chart_artifacts (
    id uuid primary key,
    digest_sha256 varchar(64) not null unique,
    payload bytea not null,
    size_bytes bigint not null,
    created_at timestamp with time zone not null
);

create table tenant_charts (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    name varchar(255) not null,
    description varchar(2000),
    source_type varchar(40) not null,
    source_name varchar(255),
    repository_url varchar(1000),
    package_name varchar(255) not null,
    trust_status varchar(40) not null,
    archived_at timestamp with time zone,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_tenant_charts_coordinate unique (tenant_id, source_type, source_name, package_name)
);
create index idx_tenant_charts_tenant_active on tenant_charts(tenant_id, archived_at, updated_at desc);

create table chart_versions (
    id uuid primary key,
    tenant_chart_id uuid not null references tenant_charts(id),
    chart_version varchar(100) not null,
    app_version varchar(100),
    source_reference varchar(1200) not null,
    digest_sha256 varchar(64) not null,
    provenance_status varchar(40) not null,
    artifact_id uuid not null references chart_artifacts(id),
    metadata_json text not null,
    imported_by varchar(255) not null,
    imported_at timestamp with time zone not null,
    constraint uk_chart_versions_chart_version unique (tenant_chart_id, chart_version)
);
create index idx_chart_versions_chart_imported on chart_versions(tenant_chart_id, imported_at desc);

create table values_profiles (
    id uuid primary key,
    tenant_id uuid not null references tenants(id),
    chart_version_id uuid not null references chart_versions(id),
    name varchar(255) not null,
    description varchar(1000),
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_values_profiles_tenant_chart_name unique (tenant_id, chart_version_id, name)
);
create index idx_values_profiles_tenant_chart on values_profiles(tenant_id, chart_version_id, updated_at desc);

create table values_revisions (
    id uuid primary key,
    profile_id uuid not null references values_profiles(id),
    revision integer not null,
    values_ciphertext text not null,
    values_key_id varchar(100) not null,
    values_algorithm varchar(50) not null,
    values_nonce varchar(255) not null,
    values_sha256 varchar(64) not null,
    parent_revision integer,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    constraint uk_values_revisions_profile_revision unique (profile_id, revision)
);
create index idx_values_revisions_profile_created on values_revisions(profile_id, revision desc);
