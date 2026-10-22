package com.management.software;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * backend-management 应用启动类。
 *
 * <p>分层约定（详见 docs/project-spec.md）：
 * <ul>
 *     <li>interfaces：对外接口（facade / web）</li>
 *     <li>application：应用服务、DTO、领域事件</li>
 *     <li>domain：领域实体与领域服务</li>
 *     <li>infrastructure：数据访问、缓存、配置、工具、异常等</li>
 * </ul>
 *
 * <p>{@code @EnableScheduling} 为定时任务（{@code infrastructure.tasks} 下带
 * {@code @Scheduled} 的类）提供调度支持，当前用于每天 08:00 的考勤统计汇总。
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableScheduling
@MapperScan("com.management.software.infrastructure.repository")
public class ManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManagementApplication.class, args);
    }

}
