create table command_executions (
    id uuid primary key,
    cluster_id uuid not null,
    namespace varchar(255),
    command_text text not null,
    argv_json text not null,
    safety varchar(50) not null,
    status varchar(50) not null,
    stdout_text text,
    stderr_text text,
    exit_code integer,
    duration_ms bigint,
    truncated boolean not null,
    created_by varchar(255) not null,
    request_id varchar(255),
    created_at timestamp with time zone not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    constraint fk_command_execution_cluster foreign key (cluster_id) references clusters(id) on delete cascade
);

create index idx_command_executions_cluster_created on command_executions(cluster_id, created_at desc);
create index idx_command_executions_actor_created on command_executions(created_by, created_at desc);

create table command_favorites (
    id uuid primary key,
    cluster_id uuid not null,
    owner_user_id varchar(255) not null,
    name varchar(80) not null,
    description varchar(300),
    command_text text not null,
    namespace varchar(255),
    shared boolean not null,
    sort_order integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_command_favorite_cluster foreign key (cluster_id) references clusters(id) on delete cascade
);

create index idx_command_favorites_cluster_owner on command_favorites(cluster_id, owner_user_id, sort_order);
