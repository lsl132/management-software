package com.management.software.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告列表/详情返回结构。
 */
@Data
public class AnnouncementVO {

    private Long id;

    private String title;
    private String content;
    /** 附件路径或 URL，无附件为 null。 */
    private String attachment;

    private Integer status;
    /** 状态描述（草稿 / 已发布 / 已下线），由 AnnouncementStatusEnum 转换。 */
    private String statusText;

    /** 发布时间，草稿态为 null。 */
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    /** 作者姓名快照。 */
    private String author;

}
