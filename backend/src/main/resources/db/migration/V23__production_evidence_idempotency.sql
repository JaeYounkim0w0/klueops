create table production_evidence_import_keys (
    import_key varchar(128) primary key,
    run_id uuid not null unique,
    created_at timestamp with time zone not null,
    constraint fk_production_evidence_import_run foreign key (run_id)
        references production_evidence_runs(id) on delete cascade
);

create index idx_production_evidence_import_created
    on production_evidence_import_keys(created_at desc);
