create table command_execution_leases (
    execution_id uuid primary key,
    cluster_id uuid not null,
    actor varchar(255) not null,
    mode varchar(30) not null,
    acquired_at timestamp with time zone not null,
    expires_at timestamp with time zone not null
);

alter table command_executions
    add column source_analysis_id uuid references analysis_sessions(id) on delete set null;

create index idx_command_executions_source_analysis
    on command_executions(source_analysis_id, created_at desc);

create index idx_command_execution_leases_actor_mode_expiry
    on command_execution_leases(actor, mode, expires_at);
create index idx_command_execution_leases_cluster_mode_expiry
    on command_execution_leases(cluster_id, mode, expires_at);

create table command_execution_rate_events (
    id bigserial primary key,
    execution_id uuid not null,
    cluster_id uuid not null,
    actor varchar(255) not null,
    mode varchar(30) not null,
    created_at timestamp with time zone not null
);

create index idx_command_execution_rate_actor_created
    on command_execution_rate_events(actor, created_at);
create index idx_command_execution_rate_cluster_created
    on command_execution_rate_events(cluster_id, created_at);
