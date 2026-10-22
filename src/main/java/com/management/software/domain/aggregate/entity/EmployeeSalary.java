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
 * 员工薪资,多个组成部分
 *
 * <p><b>一人一行</b>：业务键是 {@code work_no}，由唯一索引 {@code uk_employee_salary_work_no}
 * 兜底，同时是 {@code EmployeeSalaryMapper.upsert} 的冲突键。改薪酬是覆盖而不是追加，
 * 所以这里不需要「生效日期」——表里本来也没有这个维度，多行反而分不清哪条才是当前值。
 *
 * <p>金额用 {@code Double} 是既有选择（列类型是 {@code numeric(18,2)}，库里存的是精确两位小数）；
 * 接口边界统一走 {@code BigDecimal} 并显式 setScale(2)，避免二进制浮点把 1234.56 写成
 * 1234.5600000000001 落到前端。
 */
@Data
@TableName("employee_salary")
public class EmployeeSalary implements Serializable {

    private static final long serialVersionUID = 1L;

    //自增id
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    //员工工号
    private String workNo;
    // 币种 见CurrencyEnum
    private String currency;
    //月薪
    private Double monthSalary;
    //年薪
    private Double yearSalary;
    //日薪
    private Double daySalary;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    //最后修改时间
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    //最后修改人：user_account 的登录标识，不是可改的展示名
    private String updateBy;

}
