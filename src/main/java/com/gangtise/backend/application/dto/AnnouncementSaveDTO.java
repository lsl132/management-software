package com.gangtise.backend.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告新增/修改入参。
 *
 * <p>作者不在入参里：由服务端取当前登录用户写入，避免前端伪造署名。
 *
 * <p>状态语义：新建时可为空（按草稿处理）；修改时为空表示**不变更状态**
 * （发布 / 下线请走 {@code POST /api/announcements/{id}/publish|offline}）。
 *
 * <p>{@code content} 是**富文本 HTML**（Quill 产出）：这里的长度上限只是防滥用的粗筛，
 * 真正的产品约束「纯文字不超过 20000 字」由 {@code AnnouncementService} 清洗后按纯文字判断。
 */
@Data
public class AnnouncementSaveDTO {

    @NotBlank(message = "不能为空")
    @Size(max = 200, message = "长度不能超过 200")
    private String title;

    @NotBlank(message = "不能为空")
    @Size(max = 100000, message = "长度不能超过 100000")
    private String content;

    /** 附件路径或 URL，可为空表示无附件。 */
    @Size(max = 255, message = "长度不能超过 255")
    private String attachment;

    /** 状态，见 AnnouncementStatusEnum：1 草稿 / 2 已发布 / 3 已下线。 */
    @Min(value = 1, message = "取值只能是 1 草稿 / 2 已发布 / 3 已下线")
    @Max(value = 3, message = "取值只能是 1 草稿 / 2 已发布 / 3 已下线")
    private Integer status;

}
