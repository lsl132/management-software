-- ============================================================================
-- 字典数据：权限（authority）
-- 幂等可重复执行：按权限助记符唯一键（uk_authority_mnemonic）upsert
-- 执行顺序：必须先执行 schema.sql（authority.type 列由建表脚本补齐）
--   psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/schema.sql
--   psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/seed-authority.sql
-- type 取值见 infrastructure/enums/AuthorityTypeEnum：1=模块分组（菜单） 2=功能页
-- 权限为两级结构：模块分组的助记符为整万位（10000），其下功能页按 1 递增（10001…）
-- ============================================================================

begin;

insert into authority (mnemonic, auth_name, type) values
    -- 10000 系统设置
    (10000, '系统设置', 1),
    (10001, '系统用户', 2),
    (10002, '权限管理', 2),
    (10003, '角色管理', 2),
    (10004, '审批规则', 2),
    -- 20000 人力资源
    (20000, '人力资源', 1),
    (20001, '员工管理', 2),
    (20002, '薪酬绩效', 2),
    (20003, '组织架构', 2),
    -- 30000 财务管理
    (30000, '财务管理', 1),
    (30001, '应收应付', 2),
    (30002, '资产清单', 2),
    (30003, '税务报表', 2),
    (30004, '预算管控', 2),
    (30005, '财务计划', 2),
    -- 40000 生产制造
    (40000, '生产制造', 1),
    (40001, '订单管理', 2),
    (40002, '库存管理', 2),
    (40003, '生产排程', 2),
    (40004, '物料需求', 2),
    -- 50000 战略决策
    (50000, '战略决策', 1),
    (50001, '经营报表', 2),
    (50002, '销售趋势', 2)
on conflict (mnemonic) do update
    set auth_name = excluded.auth_name,
        type      = excluded.type;

commit;
