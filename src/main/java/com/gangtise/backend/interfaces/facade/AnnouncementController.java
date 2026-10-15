package com.gangtise.backend.interfaces.facade;

import com.gangtise.backend.application.dto.AnnouncementQueryDTO;
import com.gangtise.backend.application.dto.AnnouncementSaveDTO;
import com.gangtise.backend.application.dto.AnnouncementVO;
import com.gangtise.backend.application.dto.ApiResult;
import com.gangtise.backend.application.dto.PageResult;
import com.gangtise.backend.application.service.AnnouncementService;
import com.gangtise.backend.interfaces.facade.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公告管理接口（公告增删改查 + 发布/下线）。
 *
 * <p>作者由服务端从会话取当前登录用户写入，**不从请求体接收**，避免伪造署名。
 * 发布 / 下线用独立动词接口，前端在列表行上直接操作，无需把整篇内容回传。
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    /** 分页查询，支持标题模糊与状态过滤。 */
    @GetMapping
    public ApiResult<PageResult<AnnouncementVO>> page(AnnouncementQueryDTO query) {
        return ApiResult.ok(announcementService.page(query));
    }

    /** 最近的已发布公告（按发布时间倒序），供欢迎页展示。 */
    @GetMapping("/recent")
    public ApiResult<List<AnnouncementVO>> recent(@RequestParam(required = false) Integer limit) {
        return ApiResult.ok(announcementService.recentPublished(limit));
    }

    /** 详情。 */
    @GetMapping("/{id}")
    public ApiResult<AnnouncementVO> detail(@PathVariable Long id) {
        return ApiResult.ok(announcementService.detail(id));
    }

    /** 新增，返回新增记录的ID。 */
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody AnnouncementSaveDTO dto, HttpSession session) {
        String author = SessionUserSupport.require(session).getUsername();
        return ApiResult.ok(announcementService.create(dto, author));
    }

    /** 修改标题/内容/附件；请求体不带 status 时不改变发布状态。 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody AnnouncementSaveDTO dto) {
        announcementService.update(id, dto);
        return ApiResult.ok();
    }

    /** 发布（草稿 / 已下线均可发布）。 */
    @PostMapping("/{id}/publish")
    public ApiResult<Void> publish(@PathVariable Long id) {
        announcementService.publish(id);
        return ApiResult.ok();
    }

    /** 下线（仅已发布的公告可下线）。 */
    @PostMapping("/{id}/offline")
    public ApiResult<Void> offline(@PathVariable Long id) {
        announcementService.offline(id);
        return ApiResult.ok();
    }

    /** 删除。 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ApiResult.ok();
    }

}
