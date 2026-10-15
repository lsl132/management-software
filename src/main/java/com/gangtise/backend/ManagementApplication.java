package com.gangtise.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
 */
@SpringBootApplication
@EnableTransactionManagement
@MapperScan("com.gangtise.backend.infrastructure.repository")
public class ManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManagementApplication.class, args);
    }

}
