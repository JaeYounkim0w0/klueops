create table analysis_command_executions (
    id uuid primary key,
    analysis_id uuid not null,
    cluster_id uuid not null,
    namespace varchar(255),
    command_text text not null,
    safety varchar(50) not null,
    status varchar(50) not null,
    reason varchar(1000),
    stdout_text text,
    stderr_text text,
    exit_code integer,
    duration_ms bigint,
    created_by varchar(255) not null,
    created_at timestamp not null,
    constraint fk_analysis_command_execution_analysis foreign key (analysis_id) references analysis_sessions(id) on delete cascade,
    constraint fk_analysis_command_execution_cluster foreign key (cluster_id) references clusters(id) on delete cascade
);

create index idx_analysis_command_executions_analysis_id on analysis_command_executions(analysis_id);
create index idx_analysis_command_executions_created_at on analysis_command_executions(created_at);

create table analysis_workflow_states (
    id uuid primary key,
    analysis_id uuid not null,
    issue_group_id varchar(255) not null,
    status varchar(50) not null,
    note varchar(1000),
    updated_by varchar(255) not null,
    updated_at timestamp not null,
    constraint uk_analysis_workflow_state unique (analysis_id, issue_group_id),
    constraint fk_analysis_workflow_state_analysis foreign key (analysis_id) references analysis_sessions(id) on delete cascade
);

create index idx_analysis_workflow_states_analysis_id on analysis_workflow_states(analysis_id);
