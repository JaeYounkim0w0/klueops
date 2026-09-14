create table production_evidence_runs (
    id uuid primary key,
    release_name varchar(255) not null,
    environment_name varchar(255) not null,
    state varchar(32) not null,
    triggered_by varchar(255) not null,
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    expires_at timestamp with time zone
);

create index idx_production_evidence_runs_recent
    on production_evidence_runs(started_at desc);

create table production_evidence_checks (
    id uuid primary key,
    run_id uuid not null,
    category varchar(64) not null,
    code varchar(128) not null,
    state varchar(32) not null,
    title varchar(255) not null,
    detail text,
    observed_value text,
    action text,
    duration_ms bigint not null,
    checked_at timestamp with time zone not null,
    constraint fk_production_evidence_check_run foreign key (run_id)
        references production_evidence_runs(id) on delete cascade
);

create index idx_production_evidence_checks_run
    on production_evidence_checks(run_id, checked_at);

create table production_evidence_artifacts (
    id uuid primary key,
    check_id uuid not null,
    file_name varchar(512) not null,
    media_type varchar(255) not null,
    checksum varchar(64) not null,
    size_bytes bigint not null,
    reference_value text,
    created_at timestamp with time zone not null,
    constraint fk_production_evidence_artifact_check foreign key (check_id)
        references production_evidence_checks(id) on delete cascade
);

create index idx_production_evidence_artifacts_check
    on production_evidence_artifacts(check_id, created_at);
