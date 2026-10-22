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
 * 员工
 *
 * <p>可选字段统一使用 {@code updateStrategy = FieldStrategy.ALWAYS}：编辑表单保存时按全量覆盖处理，
 * 传 null 即清空该字段（对应 {@code EmployeeService#update}）。
 * 因此直接调用 Mapper 做「部分更新」时必须自行补齐字段，否则会把未传的字段置空。
 */
@Data
@TableName("employee")
public class Employee implements Serializable {

    private static final long serialVersionUID = 1L;

    // 自增id
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    // 员工 工号
    private String workNo;
    // 员工名称
    private String name;
    // 别名
    @TableField(value = "alias", updateStrategy = FieldStrategy.ALWAYS)
    private String alias;
    //性别 1 男 2女
    @TableField(value = "gender", updateStrategy = FieldStrategy.ALWAYS)
    private Integer gender;
    //身份证号
    @TableField(value = "identity_card", updateStrategy = FieldStrategy.ALWAYS)
    private String identityCard;
    //电话号码
    @TableField(value = "telephone", updateStrategy = FieldStrategy.ALWAYS)
    private String telephone;
    //电子邮箱
    @TableField(value = "email", updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    //居住地址
    @TableField(value = "address", updateStrategy = FieldStrategy.ALWAYS)
    private String address;
    //状态 见EmployeeStatusEnum
    private Integer status;
    //生日 yyyyMMdd
    @TableField(value = "birthday", updateStrategy = FieldStrategy.ALWAYS)
    private Integer birthday;
    //学历 见EmployeeEducationEnum
    @TableField(value = "education", updateStrategy = FieldStrategy.ALWAYS)
    private Integer education;
    //职级
    @TableField(value = "rank", updateStrategy = FieldStrategy.ALWAYS)
    private Integer rank;
    //所属部门ID
    @TableField(value = "department", updateStrategy = FieldStrategy.ALWAYS)
    private Integer department;
    //更新时间
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
