insert into users (id, user_code, display_name, created_at, updated_at)
values (1001, 'u1001', '测试用户甲', now(), now()),
       (1002, 'u1002', '测试用户乙', now(), now());

insert into groups (id, group_code, group_name, status, created_at, updated_at)
values (2001, 'product-team', '产品团队', 'ACTIVE', now(), now()),
       (2002, 'engineering-team', '研发团队', 'ACTIVE', now(), now());

insert into group_memberships (user_id, group_id, created_at, updated_at)
values (1001, 2001, now(), now()),
       (1002, 2001, now(), now()),
       (1002, 2002, now(), now());
