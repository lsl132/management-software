package com.gangtise.backend.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gangtise.backend.domain.aggregate.Attendance;
import org.apache.ibatis.annotations.Insert;

/**
 * 考勤统计 Mapper。
 */
public interface AttendanceMapper extends BaseMapper<Attendance> {

    /**
     * 按「人 + 天」幂等写入考勤统计。
     *
     * <p>冲突键是 {@code (work_no, day)}，对应唯一索引 {@code uk_attendance_work_no_day}。
     * 定时任务重跑（多实例部署同时触发、当天人工补跑、改完规则重算）时必须靠它兜底：
     * 用「先查后插/改」会留出并发窗口，两边都查不到记录就会插出重复行。
     *
     * <p>三个细节：
     * <ul>
     *   <li>{@code first}/{@code last} 在缺卡时为 null，显式写 {@code jdbcType=BIGINT}——
     *       否则 Postgres 驱动遇 null 参数可能报 {@code JdbcType OTHER} 无法推断类型；</li>
     *   <li>{@code create_time} 只在 insert 分支写入，冲突走 update 时不刷新，
     *       保留「这行第一次是什么时候生成的」；</li>
     *   <li>这里用注解 SQL 而不是 MyBatis-Plus 的 insert/updateById：自动填充只在 MP 自身
     *       注入的方法里生效，自定义 SQL 必须自己带上 create_time。</li>
     * </ul>
     */
    @Insert("""
            insert into attendance (work_no, day, first, "last", extra, status, create_time)
            values (#{workNo}, #{day}, #{first,jdbcType=BIGINT}, #{last,jdbcType=BIGINT},
                    #{extra}, #{status}, current_timestamp)
            on conflict (work_no, day) do update
               set first  = excluded.first,
                   "last" = excluded."last",
                   extra  = excluded.extra,
                   status = excluded.status
            """)
    int upsert(Attendance attendance);

}
