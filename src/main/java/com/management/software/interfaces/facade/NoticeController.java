package com.management.software.interfaces.facade;

import com.management.software.application.dto.AnnouncementQueryDTO;
import com.management.software.application.dto.AnnouncementVO;
import com.management.software.application.dto.ApiResult;
import com.management.software.application.dto.PageResult;
import com.management.software.application.service.AnnouncementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公司公告接口（员工只读入口）。
 *
 * <p>与 {@code /api/announcements} 的分工：那边是公告管理（增删改、发布/下线，能看全部状态），
 * 这边是「我的 → 公司公告」，只读、只看已发布。两个入口分开而不是靠前端传 {@code status=2}，
 * 是因为「员工只能看已发布」是业务规则，不能由调用方决定。
 */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final AnnouncementService announcementService;

    public NoticeController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    /** 已发布公告分页（关键字匹配标题）。 */
    @GetMapping
    public ApiResult<PageResult<AnnouncementVO>> page(AnnouncementQueryDTO query) {
        return ApiResult.ok(announcementService.publishedPage(query));
    }

    /** 已发布公告详情；未发布或不存在都按「不存在」处理。 */
    @GetMapping("/{id}")
    public ApiResult<AnnouncementVO> detail(@PathVariable Long id) {
        return ApiResult.ok(announcementService.publishedDetail(id));
    }

}
