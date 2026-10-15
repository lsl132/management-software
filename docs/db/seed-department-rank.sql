-- ============================================================================
-- 字典数据：部门职级（department_rank）
-- 幂等可重复执行：按 code 唯一键 upsert
-- 执行顺序：必须先执行 seed-department.sql
--   psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/seed-department.sql
--   psql -h localhost -p 5432 -U postgres -d backend_management -f docs/db/seed-department-rank.sql
-- type 取值见 infrastructure/enums/RankTypeEnum：1=O 一线操作工种 2=T 技术/研发
--                                            3=F 后勤 4=M 管理 5=S 销售
-- 职级归属部门：每个职级必须挂在一个部门下（不同部门有各自的职级）
-- ============================================================================

begin;

insert into department_rank (code, type, department, intro) values
    -- O 序列（一线操作工种）→ 生产部
    ('O1', 1, (select id from department where depart_name = '生产部'), '初级工'),
    ('O2', 1, (select id from department where depart_name = '生产部'), '中级工'),
    ('O3', 1, (select id from department where depart_name = '生产部'), '高级工'),
    ('O4', 1, (select id from department where depart_name = '生产部'), '高级技师'),
    ('O5', 1, (select id from department where depart_name = '生产部'), '车间主任'),
    -- T 序列（技术/研发）→ 技术研发部
    ('T1', 2, (select id from department where depart_name = '技术研发部'), '初级工程师'),
    ('T2', 2, (select id from department where depart_name = '技术研发部'), '中级工程师'),
    ('T3', 2, (select id from department where depart_name = '技术研发部'), '高级工程师'),
    ('T4', 2, (select id from department where depart_name = '技术研发部'), '架构师'),
    ('T5', 2, (select id from department where depart_name = '技术研发部'), '首席'),
    -- F 序列（后勤）→ 行政后勤部
    ('F1', 3, (select id from department where depart_name = '行政后勤部'), '助理'),
    ('F2', 3, (select id from department where depart_name = '行政后勤部'), '专员'),
    ('F3', 3, (select id from department where depart_name = '行政后勤部'), '主管'),
    ('F4', 3, (select id from department where depart_name = '行政后勤部'), '总监'),
    -- M 序列（管理）→ 管理总部
    ('M1', 4, (select id from department where depart_name = '管理总部'), '组长'),
    ('M2', 4, (select id from department where depart_name = '管理总部'), '经理'),
    ('M3', 4, (select id from department where depart_name = '管理总部'), '总监'),
    ('M4', 4, (select id from department where depart_name = '管理总部'), '副总'),
    ('M5', 4, (select id from department where depart_name = '管理总部'), 'CEO'),
    -- S 序列（销售）→ 销售部
    ('S1', 5, (select id from department where depart_name = '销售部'), '销售代表'),
    ('S2', 5, (select id from department where depart_name = '销售部'), '销售经理'),
    ('S3', 5, (select id from department where depart_name = '销售部'), '区域经理'),
    ('S4', 5, (select id from department where depart_name = '销售部'), '销售总监')
on conflict (code) do update
    set type       = excluded.type,
        department = excluded.department,
        intro      = excluded.intro;

-- 归属部门回填完成后收紧非空约束（幂等：已非空或仍有未归属职级时不会中断）
do $$
begin
    if not exists (select 1 from department_rank where department is null) then
        alter table department_rank alter column department set not null;
    else
        raise notice '仍有 % 条职级未归属部门，跳过 set not null',
            (select count(*) from department_rank where department is null);
    end if;
end $$;

commit;
