create table ai_chat_conversations (
    id uuid primary key,
    title varchar(255) not null,
    cluster_id uuid references clusters(id),
    namespace varchar(255),
    application_id uuid,
    created_by varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    archived_at timestamp with time zone
);

create index idx_ai_chat_conversations_created_at on ai_chat_conversations(created_at);
create index idx_ai_chat_conversations_cluster_created_at on ai_chat_conversations(cluster_id, created_at);

create table ai_chat_messages (
    id uuid primary key,
    conversation_id uuid not null references ai_chat_conversations(id),
    role varchar(64) not null,
    content text not null,
    model varchar(255),
    prompt_version varchar(64),
    finish_reason varchar(64),
    latency_ms bigint,
    error_code varchar(128),
    error_message varchar(1000),
    created_by varchar(255) not null,
    created_at timestamp with time zone not null
);

create index idx_ai_chat_messages_conversation_created_at on ai_chat_messages(conversation_id, created_at);

create table ai_chat_context_references (
    id uuid primary key,
    message_id uuid not null references ai_chat_messages(id),
    reference_type varchar(64) not null,
    reference_id uuid,
    label varchar(500) not null,
    created_at timestamp with time zone not null
);

create index idx_ai_chat_context_references_message on ai_chat_context_references(message_id);
