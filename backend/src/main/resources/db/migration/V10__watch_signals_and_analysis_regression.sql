create table kubernetes_watch_signals (
    id uuid primary key,
    cluster_id uuid not null,
    namespace varchar(255),
    resource_kind varchar(100) not null,
    resource_name varchar(255) not null,
    action varchar(30) not null,
    reason varchar(255),
    status varchar(255),
    summary varchar(2000),
    observed_at timestamp with time zone not null,
    constraint fk_watch_signal_cluster foreign key (cluster_id) references clusters(id) on delete cascade
);

create index idx_watch_signals_scope
    on kubernetes_watch_signals(cluster_id, namespace, observed_at);

create table analysis_regression_runs (
    id uuid primary key,
    status varchar(30) not null,
    passed_cases integer not null,
    total_cases integer not null,
    score double precision not null,
    baseline_version varchar(100) not null,
    triggered_by varchar(255) not null,
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone
);

create table analysis_regression_case_results (
    id uuid primary key,
    run_id uuid not null,
    case_id varchar(100) not null,
    title varchar(255) not null,
    category varchar(100) not null,
    status varchar(30) not null,
    score integer not null,
    assertions_json varchar(8000) not null,
    failures_json varchar(8000) not null,
    duration_ms bigint not null,
    constraint fk_regression_case_run foreign key (run_id) references analysis_regression_runs(id) on delete cascade
);

create index idx_analysis_regression_runs_completed
    on analysis_regression_runs(completed_at);
