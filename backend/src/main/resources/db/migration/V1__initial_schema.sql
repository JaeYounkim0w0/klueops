create table async_jobs (
    id uuid primary key,
    type varchar(64) not null,
    status varchar(64) not null,
    created_at timestamp with time zone not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    error_code varchar(128),
    error_message varchar(1000)
);

create table audit_logs (
    id uuid primary key,
    action varchar(128) not null,
    target_type varchar(128) not null,
    target_id varchar(128) not null,
    actor varchar(255) not null,
    request_id varchar(128),
    created_at timestamp with time zone not null
);

create index idx_async_jobs_status_created_at on async_jobs(status, created_at);
create index idx_audit_logs_target_created_at on audit_logs(target_type, target_id, created_at);
create index idx_audit_logs_actor_created_at on audit_logs(actor, created_at);
