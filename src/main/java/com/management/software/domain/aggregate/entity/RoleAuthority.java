package com.management.software.domain.aggregate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色权限
 */
@Data
@TableName("role_authority")
public class RoleAuthority implements Serializable {

    private static final long serialVersionUID = 1L;

    //自增数据id
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    //角色ID
    private Integer roleId;
    //权限标识（助记符）
    private Integer mnemonic;
    //权限范围
    private String scope;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
