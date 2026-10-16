package com.gangtise.backend.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gangtise.backend.domain.aggregate.entity.EmployeeSalary;
import org.apache.ibatis.annotations.Insert;

/**
 * 员工薪资 Mapper。
 */
public interface EmployeeSalaryMapper extends BaseMapper<EmployeeSalary> {

    /**
     * 按工号幂等写入员工薪酬。冲突键是 {@code work_no}，对应唯一索引
     * {@code uk_employee_salary_work_no}。
     *
     * <p><b>为什么是 upsert 而不是「先查后写」</b>：两个人同时打开同一个员工的薪酬弹窗时，
     * 「都查不到 → 都插入」会插出两行同工号记录，之后谁在改哪一行就说不清了。
     * 交给数据库的唯一索引判定，只有一条能落成 insert。
     *
     * <p>三个细节：
     * <ul>
     *   <li>{@code create_time} 只在 insert 分支写，冲突走 update 时不刷新，
     *       保留「这条薪酬第一次是什么时候设的」；</li>
     *   <li>{@code update_time}/{@code update_by} 每次写都刷新——它们要回答的是
     *       「现在的值是谁什么时候改的」；</li>
     *   <li>这里用注解 SQL 而不是 MyBatis-Plus 的 insert/updateById：自动填充只在 MP
     *       自己注入的方法里生效，自定义 SQL 必须把时间写进 SQL。</li>
     * </ul>
     */
    @Insert("""
            insert into employee_salary
                (work_no, currency, month_salary, year_salary, day_salary,
                 create_time, update_time, update_by)
            values (#{workNo}, #{currency}, #{monthSalary}, #{yearSalary}, #{daySalary},
                    current_timestamp, current_timestamp, #{updateBy})
            on conflict (work_no) do update
               set currency     = excluded.currency,
                   month_salary = excluded.month_salary,
                   year_salary  = excluded.year_salary,
                   day_salary   = excluded.day_salary,
                   update_time  = excluded.update_time,
                   update_by    = excluded.update_by
            """)
    int upsert(EmployeeSalary salary);

}
