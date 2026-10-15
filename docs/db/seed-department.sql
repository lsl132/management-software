-- ============================================================================
-- 字典数据：部门（department）
-- 幂等可重复执行：按部门名称唯一键 upsert
-- 执行顺序：先本脚本，再 seed-department-rank.sql（职级需按部门归属）
--   psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/seed-department.sql
-- ============================================================================

insert into department (depart_name, description) values
    ('生产部',     '一线操作工种，对应 O 序列职级'),
    ('技术研发部', '技术/研发，对应 T 序列职级'),
    ('行政后勤部', '后勤支持，对应 F 序列职级'),
    ('管理总部',   '管理层，对应 M 序列职级'),
    ('销售部',     '销售业务，对应 S 序列职级')
on conflict (depart_name) do update
    set description = excluded.description;
