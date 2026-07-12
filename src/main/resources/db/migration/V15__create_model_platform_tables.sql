create table model_connections (
    id bigserial primary key,
    provider_type varchar(32) not null,
    owner_type varchar(16) not null,
    owner_user_id bigint references users(id),
    name varchar(128) not null,
    base_url varchar(512),
    api_key_plaintext text,
    credential_storage_type varchar(32) not null default 'PLAINTEXT',
    credential_version integer not null default 1,
    masked_key_suffix varchar(16),
    provider_options_json jsonb,
    max_concurrency integer,
    status varchar(32) not null default 'UNVERIFIED',
    config_version bigint not null default 1,
    last_connection_test_status varchar(32),
    last_connection_test_at timestamp,
    deleted_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint ck_model_connections_owner
        check ((owner_type = 'PLATFORM' and owner_user_id is null)
            or (owner_type = 'USER' and owner_user_id is not null)),
    constraint ck_model_connections_credential_storage
        check (credential_storage_type = 'PLAINTEXT'),
    constraint ck_model_connections_credential_version
        check (credential_version > 0),
    constraint ck_model_connections_max_concurrency
        check (max_concurrency is null or max_concurrency > 0),
    constraint ck_model_connections_status
        check (status in ('UNVERIFIED', 'ACTIVE', 'FAILED', 'DISABLED', 'DELETED')),
    constraint ck_model_connections_config_version
        check (config_version > 0),
    constraint ck_model_connections_last_test_status
        check (last_connection_test_status is null
            or last_connection_test_status in ('UNVERIFIED', 'PASSED', 'FAILED')),
    constraint ck_model_connections_deleted_at
        check ((status = 'DELETED' and deleted_at is not null)
            or (status <> 'DELETED' and deleted_at is null)),
    constraint ck_model_connections_api_key_lifecycle
        check ((status = 'DELETED' and api_key_plaintext is null)
            or (status <> 'DELETED' and api_key_plaintext is not null))
);

create unique index uk_model_connections_platform_name_active
    on model_connections(name)
    where owner_type = 'PLATFORM' and status <> 'DELETED';

create unique index uk_model_connections_user_name_active
    on model_connections(owner_user_id, name)
    where owner_type = 'USER' and status <> 'DELETED';

create index idx_model_connections_owner_status
    on model_connections(owner_type, owner_user_id, status);

create index idx_model_connections_status
    on model_connections(status);

create table model_connection_models (
    id bigserial primary key,
    connection_id bigint not null references model_connections(id),
    model_name varchar(255) not null,
    source_type varchar(32) not null,
    capabilities_json jsonb,
    test_status varchar(32) not null default 'UNVERIFIED',
    tested_config_version bigint,
    last_test_at timestamp,
    enabled boolean not null default false,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uk_model_connection_models_connection_name unique (connection_id, model_name),
    constraint uk_model_connection_models_id_connection unique (id, connection_id),
    constraint ck_model_connection_models_source
        check (source_type in ('BUILT_IN', 'DISCOVERED', 'MANUAL')),
    constraint ck_model_connection_models_test_status
        check (test_status in ('UNVERIFIED', 'PASSED', 'FAILED')),
    constraint ck_model_connection_models_test_version
        check (tested_config_version is null or tested_config_version > 0),
    constraint ck_model_connection_models_test_result
        check ((test_status = 'UNVERIFIED' and tested_config_version is null and last_test_at is null)
            or (test_status in ('PASSED', 'FAILED')
                and tested_config_version is not null and last_test_at is not null)),
    constraint ck_model_connection_models_enabled
        check (not enabled or test_status = 'PASSED')
);

create index idx_model_connection_models_connection_enabled
    on model_connection_models(connection_id, enabled);

create index idx_model_connection_models_test_status
    on model_connection_models(test_status, enabled);

create table model_connection_grants (
    id bigserial primary key,
    connection_id bigint not null references model_connections(id),
    grant_type varchar(32) not null,
    grantee_user_id bigint references users(id),
    created_at timestamp not null default current_timestamp,
    constraint ck_model_connection_grants_subject
        check ((grant_type = 'ALL_BUSINESS_USERS' and grantee_user_id is null)
            or (grant_type = 'USER' and grantee_user_id is not null))
);

