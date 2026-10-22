package com.management.software.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.management.software.domain.aggregate.User;

/**
 * 用户 Mapper。
 *
 * <p>表名 {@code user} 为 PostgreSQL 保留字，实体上以 {@code @TableName("\"user\"")} 处理。
 */
public interface UserMapper extends BaseMapper<User> {
}
