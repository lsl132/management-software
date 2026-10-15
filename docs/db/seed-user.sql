-- ============================================================================
-- 字典数据：用户与账号（"user" / user_account）
-- 目标：创建指定 6 个用户，初始密码统一为 abcde12345（BCrypt 密文存储）
-- 幂等可重复执行：
--   1) "user" 以 username 唯一键 upsert
--   2) user_account 以 identifier 唯一键 upsert
-- 执行顺序：
--   psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/schema.sql
--   psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/seed-user.sql
-- ============================================================================

begin;

-- 初始密码明文：abcde12345
-- 使用 Spring BCryptPasswordEncoder 生成（每次可不同），此处固定为同一密文便于批量初始化。
-- hash: $2a$10$07DUg5o5b8IJKq/EAOCpJeiIVWW6sn/bBISsP.H97KVljBYivKXs.

-- 1) 吴千光 / wuqianguang@zhurui.com
with upsert_user as (
    insert into "user" (username, mobile, email, status)
    values ('吴千光', '13900000001', 'wuqianguang@zhurui.com', 1)
    on conflict (username) do update
        set mobile = excluded.mobile,
            email = excluded.email,
            status = excluded.status,
            update_time = current_timestamp
    returning id
), resolved_user as (
    select id from upsert_user
    union all
    select id from "user" where username = '吴千光'
    limit 1
)
insert into user_account (user_id, identifier, credential)
select id, 'wuqianguang@zhurui.com', '$2a$10$07DUg5o5b8IJKq/EAOCpJeiIVWW6sn/bBISsP.H97KVljBYivKXs.'
from resolved_user
on conflict (identifier) do update
    set user_id = excluded.user_id,
        credential = excluded.credential,
        update_time = current_timestamp;

-- 2) 林如宣 / linruxuan@zhurui.com
with upsert_user as (
    insert into "user" (username, mobile, email, status)
    values ('林如宣', '13900000002', 'linruxuan@zhurui.com', 1)
    on conflict (username) do update
        set mobile = excluded.mobile,
            email = excluded.email,
            status = excluded.status,
            update_time = current_timestamp
    returning id
), resolved_user as (
    select id from upsert_user
    union all
    select id from "user" where username = '林如宣'
    limit 1
)
insert into user_account (user_id, identifier, credential)
select id, 'linruxuan@zhurui.com', '$2a$10$07DUg5o5b8IJKq/EAOCpJeiIVWW6sn/bBISsP.H97KVljBYivKXs.'
from resolved_user
on conflict (identifier) do update
    set user_id = excluded.user_id,
        credential = excluded.credential,
        update_time = current_timestamp;

-- 3) 曾尧心 / zengyaoxin@zhurui.com
with upsert_user as (
    insert into "user" (username, mobile, email, status)
    values ('曾尧心', '13900000003', 'zengyaoxin@zhurui.com', 1)
    on conflict (username) do update
        set mobile = excluded.mobile,
            email = excluded.email,
            status = excluded.status,
            update_time = current_timestamp
    returning id
), resolved_user as (
    select id from upsert_user
    union all
    select id from "user" where username = '曾尧心'
    limit 1
)
insert into user_account (user_id, identifier, credential)
select id, 'zengyaoxin@zhurui.com', '$2a$10$07DUg5o5b8IJKq/EAOCpJeiIVWW6sn/bBISsP.H97KVljBYivKXs.'
from resolved_user
on conflict (identifier) do update
    set user_id = excluded.user_id,
        credential = excluded.credential,
        update_time = current_timestamp;

-- 4) 颜岳威 / yanyuewei@zhurui.com
with upsert_user as (
    insert into "user" (username, mobile, email, status)
    values ('颜岳威', '13900000004', 'yanyuewei@zhurui.com', 1)
    on conflict (username) do update
        set mobile = excluded.mobile,
            email = excluded.email,
            status = excluded.status,
            update_time = current_timestamp
    returning id
), resolved_user as (
    select id from upsert_user
    union all
    select id from "user" where username = '颜岳威'
    limit 1
)
insert into user_account (user_id, identifier, credential)
select id, 'yanyuewei@zhurui.com', '$2a$10$07DUg5o5b8IJKq/EAOCpJeiIVWW6sn/bBISsP.H97KVljBYivKXs.'
from resolved_user
on conflict (identifier) do update
    set user_id = excluded.user_id,
        credential = excluded.credential,
        update_time = current_timestamp;

-- 5) 林宜臻 / linyizhen@zhurui.com
with upsert_user as (
    insert into "user" (username, mobile, email, status)
    values ('林宜臻', '13900000005', 'linyizhen@zhurui.com', 1)
    on conflict (username) do update
        set mobile = excluded.mobile,
            email = excluded.email,
            status = excluded.status,
            update_time = current_timestamp
    returning id
), resolved_user as (
    select id from upsert_user
    union all
    select id from "user" where username = '林宜臻'
    limit 1
)
insert into user_account (user_id, identifier, credential)
select id, 'linyizhen@zhurui.com', '$2a$10$07DUg5o5b8IJKq/EAOCpJeiIVWW6sn/bBISsP.H97KVljBYivKXs.'
from resolved_user
on conflict (identifier) do update
    set user_id = excluded.user_id,
        credential = excluded.credential,
        update_time = current_timestamp;

-- 6) 黄惠如 / huanghuiru@zhurui.com
with upsert_user as (
    insert into "user" (username, mobile, email, status)
    values ('黄惠如', '13900000006', 'huanghuiru@zhurui.com', 1)
    on conflict (username) do update
        set mobile = excluded.mobile,
            email = excluded.email,
            status = excluded.status,
            update_time = current_timestamp
    returning id
), resolved_user as (
    select id from upsert_user
    union all
    select id from "user" where username = '黄惠如'
    limit 1
)
insert into user_account (user_id, identifier, credential)
select id, 'huanghuiru@zhurui.com', '$2a$10$07DUg5o5b8IJKq/EAOCpJeiIVWW6sn/bBISsP.H97KVljBYivKXs.'
from resolved_user
on conflict (identifier) do update
    set user_id = excluded.user_id,
        credential = excluded.credential,
        update_time = current_timestamp;

commit;

