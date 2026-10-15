package com.gangtise.backend.domain.aggregate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 部门职级（职级隶属于部门：不同部门有各自的职级体系）。
 *
 * <p>{@code intro} 为可空字段，使用 {@code updateStrategy = ALWAYS} 支持「保存时清空说明」。
 */
@Data
@TableName("department_rank")
public class DepartmentRank implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String code;
    private Integer type;
    /** 归属部门，关联 department.id */
    private Integer department;
    @TableField(value = "intro", updateStrategy = FieldStrategy.ALWAYS)
    private String intro;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
