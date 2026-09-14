create table tenants (
    id uuid primary key,
    code varchar(63) not null,
    name varchar(255) not null,
    description varchar(1000),
    status varchar(30) not null,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_tenants_code unique (code)
);

create table workspaces (
    id uuid primary key,
    tenant_id uuid not null,
    code varchar(63) not null,
    name varchar(255) not null,
    description varchar(1000),
    status varchar(30) not null,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_workspaces_tenant foreign key (tenant_id) references tenants(id),
    constraint uk_workspaces_tenant_code unique (tenant_id, code),
    constraint uk_workspaces_id_tenant unique (id, tenant_id)
);

insert into tenants (id, code, name, description, status, created_by, created_at, updated_at)
values ('00000000-0000-0000-0000-000000000001', 'default', 'Default Tenant',
        'Migrated platform data', 'ACTIVE', 'system', current_timestamp, current_timestamp);

insert into workspaces (id, tenant_id, code, name, description, status, created_by, created_at, updated_at)
values ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001',
        'default', 'Default Workspace', 'Migrated cluster placement', 'ACTIVE', 'system', current_timestamp, current_timestamp);

alter table clusters add column tenant_id uuid;
alter table clusters add column workspace_id uuid;

update clusters
set tenant_id = '00000000-0000-0000-0000-000000000001',
    workspace_id = '00000000-0000-0000-0000-000000000002';

alter table clusters alter column tenant_id set not null;
alter table clusters alter column workspace_id set not null;
alter table clusters add constraint fk_clusters_tenant foreign key (tenant_id) references tenants(id);
alter table clusters add constraint fk_clusters_workspace_tenant foreign key (workspace_id, tenant_id) references workspaces(id, tenant_id);

drop index idx_clusters_name;
create unique index idx_clusters_tenant_name on clusters(tenant_id, name);
create index idx_clusters_tenant_workspace_created on clusters(tenant_id, workspace_id, created_at);

alter table role_bindings add column tenant_id uuid;
alter table role_bindings add column workspace_id uuid;
alter table role_bindings drop constraint chk_role_binding_scope;
alter table role_bindings add constraint chk_role_binding_scope check (
    (scope_type = 'PLATFORM' and tenant_id is null and workspace_id is null and cluster_id is null and namespace is null)
    or (scope_type = 'TENANT' and tenant_id is not null and workspace_id is null and cluster_id is null and namespace is null)
    or (scope_type = 'WORKSPACE' and tenant_id is not null and workspace_id is not null and cluster_id is null and namespace is null)
    or (scope_type = 'CLUSTER' and tenant_id is null and workspace_id is null and cluster_id is not null and namespace is null)
    or (scope_type = 'NAMESPACE' and tenant_id is null and workspace_id is null and cluster_id is not null and namespace is not null)
);
create index idx_role_bindings_tenant_workspace on role_bindings(tenant_id, workspace_id, scope_type);

