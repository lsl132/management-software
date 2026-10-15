package com.gangtise.backend.domain.aggregate.entity;

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

}
