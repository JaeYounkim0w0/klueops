create table ai_provider_profiles (
    id uuid primary key,
    tenant_id uuid references tenants(id),
    name varchar(255) not null,
    provider_type varchar(40) not null,
    base_url varchar(1000),
    credential_ciphertext text,
    credential_key_id varchar(100),
    credential_algorithm varchar(50),
    credential_nonce varchar(255),
    default_model varchar(255) not null,
    allowed_models_json text not null,
    enabled boolean not null,
    external_data_transfer boolean not null,
    validation_status varchar(40) not null,
    last_validated_at timestamp with time zone,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_ai_provider_profile_owner_name unique (tenant_id, name)
);
create index idx_ai_provider_profiles_owner_enabled on ai_provider_profiles(tenant_id, enabled, name);

create table tenant_ai_routing_policies (
    tenant_id uuid not null references tenants(id),
    purpose varchar(40) not null,
    primary_profile_id uuid not null references ai_provider_profiles(id),
    model varchar(255) not null,
    fallback_profile_id uuid references ai_provider_profiles(id),
    fallback_model varchar(255),
    external_transfer_allowed boolean not null,
    maximum_context_chars integer not null,
    maximum_output_tokens integer not null,
    updated_by varchar(255) not null,
    updated_at timestamp with time zone not null,
    primary key (tenant_id, purpose)
);

create table local_ai_models (
    id uuid primary key,
    provider_profile_id uuid not null references ai_provider_profiles(id),
    model_tag varchar(255) not null,
    parameter_billions numeric(4,1),
    status varchar(40) not null,
    size_bytes bigint,
    digest varchar(255),
    updated_at timestamp with time zone not null,
    constraint uk_local_ai_models_profile_tag unique (provider_profile_id, model_tag)
);

insert into ai_provider_profiles(id,tenant_id,name,provider_type,base_url,default_model,allowed_models_json,
  enabled,external_data_transfer,validation_status,created_by,created_at,updated_at)
values ('00000000-0000-0000-0000-000000000032',null,'Local Ollama','OLLAMA','http://ollama:11434',
  'qwen2.5-coder:7b','["qwen2.5-coder:7b","qwen3.5:9b","granite3.3:8b","qwen3:8b","llama3.1:8b"]',
  true,false,'NOT_VALIDATED','system',current_timestamp,current_timestamp);
