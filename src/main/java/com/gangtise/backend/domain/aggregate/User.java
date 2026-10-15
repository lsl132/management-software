package com.gangtise.backend.domain.aggregate;

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
 * 用户实体类
 *
 * <p>可选字段（手机号 / 邮箱 / 角色）统一使用 {@code updateStrategy = FieldStrategy.ALWAYS}：
 * 用户编辑表单按全量覆盖处理，传 null 即清空（对应 {@code UserService#update}）。
 * 因此直接调用 Mapper 做「局部更新」（如冻结、设置角色）时必须用 {@code LambdaUpdateWrapper}
 * 显式指定列，否则未传字段会被一并置空。
 *
 * <p>{@code username} 是**展示名**，登录以 {@code user_account.identifier} 为准
 * （见 {@code AuthService#login}），两者可不同。
 */
@Data
@TableName("\"user\"")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    //自增数据id
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    //用户名（展示名）
    private String username;
    //手机号
    @TableField(value = "mobile", updateStrategy = FieldStrategy.ALWAYS)
    private String mobile;
    //邮箱
    @TableField(value = "email", updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    //角色ID
    @TableField(value = "role_id", updateStrategy = FieldStrategy.ALWAYS)
    private Integer roleId;
    //状态 见UserStatusEnum
    private Integer status;
    //更改时间
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
