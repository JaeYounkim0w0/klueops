-- Values 생성 원문과 결과는 application 암호화 후 저장한다.
create table values_assistance_jobs (
    job_id uuid primary key references async_jobs(id),
    tenant_id uuid not null,
    actor varchar(255) not null,
    chart_version_id uuid not null,
    request_payload text,
    result_payload text
);
create index idx_values_assistance_owner on values_assistance_jobs(tenant_id, actor);
