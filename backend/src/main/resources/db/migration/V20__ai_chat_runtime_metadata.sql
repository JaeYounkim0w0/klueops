alter table ai_chat_messages add column first_token_latency_ms bigint;
alter table ai_chat_messages add column total_latency_ms bigint;
alter table ai_chat_messages add column context_chars integer;
