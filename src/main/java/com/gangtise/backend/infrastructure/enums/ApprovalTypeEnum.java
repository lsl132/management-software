package com.gangtise.backend.infrastructure.enums;

import lombok.Getter;

/**
 * 审批方式，对应 {@code workflow_node.approval_type}（仅审批节点有值）。
 *
 * <p><b>「直属上级」当前不可用</b>：它需要「员工的上级是谁」这份数据，而 {@code employee}
 * 表没有上级字段，{@code user} 与 {@code employee} 之间也没有关联列。即便配出来，
 * 运行时也找不到该由谁来点通过，只会得到一条永远推不动的规则。因此枚举里保留这个码值
 * （字典基线不残缺），但 {@link #isSupported} 返回 false，保存时直接拒绝并提示改用其它方式。
 */
@Getter
public enum ApprovalTypeEnum {

    /** 指定人员：approval_values 存 {@code "user".id}，多个人逗号分隔 */
    ASSIGNEE(1, "指定人员", true),
    /** 直属上级：依赖员工的上级字段，当前数据模型不支持 */
    SUPERIOR(2, "直属上级", false),
    /** 指定角色：approval_values 存 {@code role.id} */
    ROLE(3, "指定角色", true);

    private final int value;
    private final String description;
    /** 当前版本能否真正执行；false 的码值不允许被保存进规则 */
    private final boolean supported;

    ApprovalTypeEnum(int value, String description, boolean supported) {
        this.value = value;
        this.description = description;
        this.supported = supported;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (ApprovalTypeEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

    /** 码值是否是当前版本可执行的审批方式。 */
    public static boolean isSupported(Integer value) {
        if (value == null) {
            return false;
        }
        for (ApprovalTypeEnum item : values()) {
            if (item.value == value) {
                return item.supported;
            }
        }
        return false;
    }

    /** 该审批方式是否需要配置具体对象（人员 / 角色）；「直属上级」由组织关系推导，无需配置。 */
    public static boolean requiresValues(Integer value) {
        return isSupported(value);
    }

}
