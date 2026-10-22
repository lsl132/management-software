package com.management.software.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 员工薪酬保存入参（一人一行，整体覆盖）。
 *
 * <p>三个金额用 {@link BigDecimal} 而不是 {@code Double}：这是钱，入参精度必须由调用方明确，
 * 不能让它先掉进二进制浮点再落库。落库前统一 setScale(2) 与列类型 {@code numeric(18,2)} 对齐。
 *
 * <p>三个金额都**不可缺省**：0 表示「该项不适用」是合法语义，所以不能用「没传 = 0」来兜底——
 * 那会让客户端漏传一个字段变成静默清零。宁可 400。
 */
@Data
public class EmployeeSalarySaveDTO {

    /** 币种，见 CurrencyEnum；按白名单校验，不在这里写死取值范围。 */
    @NotBlank(message = "不能为空")
    @Size(max = 8, message = "长度不能超过 8")
    private String currency;

    /** 月薪。 */
    @NotNull(message = "不能为空")
    @DecimalMin(value = "0", message = "不能为负数")
    @Digits(integer = 16, fraction = 2, message = "最多 16 位整数、2 位小数")
    private BigDecimal monthSalary;

    /** 年薪。 */
    @NotNull(message = "不能为空")
    @DecimalMin(value = "0", message = "不能为负数")
    @Digits(integer = 16, fraction = 2, message = "最多 16 位整数、2 位小数")
    private BigDecimal yearSalary;

    /** 日薪。 */
    @NotNull(message = "不能为空")
    @DecimalMin(value = "0", message = "不能为负数")
    @Digits(integer = 16, fraction = 2, message = "最多 16 位整数、2 位小数")
    private BigDecimal daySalary;

}
