drop table if exists assistant_memory_audit_logs;

drop index if exists idx_assistant_user_memories_enabled;
drop index if exists idx_assistant_user_memories_user_id;

drop table if exists assistant_user_memories;
