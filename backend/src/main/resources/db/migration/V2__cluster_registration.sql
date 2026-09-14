create table clusters (
    id uuid primary key,
    name varchar(255) not null,
    description varchar(1000),
    environment varchar(64) not null,
    provider varchar(64) not null,
    region varchar(128),
    status varchar(64) not null,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table cluster_credentials (
    id uuid primary key,
    cluster_id uuid not null references clusters(id),
    credential_type varchar(64) not null,
    encrypted_payload text not null,
    key_id varchar(128) not null,
    algorithm varchar(128) not null,
    nonce varchar(255) not null,
    created_at timestamp with time zone not null
);

create table cluster_sync_settings (
    id uuid primary key,
    cluster_id uuid not null references clusters(id),
    auto_sync_enabled boolean not null,
    sync_interval_seconds integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index idx_clusters_name on clusters(name);
create unique index idx_cluster_credentials_cluster_id on cluster_credentials(cluster_id);
create unique index idx_cluster_sync_settings_cluster_id on cluster_sync_settings(cluster_id);
create index idx_clusters_status_created_at on clusters(status, created_at);

