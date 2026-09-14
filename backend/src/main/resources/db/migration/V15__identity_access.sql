create table aiops_users (
    id uuid primary key,
    issuer varchar(500) not null,
    subject varchar(255) not null,
    username varchar(255) not null,
    display_name varchar(255) not null,
    email varchar(320),
    active boolean not null,
    first_seen_at timestamp with time zone not null,
    last_login_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_aiops_users_identity unique (issuer, subject)
);

create index idx_aiops_users_active_username on aiops_users(active, username);

create table role_bindings (
    id uuid primary key,
    principal_type varchar(30) not null,
    principal_key varchar(255) not null,
    role_name varchar(50) not null,
    scope_type varchar(30) not null,
    cluster_id uuid,
    namespace varchar(255),
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    constraint chk_role_binding_scope check (
        (scope_type = 'PLATFORM' and cluster_id is null and namespace is null)
        or (scope_type = 'CLUSTER' and cluster_id is not null and namespace is null)
        or (scope_type = 'NAMESPACE' and cluster_id is not null and namespace is not null)
    )
);

create index idx_role_bindings_principal on role_bindings(principal_key, principal_type);
create index idx_role_bindings_scope on role_bindings(cluster_id, namespace, scope_type);

