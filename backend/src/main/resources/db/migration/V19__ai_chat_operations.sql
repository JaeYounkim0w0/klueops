alter table ai_chat_conversations add column chat_mode varchar(32) default 'GENERAL' not null;
alter table ai_chat_conversations add column favorite boolean default false not null;

update ai_chat_conversations
set chat_mode = 'CLUSTER'
where cluster_id is not null or application_id is not null;

create index idx_ai_chat_conversations_owner_state
    on ai_chat_conversations(created_by, archived_at, favorite, updated_at);
