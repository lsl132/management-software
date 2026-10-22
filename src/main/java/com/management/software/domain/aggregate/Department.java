package com.management.software.domain.aggregate;

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
 * 部门
 *
 * <p>可空字段统一使用 {@code updateStrategy = FieldStrategy.ALWAYS}：编辑表单保存时按全量覆盖处理，
 * 传 null 即清空（如把「上级部门」清空为顶级部门），对应 {@code DepartmentService#update}。
 */
@Data
@TableName("department")
public class Department implements Serializable {

    private static final long serialVersionUID = 1L;

    //自增ID
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    //上级部门ID，null 表示顶级部门
    @TableField(value = "parent_id", updateStrategy = FieldStrategy.ALWAYS)
    private Integer parentId;
    //部门名称
    private String departName;
    //部门简介
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
