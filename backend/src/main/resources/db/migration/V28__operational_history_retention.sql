alter table operation_settings
    add column audit_retention_days integer not null default 365,
    add column command_retention_days integer not null default 90;

create index if not exists idx_audit_logs_created_at on audit_logs(created_at);
create index if not exists idx_command_executions_created_at on command_executions(created_at);
