alter table local_ai_models add column if not exists evaluation_score integer;
alter table local_ai_models add column if not exists evaluation_samples integer;
alter table local_ai_models add column if not exists average_latency_ms bigint;
alter table local_ai_models add column if not exists evaluated_at timestamp with time zone;
alter table local_ai_models add column if not exists promoted boolean not null default false;
