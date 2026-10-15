package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gangtise.backend.application.dto.AnnouncementQueryDTO;
import com.gangtise.backend.application.dto.AnnouncementSaveDTO;
import com.gangtise.backend.application.dto.AnnouncementVO;
import com.gangtise.backend.application.dto.PageResult;
import com.gangtise.backend.domain.aggregate.entity.Announcement;
import com.gangtise.backend.infrastructure.enums.AnnouncementStatusEnum;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.repository.mapper.AnnouncementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告应用服务：公告增删改查 + 发布/下线。
 *
 * <p>两条被刻意固定的规则：
 * <ul>
 *   <li><b>作者由服务端写入</b>：取当前登录用户名，不接受前端传入，避免伪造署名；</li>
 *   <li><b>发布时间只在首次发布时写入</b>：之后编辑、下线、重新发布都不会改动它，
 *       保证「这条公告是什么时候发出去的」始终可追溯。</li>
 * </ul>
 */
@Service
public class AnnouncementService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    /** 欢迎页「发布的公告」默认条数。 */
    public static final int DEFAULT_RECENT_LIMIT = 10;
    private static final int MAX_RECENT_LIMIT = 50;

    private final AnnouncementMapper announcementMapper;

    public AnnouncementService(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    /** 分页查询，支持标题模糊与状态过滤（后台按创建时间倒序）。 */
    public PageResult<AnnouncementVO> page(AnnouncementQueryDTO query) {
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1L : query.getCurrent();
        long size = query.getSize() == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Announcement::getTitle, query.getKeyword().trim());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Announcement::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Announcement::getCreateTime).orderByDesc(Announcement::getId);

        Page<Announcement> page = announcementMapper.selectPage(new Page<>(current, size), wrapper);
        List<AnnouncementVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 最近的已发布公告（按发布时间倒序），供欢迎页展示。
     */
    public List<AnnouncementVO> recentPublished(Integer limit) {
        int size = limit == null ? DEFAULT_RECENT_LIMIT : Math.min(Math.max(limit, 1), MAX_RECENT_LIMIT);
        List<Announcement> records = announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, AnnouncementStatusEnum.PUBLISHED.getValue())
                .orderByDesc(Announcement::getPublishTime)
                .orderByDesc(Announcement::getId)
                .last("limit " + size));
        return records.stream().map(this::toVO).toList();
    }

    public AnnouncementVO detail(Long id) {
        return toVO(require(id));
    }

    /**
     * 新增公告。
     *
     * @param author 作者姓名，由控制层从会话取当前登录用户
     * @return 新增记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(AnnouncementSaveDTO dto, String author) {
        int status = dto.getStatus() == null ? AnnouncementStatusEnum.DRAFT.getValue() : dto.getStatus();

        Announcement announcement = new Announcement();
        announcement.setTitle(dto.getTitle().trim());
        announcement.setContent(dto.getContent().trim());
        announcement.setAttachment(trimToNull(dto.getAttachment()));
        announcement.setStatus(status);
        announcement.setPublishTime(AnnouncementStatusEnum.isPublished(status) ? LocalDateTime.now() : null);
        announcement.setAuthor(StringUtils.hasText(author) ? author.trim() : "系统");
        announcementMapper.insert(announcement);
        return announcement.getId();
    }

    /**
     * 全量更新标题/内容/附件；{@code status} 为空表示不变更状态。
     *
     * <p>发布时间只在「原本未发布、本次改成已发布」时补写，其余情况保持原值。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AnnouncementSaveDTO dto) {
        Announcement existing = require(id);

        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setTitle(dto.getTitle().trim());
        announcement.setContent(dto.getContent().trim());
        announcement.setAttachment(trimToNull(dto.getAttachment()));

        int status = dto.getStatus() == null ? existing.getStatus() : dto.getStatus();
        announcement.setStatus(status);
        if (AnnouncementStatusEnum.isPublished(status) && existing.getPublishTime() == null) {
            announcement.setPublishTime(LocalDateTime.now());
        }
        announcementMapper.updateById(announcement);
    }

    /** 发布：草稿或已下线的公告可以发布，发布时间沿用首次发布的时间。 */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Announcement existing = require(id);
        if (AnnouncementStatusEnum.isPublished(existing.getStatus())) {
            throw new BizException("该公告已是发布状态，无需重复发布");
        }

        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setStatus(AnnouncementStatusEnum.PUBLISHED.getValue());
        announcement.setPublishTime(existing.getPublishTime() == null ? LocalDateTime.now() : existing.getPublishTime());
        announcementMapper.updateById(announcement);
    }

    /** 下线：只有已发布的公告才能下线，数据保留、发布时间不回退。 */
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id) {
        Announcement existing = require(id);
        if (!AnnouncementStatusEnum.isPublished(existing.getStatus())) {
            throw new BizException("只有已发布的公告才能下线");
        }

        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setStatus(AnnouncementStatusEnum.OFFLINE.getValue());
        announcementMapper.updateById(announcement);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        announcementMapper.deleteById(id);
    }

    private Announcement require(Long id) {
        Announcement announcement = id == null ? null : announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BizException("公告不存在或已被删除");
        }
        return announcement;
    }

    private AnnouncementVO toVO(Announcement announcement) {
        AnnouncementVO vo = new AnnouncementVO();
        vo.setId(announcement.getId());
        vo.setTitle(announcement.getTitle());
        vo.setContent(announcement.getContent());
        vo.setAttachment(announcement.getAttachment());
        vo.setStatus(announcement.getStatus());
        vo.setStatusText(AnnouncementStatusEnum.descriptionOf(announcement.getStatus()));
        vo.setPublishTime(announcement.getPublishTime());
        vo.setCreateTime(announcement.getCreateTime());
        vo.setAuthor(announcement.getAuthor());
        return vo;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
