alter table assistant_session_contexts
    rename column summary_text to session_memory;

alter table assistant_session_contexts
    rename column source_message_id to session_memory_range_end_message_id;

alter table assistant_session_contexts
    alter column session_memory drop not null;

alter table assistant_session_contexts
    add column compact_summary text,
    add column session_memory_base_message_id bigint references assistant_messages(id),
    add column compact_summary_base_message_id bigint references assistant_messages(id),
    add column compact_summary_range_end_message_id bigint references assistant_messages(id),
    add column context_version integer not null default 0;

create index idx_assistant_session_contexts_session_memory_range_end_message_id
    on assistant_session_contexts(session_memory_range_end_message_id);

alter table assistant_user_memories
    rename column id to memory_id;

alter table assistant_user_memories
    rename column title to memory_key;

alter table assistant_user_memories
    rename column content to memory_value;

alter table assistant_user_memories
    add column tenant_id bigint not null default 0,
    add column status varchar(32) not null default 'APPROVED',
    add column sensitivity_level varchar(32) not null default 'LOW',
    add column source_message_id bigint references assistant_messages(id),
    add column confidence numeric(3,2) not null default 1.0,
    add column version integer not null default 1,
    add column previous_memory_id bigint,
    add column created_by bigint,
    add column updated_by bigint,
    add column disabled_at timestamp;

update assistant_user_memories
set created_by = user_id,
    updated_by = user_id
where created_by is null
   or updated_by is null;

alter table assistant_user_memories
    drop constraint if exists assistant_user_memories_pkey,
    add constraint assistant_user_memories_pkey primary key (memory_id);

alter table assistant_user_memories
    drop constraint if exists ck_assistant_user_memories_source;

alter table assistant_user_memories
    add constraint ck_assistant_user_memories_source
        check (source in (
            'USER_CREATED',
            'USER_EDITED',
            'TOOL',
            'CANDIDATE_PROMOTION',
            'USER_CONFIRMATION'
        )),
    add constraint ck_assistant_user_memories_status
        check (status in ('APPROVED', 'PENDING_CONFIRMATION', 'REJECTED', 'ARCHIVED')),
    add constraint ck_assistant_user_memories_sensitivity_level
        check (sensitivity_level in ('LOW', 'MEDIUM', 'HIGH', 'SENSITIVE'));

create index idx_assistant_user_memories_status
    on assistant_user_memories(status);

create index idx_assistant_user_memories_user_type_key
    on assistant_user_memories(user_id, memory_type, memory_key);

create table assistant_memory_candidates (
    candidate_id bigserial primary key,
    user_id bigint not null references users(id),
    tenant_id bigint not null default 0,
    memory_type varchar(32) not null,
    memory_key varchar(255) not null,
    memory_value text not null,
    candidate_reason text not null,
    sensitivity_level varchar(32) not null default 'LOW',
    source varchar(32) not null,
    source_message_id bigint references assistant_messages(id),
    status varchar(32) not null default 'PENDING_CONFIRMATION',
    expires_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint ck_assistant_memory_candidates_type
        check (memory_type in ('PREFERENCE', 'BACKGROUND')),
    constraint ck_assistant_memory_candidates_source
        check (source in ('AFTER_MODEL', 'TOOL', 'USER_CONFIRMATION', 'SYSTEM_IMPORT')),
    constraint ck_assistant_memory_candidates_status
        check (status in ('PENDING_CONFIRMATION', 'APPROVED', 'REJECTED', 'EXPIRED')),
    constraint ck_assistant_memory_candidates_sensitivity_level
        check (sensitivity_level in ('LOW', 'MEDIUM', 'HIGH', 'SENSITIVE'))
);

create index idx_assistant_memory_candidates_user_id
    on assistant_memory_candidates(user_id);

create index idx_assistant_memory_candidates_status
    on assistant_memory_candidates(status);

create index idx_assistant_memory_candidates_expires_at
    on assistant_memory_candidates(expires_at);
