package com.gangtise.backend.domain.aggregate;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 结算薪资
 */
@Data
@TableName("settlement_salary")
public class SettlementSalary implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String workNo;

    private String cycleScope;

    private Double attendanceNum;

    private Double leave;
    //基本工资（固定 3000）
    private Double baseSalary;
    //岗位工资（占比35%）
    private Double rankSalary;
    //绩效工资(占比25%)
    private Double dayPerformanceSalary;
    //补贴工资（月工作日 * 日薪 - 岗位 - 绩效 - 基本）
    private Double daySubsidySalary;
    //加班工资（加班小时 * 日薪 / 8）
    private Double extraSalary;
    //奖金/提成
    private Double bonus;
    //所得税
    private Double tax;
    //养老保险
    private Double insurance;
    //公积金
    private Double accumulationFund;
    //实际所得
    private Double actual;
    //状态
    private Integer status;
    //修改时间
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
