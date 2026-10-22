package com.management.software.application.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 员工薪酬明细（供员工页的薪酬弹窗回显）。
 *
 * <p>{@code currencyText} 是币种的中文描述，服务端拼好下发：让前端自己维护一份
 * 「码值 → 中文」的映射迟早会与后端枚举漂移。
 */
@Data
public class EmployeeSalaryVO {

    private Integer employeeId;
    private String workNo;
    private String employeeName;

    private String currency;
    private String currencyText;

    private BigDecimal monthSalary;
    private BigDecimal yearSalary;
    private BigDecimal daySalary;

    /** 首次设置时间。 */
    private LocalDateTime createTime;
    /** 最后修改时间。 */
    private LocalDateTime updateTime;
    /** 最后修改人（登录标识）。 */
    private String updateBy;

}
