alter table assistant_session_contexts
    add column if not exists runtime_memory_state jsonb;