create unique index uk_model_connection_grants_all_users
    on model_connection_grants(connection_id)
    where grant_type = 'ALL_BUSINESS_USERS';

create unique index uk_model_connection_grants_user
    on model_connection_grants(connection_id, grantee_user_id)
    where grant_type = 'USER';

create index idx_model_connection_grants_grantee
    on model_connection_grants(grantee_user_id, connection_id);

create table model_scenario_routes (
    id bigserial primary key,
    scenario varchar(64) not null,
    connection_id bigint not null references model_connections(id),
    model_id bigint not null,
    enabled boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uk_model_scenario_routes_scenario unique (scenario),
    constraint fk_model_scenario_routes_model_connection
        foreign key (model_id, connection_id)
        references model_connection_models(id, connection_id),
    constraint ck_model_scenario_routes_scenario
        check (scenario in ('ASSISTANT_CHAT', 'QA_ANSWER', 'QUERY_PLANNING', 'SESSION_SUMMARY',
            'RUNTIME_MEMORY_EXTRACTION', 'CONNECTION_TEST', 'MODEL_TEST'))
);

create index idx_model_scenario_routes_connection_model
    on model_scenario_routes(connection_id, model_id, enabled);

create table assistant_instruction_profiles (
    id bigserial primary key,
    user_id bigint not null references users(id),
    name varchar(128) not null,
    current_version_id bigint,
    enabled boolean not null default true,
    is_default boolean not null default false,
    deleted_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create unique index uk_assistant_instruction_profiles_user_default
    on assistant_instruction_profiles(user_id)
    where is_default and enabled and deleted_at is null;

create unique index uk_assistant_instruction_profiles_user_name_active
    on assistant_instruction_profiles(user_id, name)
    where deleted_at is null;

create index idx_assistant_instruction_profiles_user_enabled
    on assistant_instruction_profiles(user_id, enabled);

create table assistant_instruction_profile_versions (
    id bigserial primary key,
    profile_id bigint not null references assistant_instruction_profiles(id),
    version integer not null,
    content text not null,
    created_at timestamp not null default current_timestamp,
    constraint uk_assistant_instruction_profile_versions unique (profile_id, version),
    constraint uk_assistant_instruction_profile_versions_id_profile unique (id, profile_id),
    constraint ck_assistant_instruction_profile_versions_version check (version > 0)
);

alter table assistant_instruction_profiles
    add constraint fk_assistant_instruction_profiles_current_version
    foreign key (current_version_id, id)
    references assistant_instruction_profile_versions(id, profile_id);

create index idx_assistant_instruction_profile_versions_profile_created
    on assistant_instruction_profile_versions(profile_id, created_at desc);

alter table assistant_sessions
    add column current_model_connection_id bigint,
    add column current_model_id bigint,
    add column current_instruction_profile_id bigint,
    add constraint fk_assistant_sessions_model_connection
        foreign key (current_model_connection_id) references model_connections(id) on delete set null,
    add constraint fk_assistant_sessions_model
        foreign key (current_model_id, current_model_connection_id)
        references model_connection_models(id, connection_id) on delete set null,
    add constraint fk_assistant_sessions_instruction_profile
        foreign key (current_instruction_profile_id) references assistant_instruction_profiles(id) on delete set null,
    add constraint ck_assistant_sessions_current_model_pair
        check ((current_model_connection_id is null and current_model_id is null)
            or (current_model_connection_id is not null and current_model_id is not null));

create index idx_assistant_sessions_current_model
    on assistant_sessions(current_model_connection_id, current_model_id);

create table assistant_turn_requests (
    id bigserial primary key,
    user_id bigint not null references users(id),
    request_id varchar(128) not null,
    turn_id varchar(128),
    session_id bigint references assistant_sessions(id) on delete set null,
    user_message_id bigint references assistant_messages(id) on delete set null,
    assistant_message_id bigint references assistant_messages(id) on delete set null,
    status varchar(32) not null,
    failure_code varchar(64),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    completed_at timestamp,
    constraint uk_assistant_turn_requests_user_request unique (user_id, request_id),
    constraint ck_assistant_turn_requests_status
        check (status in ('RUNNING', 'COMPLETED', 'FAILED')),
    constraint ck_assistant_turn_requests_completion
        check ((status = 'RUNNING' and completed_at is null)
            or (status in ('COMPLETED', 'FAILED') and completed_at is not null))
);

create index idx_assistant_turn_requests_session_status
    on assistant_turn_requests(session_id, status);

create index idx_assistant_turn_requests_user_created
    on assistant_turn_requests(user_id, created_at desc);

create table model_call_ledger (
    id bigserial primary key,
    invocation_id varchar(128) not null,
    user_id bigint references users(id) on delete set null,
    scenario varchar(64) not null,
    session_id bigint references assistant_sessions(id) on delete set null,
    user_message_id bigint references assistant_messages(id) on delete set null,
    assistant_message_id bigint references assistant_messages(id) on delete set null,
    turn_id varchar(128),
    request_id varchar(128),
    connection_id bigint references model_connections(id) on delete set null,
    model_id bigint,
    provider_type_snapshot varchar(32) not null,
    model_name_snapshot varchar(255) not null,
    connection_name_snapshot varchar(128),
    owner_type_snapshot varchar(16),
    instruction_profile_id bigint references assistant_instruction_profiles(id) on delete set null,
    instruction_version_id bigint,
    instruction_version_snapshot integer,
    input_tokens bigint,
    output_tokens bigint,
    total_tokens bigint,
    duration_ms bigint,
    logical_status varchar(32) not null,
    transport_status varchar(32) not null,
    error_category varchar(64),
    error_summary varchar(512),
    started_at timestamp not null default current_timestamp,
    finished_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uk_model_call_ledger_invocation unique (invocation_id),
    constraint fk_model_call_ledger_model_connection
        foreign key (model_id, connection_id)
        references model_connection_models(id, connection_id) on delete set null,
    constraint fk_model_call_ledger_instruction_version
        foreign key (instruction_version_id, instruction_profile_id)
        references assistant_instruction_profile_versions(id, profile_id) on delete set null,
    constraint ck_model_call_ledger_scenario
        check (scenario in ('ASSISTANT_CHAT', 'QA_ANSWER', 'QUERY_PLANNING', 'SESSION_SUMMARY',
            'RUNTIME_MEMORY_EXTRACTION', 'CONNECTION_TEST', 'MODEL_TEST')),
    constraint ck_model_call_ledger_owner_type_snapshot
        check (owner_type_snapshot is null or owner_type_snapshot in ('PLATFORM', 'USER')),
    constraint ck_model_call_ledger_logical_status
        check (logical_status in ('RUNNING', 'SUCCEEDED', 'FAILED', 'TIMEOUT',
            'CANCEL_REQUESTED', 'CANCELLED')),
    constraint ck_model_call_ledger_transport_status
        check (transport_status in ('ACTIVE', 'TERMINATED', 'DETACHED', 'HARD_TIMEOUT')),
    constraint ck_model_call_ledger_tokens
        check ((input_tokens is null or input_tokens >= 0)
            and (output_tokens is null or output_tokens >= 0)
            and (total_tokens is null or total_tokens >= 0)),
    constraint ck_model_call_ledger_duration
        check (duration_ms is null or duration_ms >= 0)
);

create index idx_model_call_ledger_user_started
    on model_call_ledger(user_id, started_at desc);

create index idx_model_call_ledger_scenario_started
    on model_call_ledger(scenario, started_at desc);

create index idx_model_call_ledger_connection_started
    on model_call_ledger(connection_id, started_at desc);

create index idx_model_call_ledger_model_started
    on model_call_ledger(model_id, started_at desc);

create index idx_model_call_ledger_status_started
    on model_call_ledger(logical_status, transport_status, started_at);

create index idx_model_call_ledger_request
    on model_call_ledger(user_id, request_id);
