alter table analysis_sessions add column async_job_id uuid;

create index idx_analysis_sessions_async_job_id on analysis_sessions(async_job_id);
