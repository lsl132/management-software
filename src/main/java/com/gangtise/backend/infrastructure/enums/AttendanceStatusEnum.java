package com.gangtise.backend.infrastructure.enums;

import lombok.Getter;

/**
 * 考勤统计状态，对应 {@code attendance.status}。
 *
 * <p>由 {@code AttendanceService#statisticsFor} 依据当天的「最早打卡」与「最晚打卡」两个时刻判定：
 * <ul>
 *   <li>最早打卡<b>晚于</b>上班时间 → 迟到（等于上班时间不算）；</li>
 *   <li>最晚打卡<b>早于</b>下班时间 → 早退（等于下班时间不算）；</li>
 *   <li>两者都中 → 迟到且早退。</li>
 * </ul>
 * 当天一条有效打卡都没有记 {@link #ABSENT}，此时 {@code attendance.first}/{@code last} 均为 null，
 * 与「有打卡但时间异常」区分开——所以缺卡不能用时间字段表达，必须占一个状态码。
 *
 * <p>迟到/早退的判定基准（上下班时间）见 {@code app.attendance.work-start} / {@code work-end}，
 * 不是硬编码常量：不同公司、不同季节作息不同。
 */
@Getter
public enum AttendanceStatusEnum {

    /** 缺卡：当天没有任何有效打卡记录 */
    ABSENT(0, "缺卡"),
    /** 正常：在上下班时间区间内打了卡 */
    NORMAL(1, "正常"),
    /** 迟到：最早打卡晚于上班时间 */
    LATE(2, "迟到"),
    /** 早退：最晚打卡早于下班时间 */
    EARLY_LEAVE(3, "早退"),
    /** 迟到且早退：两头都没踩上 */
    LATE_AND_EARLY_LEAVE(4, "迟到且早退");

    private final int value;
    private final String description;

    AttendanceStatusEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 由「是否迟到」「是否早退」两个判定归并出状态码。
     *
     * <p>调用方只负责比较时间，组合规则集中在这里，避免各处自己写 if 分支写岔。
     */
    public static AttendanceStatusEnum of(boolean late, boolean earlyLeave) {
        if (late && earlyLeave) {
            return LATE_AND_EARLY_LEAVE;
        }
        if (late) {
            return LATE;
        }
        if (earlyLeave) {
            return EARLY_LEAVE;
        }
        return NORMAL;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (AttendanceStatusEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

}
