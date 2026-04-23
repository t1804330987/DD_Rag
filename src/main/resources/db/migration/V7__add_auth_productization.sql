alter table users
    add column username varchar(64),
    add column email varchar(128),
    add column password_hash varchar(255),
    add column system_role varchar(32) not null default 'USER',
    add column status varchar(32) not null default 'ACTIVE',
    add column must_change_password boolean not null default true,
    add column last_login_at timestamp;

update users
set username = user_code,
    email = user_code || '@local.ddrag.test';

alter table users
    alter column username set not null,
    alter column email set not null;

create unique index uk_users_username on users(username);
create unique index uk_users_email on users(email);

alter table users
    add constraint ck_users_system_role
    check (system_role in ('ADMIN', 'USER'));

alter table users
    add constraint ck_users_status
    check (status in ('ACTIVE', 'DISABLED'));

create table user_refresh_tokens (
    id bigserial primary key,
    user_id bigint not null references users(id),
    token_id varchar(64) not null,
    token_hash varchar(255) not null,
    expires_at timestamp not null,
    revoked_at timestamp,
    created_at timestamp not null default current_timestamp
);

create unique index uk_user_refresh_tokens_token_id on user_refresh_tokens(token_id);
create index idx_user_refresh_tokens_user_id on user_refresh_tokens(user_id);
