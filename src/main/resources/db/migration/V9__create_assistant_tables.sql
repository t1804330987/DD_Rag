create table assistant_sessions (
    id bigserial primary key,
    user_id bigint not null references users(id),
    title varchar(255) not null default '新会话',
    status varchar(32) not null default 'ACTIVE',
    last_message_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint ck_assistant_sessions_status
        check (status in ('ACTIVE', 'ARCHIVED', 'DELETED'))
);

create index idx_assistant_sessions_user_id
    on assistant_sessions(user_id);

create index idx_assistant_sessions_last_message_at
    on assistant_sessions(last_message_at desc);

create table assistant_messages (
    id bigserial primary key,
    session_id bigint not null references assistant_sessions(id),
    role varchar(32) not null,
    tool_mode varchar(32),
    group_id bigint references groups(id),
    content text not null,
    structured_payload jsonb,
    created_at timestamp not null default current_timestamp,
    constraint ck_assistant_messages_role
        check (role in ('USER', 'ASSISTANT', 'TOOL')),
    constraint ck_assistant_messages_tool_mode
        check (tool_mode is null or tool_mode in ('CHAT', 'KB_SEARCH')),
    constraint ck_assistant_messages_group_scope
        check (group_id is null or tool_mode = 'KB_SEARCH')
);

create index idx_assistant_messages_session_id
    on assistant_messages(session_id);

create table assistant_session_contexts (
    session_id bigint primary key references assistant_sessions(id),
    summary_text text not null,
    source_message_id bigint references assistant_messages(id),
    updated_at timestamp not null default current_timestamp
);

create table assistant_user_memories (
    id bigserial primary key,
    user_id bigint not null references users(id),
    memory_type varchar(32) not null,
    title varchar(255) not null,
    content text not null,
    enabled boolean not null default true,
    source varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint ck_assistant_user_memories_type
        check (memory_type in ('PREFERENCE', 'BACKGROUND')),
    constraint ck_assistant_user_memories_source
        check (source in ('USER_CREATED', 'USER_EDITED'))
);

create index idx_assistant_user_memories_user_id
    on assistant_user_memories(user_id);

create index idx_assistant_user_memories_enabled
    on assistant_user_memories(enabled);

create table assistant_memory_audit_logs (
    id bigserial primary key,
    memory_id bigint not null references assistant_user_memories(id),
    action varchar(32) not null,
    operator_user_id bigint not null references users(id),
    before_payload jsonb,
    after_payload jsonb,
    created_at timestamp not null default current_timestamp,
    constraint ck_assistant_memory_audit_logs_action
        check (action in ('CREATED', 'UPDATED', 'DELETED', 'ENABLED', 'DISABLED'))
);
