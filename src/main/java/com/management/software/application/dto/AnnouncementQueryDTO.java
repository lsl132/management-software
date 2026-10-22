package com.management.software.application.dto;

import lombok.Data;

/**
 * 公告分页查询条件（GET 查询参数绑定）。
 */
@Data
public class AnnouncementQueryDTO {

    /** 页码，从 1 开始。 */
    private Integer current = 1;

    /** 每页条数，服务端会限制在 1~100。 */
    private Integer size = 10;

    /** 关键字，模糊匹配标题。 */
    private String keyword;

    /** 状态，见 AnnouncementStatusEnum；为空表示全部。 */
    private Integer status;

}
