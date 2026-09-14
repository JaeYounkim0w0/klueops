create table live_validation_runs (
    id uuid primary key,
    cluster_id uuid not null,
    cluster_name varchar(255) not null,
    scenario_id varchar(100) not null,
    namespace varchar(255) not null,
    state varchar(30) not null,
    safety_mode varchar(30) not null,
    ttl_seconds integer not null,
    safety_checks_json text not null,
    resources_json text not null,
    observed_signal varchar(1000),
    detail varchar(3000),
    cleanup_required boolean not null,
    started_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    triggered_by varchar(255) not null,
    constraint fk_live_validation_cluster foreign key (cluster_id) references clusters(id) on delete cascade
);

create index idx_live_validation_expiry
    on live_validation_runs(cleanup_required, expires_at);

create index idx_live_validation_scope
    on live_validation_runs(cluster_id, started_at);
