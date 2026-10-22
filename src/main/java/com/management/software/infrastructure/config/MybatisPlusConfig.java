package com.management.software.infrastructure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mybatis-plus 配置。
 *
 * <p>包含分页插件、乐观锁插件与防全表更新/删除插件。
 * 逻辑删除、主键策略等下划线转驼峰等配置见 application.yml 中的 {@code mybatis-plus.*}。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 单页最大条数限制，防止恶意大分页拖垮数据库。
     */
    private static final long MAX_PAGE_LIMIT = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 乐观锁插件（实体字段使用 @Version 注解）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 防全表更新与删除插件（拦截没有 where 条件的 update/delete）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 分页插件：分页插件务必要放在最后添加
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(MAX_PAGE_LIMIT);
        // 超出最大页数不进行处理（默认返回空数据）
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }

}
