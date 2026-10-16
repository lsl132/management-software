-- ============================================================================
-- 字典数据：权限（authority）
-- 幂等可重复执行：按权限助记符唯一键（uk_authority_mnemonic）upsert
-- 执行顺序：必须先执行 schema.sql（authority.type / authority.path 列由建表脚本补齐）
--   psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/schema.sql
--   psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/seed-authority.sql
-- type 取值见 infrastructure/enums/AuthorityTypeEnum：1=模块分组（菜单） 2=功能页
-- 权限为两级结构：模块分组的助记符为整万位（10000），其下功能页按 1 递增（10001…）
--
-- path：功能页的路由，二级导航按它渲染成可点的链接；**留空表示该功能页尚未实现**，
--       菜单里置灰不可点。模块分组是下拉容器，一律不设路由（服务端会强制置空）。
--       注意：本脚本是字典基线，重跑会把 path 覆盖回下表的值（与 auth_name / type 同一策略）。
-- ============================================================================

begin;

insert into authority (mnemonic, auth_name, type, path) values
    -- 10000 系统设置
    (10000, '系统设置', 1, null),
    (10001, '系统用户', 2, '/users'),
    (10002, '权限管理', 2, '/authorities'),
    (10003, '角色管理', 2, '/roles'),
    -- 10004 审批规则（即「流程管理」：流程 workflow + 节点 workflow_node 的编排页）
    (10004, '流程管理', 2, '/workflows'),
    (10005, '公告管理', 2, '/announcements'),
    -- 20000 人力资源
    (20000, '人力资源', 1, null),
    (20001, '员工管理', 2, '/employees'),
    (20002, '薪酬绩效', 2, null),
    (20003, '组织架构', 2, '/departments'),
    -- 30000 财务管理
    (30000, '财务管理', 1, null),
    (30001, '应收应付', 2, null),
    (30002, '资产清单', 2, null),
    (30003, '税务报表', 2, null),
    (30004, '预算管控', 2, null),
    (30005, '财务计划', 2, null),
    -- 40000 生产制造
    (40000, '生产制造', 1, null),
    (40001, '订单管理', 2, null),
    (40002, '库存管理', 2, null),
    (40003, '生产排程', 2, null),
    (40004, '物料需求', 2, null),
    -- 50000 战略决策
    (50000, '战略决策', 1, null),
    (50001, '经营报表', 2, null),
    (50002, '销售趋势', 2, null)
on conflict (mnemonic) do update
    set auth_name = excluded.auth_name,
        type      = excluded.type,
        path      = excluded.path;

commit;
