create table watch_continuity (
    cluster_id uuid primary key,
    pod_resource_version varchar(255),
    event_resource_version varchar(255),
    continuity_state varchar(30) not null,
    gap_signal_count integer not null default 0,
    last_reconciled_at timestamp with time zone,
    last_error varchar(1800),
    updated_at timestamp with time zone not null,
    constraint fk_watch_continuity_cluster foreign key (cluster_id) references clusters(id) on delete cascade
);

create table signal_noise_policies (
    id uuid primary key,
    name varchar(255) not null,
    cluster_id uuid,
    namespace_pattern varchar(255),
    severity_floor varchar(20) not null,
    repeat_threshold integer not null,
    maintenance_start timestamp with time zone,
    maintenance_end timestamp with time zone,
    snooze_until timestamp with time zone,
    enabled boolean not null,
    updated_by varchar(255) not null,
    updated_at timestamp with time zone not null,
    constraint fk_signal_noise_policy_cluster foreign key (cluster_id) references clusters(id) on delete cascade
);

create index idx_signal_noise_policy_scope
    on signal_noise_policies(cluster_id, enabled, maintenance_start, maintenance_end);

create table remediation_observations (
    id uuid primary key,
    incident_id uuid not null,
    analysis_id uuid,
    command_execution_id uuid,
    state varchar(30) not null,
    observation_seconds integer not null,
    started_at timestamp with time zone not null,
    observe_until timestamp with time zone not null,
    baseline_json text not null,
    latest_json text,
    conclusion varchar(2000),
    rollback_candidate varchar(2000),
    updated_by varchar(255) not null,
    updated_at timestamp with time zone not null,
    constraint fk_remediation_observation_incident foreign key (incident_id) references incidents(id) on delete cascade,
    constraint fk_remediation_observation_analysis foreign key (analysis_id) references analysis_sessions(id) on delete set null,
    constraint fk_remediation_observation_command foreign key (command_execution_id) references analysis_command_executions(id) on delete set null
);

create index idx_remediation_observation_incident
    on remediation_observations(incident_id, started_at);
create index idx_remediation_observation_state
    on remediation_observations(state, observe_until);

create table ai_release_gates (
    id uuid primary key,
    candidate_version varchar(255) not null,
    baseline_version varchar(255) not null,
    state varchar(30) not null,
    regression_score double precision not null,
    minimum_regression_score double precision not null,
    ground_truth_samples integer not null,
    minimum_ground_truth_samples integer not null,
    verified_accuracy double precision not null,
    minimum_verified_accuracy double precision not null,
    dangerous_suggestion_count integer not null,
    reasons_json text not null,
    evaluated_at timestamp with time zone not null,
    evaluated_by varchar(255) not null
);

create index idx_ai_release_gate_evaluated
    on ai_release_gates(evaluated_at);

create table incident_postmortems (
    incident_id uuid primary key,
    title varchar(500) not null,
    impact varchar(2000) not null,
    root_cause varchar(4000) not null,
    resolution varchar(4000) not null,
    evidence_json text not null,
    prevention_json text not null,
    generated_at timestamp with time zone not null,
    generated_by varchar(255) not null,
    constraint fk_incident_postmortem_incident foreign key (incident_id) references incidents(id) on delete cascade
);
