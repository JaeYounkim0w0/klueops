alter table command_executions
    add column verification_status varchar(40) not null default 'NOT_REQUIRED',
    add column verification_summary text,
    add column before_snapshot text,
    add column after_snapshot text,
    add column rollback_command text,
    add column verified_at timestamp with time zone;

update command_executions
set verification_status = 'NOT_REQUIRED'
where verification_status is null;

create index idx_command_executions_verification
    on command_executions(cluster_id, verification_status, created_at desc);
