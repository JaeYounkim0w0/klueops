create table watch_signal_groups (
    id uuid primary key,
    fingerprint varchar(64) not null unique,
    cluster_id uuid not null,
    namespace varchar(255),
    resource_kind varchar(100) not null,
    resource_name varchar(255) not null,
    category varchar(100) not null,
    severity varchar(20) not null,
    state varchar(30) not null,
    reason varchar(255),
    summary varchar(4000),
    occurrence_count integer not null default 1,
    first_observed_at timestamp with time zone not null,
    last_observed_at timestamp with time zone not null,
    incident_id uuid,
    updated_by varchar(255) not null,
    updated_at timestamp with time zone not null,
    constraint fk_watch_signal_group_cluster foreign key (cluster_id) references clusters(id) on delete cascade,
    constraint fk_watch_signal_group_incident foreign key (incident_id) references incidents(id) on delete set null
);

create index idx_watch_signal_group_queue
    on watch_signal_groups(state, severity, last_observed_at);
create index idx_watch_signal_group_scope
    on watch_signal_groups(cluster_id, namespace, last_observed_at);

alter table analysis_feedback add column actual_root_cause varchar(2000);
alter table analysis_feedback add column actual_resolution varchar(2000);
alter table analysis_feedback add column validated_resource_kind varchar(100);
alter table analysis_feedback add column validated_resource_name varchar(255);
alter table analysis_feedback add column confidence_expectation varchar(20);
