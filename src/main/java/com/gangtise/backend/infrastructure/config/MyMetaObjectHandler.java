package com.gangtise.backend.infrastructure.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充处理器。
 *
 * <p>实体字段加上 {@code @TableField(fill = FieldFill.INSERT)} /
 * {@code FieldFill.INSERT_UPDATE} / {@code FieldFill.UPDATE} 后即可自动填充。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final String FIELD_CREATE_TIME = "createTime";
    private static final String FIELD_UPDATE_TIME = "updateTime";
    private static final String FIELD_DELETED = "deleted";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, FIELD_CREATE_TIME, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, now);
        // 逻辑删除标识默认 0（未删除）
        this.strictInsertFill(metaObject, FIELD_DELETED, Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 注意：不能使用 strictUpdateFill —— strict 语义是「字段为 null 时才填充」，
        // 而更新场景下实体通常已持有从库里查出的旧 updateTime，会导致永远填不进去。
        // 这里改用 setFieldValByName 强制刷新；实体上没有该字段时会自动跳过。
        this.setFieldValByName(FIELD_UPDATE_TIME, LocalDateTime.now(), metaObject);
    }

}
