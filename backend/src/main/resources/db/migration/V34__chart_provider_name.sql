alter table tenant_charts add column provider_name varchar(255);

-- 기존 Chart도 제공사를 식별할 수 있도록 저장소 식별자를 안전한 fallback으로 사용한다.
update tenant_charts
set provider_name = source_name
where provider_name is null and source_name is not null;
