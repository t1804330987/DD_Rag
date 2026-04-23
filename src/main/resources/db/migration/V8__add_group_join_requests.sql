create table group_join_requests (
    id bigserial primary key,
    group_id bigint not null references groups(id),
    applicant_user_id bigint not null references users(id),
    status varchar(32) not null,
    decided_at timestamp,
    decided_by_user_id bigint references users(id),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint ck_group_join_requests_status
        check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED'))
);

create unique index uk_group_join_requests_pending
    on group_join_requests(group_id, applicant_user_id)
    where status = 'PENDING';

create index idx_group_join_requests_group_status
    on group_join_requests(group_id, status);

create index idx_group_join_requests_applicant
    on group_join_requests(applicant_user_id, created_at desc);
