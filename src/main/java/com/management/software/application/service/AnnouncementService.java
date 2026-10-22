package com.management.software.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.management.software.application.dto.AnnouncementQueryDTO;
import com.management.software.application.dto.AnnouncementSaveDTO;
import com.management.software.application.dto.AnnouncementVO;
import com.management.software.application.dto.PageResult;
import com.management.software.domain.aggregate.entity.Announcement;
import com.management.software.infrastructure.enums.AnnouncementStatusEnum;
import com.management.software.infrastructure.exception.BizException;
import com.management.software.infrastructure.repository.mapper.AnnouncementMapper;
import com.management.software.infrastructure.utils.RichTextUtils;
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
    /** 正文允许的最大纯文字长度（不含排版标记），与前端编辑器的提示口径一致。 */
    private static final int MAX_CONTENT_TEXT_LENGTH = RichTextUtils.MAX_TEXT_LENGTH;
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
     * 员工视角的分页（导航「我的 → 公司公告」）：只列「已发布」，按发布时间倒序。
     *
     * <p><b>刻意忽略请求里的 status</b>：这个入口是给员工看的，能看到的只有已发布内容。
     * 若沿用 {@link #page} 的过滤逻辑，调用方传 {@code status=1} 就能翻出全公司的草稿——
     * 「前端没传」不是权限边界。
     */
    public PageResult<AnnouncementVO> publishedPage(AnnouncementQueryDTO query) {
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1L : query.getCurrent();
        long size = query.getSize() == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, AnnouncementStatusEnum.PUBLISHED.getValue());
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Announcement::getTitle, query.getKeyword().trim());
        }
        // 按发布时间而不是创建时间：草稿放了半个月才发出去，员工该看到的是「发放时间」的顺序
        wrapper.orderByDesc(Announcement::getPublishTime).orderByDesc(Announcement::getId);

        Page<Announcement> page = announcementMapper.selectPage(new Page<>(current, size), wrapper);
        List<AnnouncementVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 员工视角的详情：未发布的一律当作不存在。
     *
     * <p>「不存在」与「你没权限看」返回同一句话，是为了不让人拿 id 试探出哪些草稿存在。
     */
    public AnnouncementVO publishedDetail(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null || !AnnouncementStatusEnum.isPublished(announcement.getStatus())) {
            throw new BizException("公告不存在或已下线");
        }
        return toVO(announcement);
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
        announcement.setContent(normalizeContent(dto.getContent()));
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
        announcement.setContent(normalizeContent(dto.getContent()));
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

    /**
     * 正文落库前的清洗与校验：先按白名单清洗 HTML，再按**纯文字**判空与限长。
     *
     * <p>为什么必须在服务端做：正文以 HTML 存储，前端 DOMPurify 只保护「页面自己渲染」这条路，
     * 直接调接口（或导入数据）可以绕过它，所以最终防线在服务端。
     *
     * <p>为什么按纯文字计数：排版标记不该占用户额度——同样一段文字，加粗与否不该影响能否提交；
     * 而且 DTO 上 100000 的上限只是防滥用粗筛，真正的产品约束（20000 字）在这里落地。
     */
    private static String normalizeContent(String rawContent) {
        String html = RichTextUtils.sanitize(rawContent);
        String plain = RichTextUtils.toPlainText(html);
        if (plain.isEmpty()) {
            throw new BizException("公告内容不能为空");
        }
        if (plain.length() > MAX_CONTENT_TEXT_LENGTH) {
            throw new BizException("公告正文最长 " + MAX_CONTENT_TEXT_LENGTH + " 字，当前 " + plain.length() + " 字");
        }
        return html;
    }

}
