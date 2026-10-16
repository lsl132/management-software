package com.gangtise.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工打卡入参。
 *
 * <p><b>没有 punchTime 字段</b>：打卡时间一律由服务端时钟写入。允许客户端传时间看似方便
 * （离线设备补传），但代价是改一下本机时间就能伪造考勤，而事后从数据上根本看不出哪条是假的。
 * 需要补录历史考勤时应走带审计的独立入口，而不是让常规打卡接口接受任意时间。
 *
 * <p>同理没有 status：打卡产生的记录固定是「有效」，作废是另一个动作。
 */
@Data
public class AttendancePunchDTO {

    /** 员工工号，必须在 employee 表中存在且为在职（试用期 / 正式）。 */
    @NotBlank(message = "不能为空")
    @Size(max = 32, message = "长度不能超过 32")
    private String workNo;

    /** 打卡位置，如「总部 A 座 1F」「外勤：中关村」，可为空。 */
    @Size(max = 255, message = "长度不能超过 255")
    private String location;

}
