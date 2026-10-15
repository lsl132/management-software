package com.gangtise.backend.infrastructure.enums;

import lombok.Getter;

/**
 * 公告状态，对应 announcement.status。
 *
 * <p>生命周期：草稿（可继续编辑）→ 已发布（对外可见，写入发布时间）→ 已下线（不再展示，保留历史）。
 */
@Getter
public enum AnnouncementStatusEnum {

    /** 草稿：仅作者可见，publish_time 为空 */
    DRAFT(1, "草稿"),
    /** 已发布：对外可见，publish_time 记录发布时刻 */
    PUBLISHED(2, "已发布"),
    /** 已下线：不再展示，数据保留 */
    OFFLINE(3, "已下线");

    private final int value;
    private final String description;

    AnnouncementStatusEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (AnnouncementStatusEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

    /** 是否草稿。 */
    public static boolean isDraft(Integer value) {
        return value != null && value == DRAFT.value;
    }

    /** 是否已发布。 */
    public static boolean isPublished(Integer value) {
        return value != null && value == PUBLISHED.value;
    }

    /** 是否已下线。 */
    public static boolean isOffline(Integer value) {
        return value != null && value == OFFLINE.value;
    }

}
