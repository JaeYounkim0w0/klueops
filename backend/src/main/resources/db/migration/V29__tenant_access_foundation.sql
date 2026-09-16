create table tenant_memberships (
    id uuid primary key,
    tenant_id uuid not null,
    user_id uuid,
    pending_issuer varchar(500),
    pending_subject varchar(255),
    pending_email varchar(320),
    role_name varchar(50) not null,
    scope_type varchar(30) not null,
    workspace_id uuid,
    cluster_id uuid,
    namespace varchar(255),
    status varchar(30) not null,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    suspended_at timestamp with time zone,
    offboarded_at timestamp with time zone,
    constraint fk_tenant_memberships_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_tenant_memberships_user foreign key (user_id) references aiops_users(id),
    constraint fk_tenant_memberships_workspace_tenant foreign key (workspace_id, tenant_id) references workspaces(id, tenant_id),
    constraint fk_tenant_memberships_cluster foreign key (cluster_id) references clusters(id),
    constraint chk_tenant_membership_identity check (
        user_id is not null or pending_subject is not null or pending_email is not null
    ),
    constraint chk_tenant_membership_scope check (
        (scope_type = 'TENANT' and workspace_id is null and cluster_id is null and namespace is null)
        or (scope_type = 'WORKSPACE' and workspace_id is not null and cluster_id is null and namespace is null)
        or (scope_type = 'CLUSTER' and workspace_id is null and cluster_id is not null and namespace is null)
        or (scope_type = 'NAMESPACE' and workspace_id is null and cluster_id is not null and namespace is not null)
    )
);

create unique index uk_tenant_memberships_user
    on tenant_memberships(tenant_id, user_id) where user_id is not null;
create index idx_tenant_memberships_tenant_status
    on tenant_memberships(tenant_id, status, created_at desc);
create index idx_tenant_memberships_pending_identity
    on tenant_memberships(pending_issuer, pending_subject, pending_email);

create table tenant_feature_policies (
    id uuid primary key,
    tenant_id uuid not null,
    feature_key varchar(80) not null,
    enabled boolean not null,
    updated_by varchar(255) not null,
    updated_at timestamp with time zone not null,
    constraint fk_tenant_feature_policies_tenant foreign key (tenant_id) references tenants(id),
    constraint uk_tenant_feature_policies unique (tenant_id, feature_key)
);

create index idx_tenant_feature_policies_enabled
    on tenant_feature_policies(tenant_id, enabled);

create table oidc_group_mappings (
    id uuid primary key,
    issuer varchar(500) not null,
    group_value varchar(255) not null,
    tenant_id uuid not null,
    role_name varchar(50) not null,
    scope_type varchar(30) not null,
    workspace_id uuid,
    cluster_id uuid,
    namespace varchar(255),
    active boolean not null,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_oidc_group_mappings_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_oidc_group_mappings_workspace_tenant foreign key (workspace_id, tenant_id) references workspaces(id, tenant_id),
    constraint fk_oidc_group_mappings_cluster foreign key (cluster_id) references clusters(id),
    constraint chk_oidc_group_mapping_scope check (
        (scope_type = 'TENANT' and workspace_id is null and cluster_id is null and namespace is null)
        or (scope_type = 'WORKSPACE' and workspace_id is not null and cluster_id is null and namespace is null)
        or (scope_type = 'CLUSTER' and workspace_id is null and cluster_id is not null and namespace is null)
        or (scope_type = 'NAMESPACE' and workspace_id is null and cluster_id is not null and namespace is not null)
    ),
    constraint uk_oidc_group_mapping unique (
        issuer, group_value, tenant_id, role_name, scope_type,
        workspace_id, cluster_id, namespace
    )
);

create index idx_oidc_group_mappings_lookup
    on oidc_group_mappings(issuer, group_value, active);
create index idx_oidc_group_mappings_tenant
    on oidc_group_mappings(tenant_id, active, created_at desc);
