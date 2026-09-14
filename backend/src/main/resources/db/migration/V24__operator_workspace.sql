create table incident_collaboration (
    incident_id uuid primary key,
    assignee varchar(255),
    tags_json text not null default '[]',
    acknowledge_due_at timestamp with time zone,
    resolve_due_at timestamp with time zone,
    updated_by varchar(255) not null,
    updated_at timestamp with time zone not null,
    constraint fk_incident_collaboration_incident foreign key (incident_id) references incidents(id) on delete cascade
);

create table incident_links (
    incident_id uuid not null,
    related_incident_id uuid not null,
    relation_type varchar(30) not null,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    primary key (incident_id, related_incident_id),
    constraint ck_incident_link_self check (incident_id <> related_incident_id),
    constraint fk_incident_link_source foreign key (incident_id) references incidents(id) on delete cascade,
    constraint fk_incident_link_target foreign key (related_incident_id) references incidents(id) on delete cascade
);

create index idx_incident_links_related on incident_links(related_incident_id, created_at);

create table custom_runbooks (
    id varchar(100) primary key,
    signal varchar(100) not null,
    category varchar(100) not null,
    resource_kind varchar(100),
    title varchar(255) not null,
    beginner_explanation varchar(2000) not null,
    verification_command varchar(2000) not null,
    expected_result varchar(2000) not null,
    safe_action varchar(2000),
    validation_command varchar(2000) not null,
    rollback_guidance varchar(2000),
    safety_level varchar(30) not null,
    version integer not null,
    enabled boolean not null,
    owner varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_custom_runbooks_category on custom_runbooks(category, enabled, updated_at);

create table custom_runbook_versions (
    id uuid primary key,
    runbook_id varchar(100) not null,
    version integer not null,
    snapshot_json text not null,
    change_note varchar(1000),
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    constraint uk_custom_runbook_version unique (runbook_id, version),
    constraint fk_custom_runbook_version foreign key (runbook_id) references custom_runbooks(id) on delete cascade
);

create index idx_custom_runbook_versions_runbook on custom_runbook_versions(runbook_id, version desc);

