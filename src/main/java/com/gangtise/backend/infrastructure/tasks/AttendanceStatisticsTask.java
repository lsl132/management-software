package com.gangtise.backend.infrastructure.tasks;

import com.gangtise.backend.application.service.AttendanceService;
import com.gangtise.backend.infrastructure.config.AttendanceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 考勤统计定时任务：每天 08:00 汇总**前一天**的考勤。
 *
 * <p>为什么是「昨天」而不是「今天」：当天的打卡还没结束（有人可能加班到深夜），
 * 8 点去统计今天等于把半天数据当成全天。汇总的是已经完整过去的一天。
 *
 * <p>为什么是 8 点：比正常上班时间早，员工到岗时昨天的考勤已经算好；
 * 同时离零点又足够远，不会和跨天的收尾动作挤在一起。
 *
 * <p>任务本身只做「挑日子 + 记日志」，判定规则全在 {@link AttendanceService#statisticsFor}。
 * 这样人工补算某一天时可以直接调服务，不必绕过定时调度的壳。
 *
 * <p><b>多实例部署</b>：K8s 多副本时每个实例都会在 8 点触发一次。这里没有加分布式锁，
 * 因为写入是按 {@code (work_no, day)} 的幂等 upsert，重复执行的代价只是白算一遍，
 * 而为此引入 Redis 锁反而多出一个「锁没释放导致当天彻底不跑」的故障模式。
 */
@Component
public class AttendanceStatisticsTask {

    private static final Logger log = LoggerFactory.getLogger(AttendanceStatisticsTask.class);

    private final AttendanceService attendanceService;
    private final AttendanceProperties properties;

    public AttendanceStatisticsTask(AttendanceService attendanceService, AttendanceProperties properties) {
        this.attendanceService = attendanceService;
        this.properties = properties;
    }

    /** 每天 08:00 执行，cron 可用 {@code app.attendance.statistics-cron} 覆盖。 */
    @Scheduled(cron = "${app.attendance.statistics-cron:0 0 8 * * ?}")
    public void statisticsYesterday() {
        if (!properties.isEnabled()) {
            log.info("[考勤统计] 开关已关闭（app.attendance.enabled=false），本次不执行");
            return;
        }

        // 「昨天」按配置时区算，不能取 JVM 默认时区：容器默认多半是 UTC
        LocalDate yesterday = LocalDate.now(properties.zoneId()).minusDays(1);
        try {
            attendanceService.statisticsFor(yesterday);
        } catch (Exception e) {
            // 定时任务里抛异常只会被调度器打一条日志，且容易混在其它输出里；
            // 这里显式记一条带日期的错误，方便按日期排查/补算
            log.error("[考勤统计] {} 汇总失败，可手动调用 AttendanceService#statisticsFor 补算", yesterday, e);
        }
    }

}
