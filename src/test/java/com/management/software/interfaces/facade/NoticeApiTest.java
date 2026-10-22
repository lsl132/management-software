package com.management.software.interfaces.facade;

import com.management.software.domain.aggregate.entity.Announcement;
import com.management.software.infrastructure.enums.AnnouncementStatusEnum;
import com.management.software.infrastructure.repository.mapper.AnnouncementMapper;
import com.management.software.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公司公告接口测试（员工只读入口 {@code /api/notices}）。
 *
 * <p>核心契约只有一条：**未发布的公告对外不存在**。草稿与已下线既不进列表，也不让按 id 查到——
 * 「前端没传 status」不是权限边界，服务端必须自己钉死。
 */
class NoticeApiTest extends ApiTestSupport {

    /** 标题前缀。刻意不含下划线：{@code _} 在 SQL LIKE 里是单字符通配符，会让关键字过滤失准。 */
    private static final String PREFIX = "ITNOTICE";

    @Autowired
    private AnnouncementMapper announcementMapper;

    private Long create(String suffix, AnnouncementStatusEnum status) {
        Announcement announcement = new Announcement();
        announcement.setTitle(PREFIX + suffix);
        announcement.setContent("<p>" + PREFIX + suffix + " 的正文</p>");
        announcement.setStatus(status.getValue());
        announcement.setAuthor("测试发布人");
        // 只有已发布才有发布时间，与线上写入逻辑一致
        announcement.setPublishTime(status == AnnouncementStatusEnum.PUBLISHED ? LocalDateTime.now() : null);
        announcementMapper.insert(announcement);
        return announcement.getId();
    }

    @Test
    @DisplayName("列表只含已发布：草稿与已下线都不出现")
    void listsOnlyPublished() throws Exception {
        MockHttpSession session = login();
        create("A", AnnouncementStatusEnum.DRAFT);
        create("B", AnnouncementStatusEnum.PUBLISHED);
        create("C", AnnouncementStatusEnum.OFFLINE);

        mockMvc.perform(get("/api/notices").session(session).param("keyword", PREFIX))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value(PREFIX + "B"))
                .andExpect(jsonPath("$.data.records[0].author").value("测试发布人"));
    }

    @Test
    @DisplayName("请求里传 status 也翻不出草稿")
    void statusParamCannotExposeDrafts() throws Exception {
        MockHttpSession session = login();
        create("D", AnnouncementStatusEnum.DRAFT);
        create("E", AnnouncementStatusEnum.OFFLINE);

        mockMvc.perform(get("/api/notices").session(session)
                        .param("keyword", PREFIX)
                        .param("status", String.valueOf(AnnouncementStatusEnum.DRAFT.getValue())))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("未发布公告按「不存在」处理，按 id 也查不到")
    void unpublishedDetailHidden() throws Exception {
        MockHttpSession session = login();
        Long draftId = create("F", AnnouncementStatusEnum.DRAFT);
        Long offlineId = create("G", AnnouncementStatusEnum.OFFLINE);

        mockMvc.perform(get("/api/notices/" + draftId).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("公告不存在或已下线"));

        mockMvc.perform(get("/api/notices/" + offlineId).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("公告不存在或已下线"));
    }

    @Test
    @DisplayName("已发布公告详情可读，正文按原样返回")
    void publishedDetailReadable() throws Exception {
        MockHttpSession session = login();
        Long id = create("H", AnnouncementStatusEnum.PUBLISHED);

        mockMvc.perform(get("/api/notices/" + id).session(session))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value(PREFIX + "H"))
                .andExpect(jsonPath("$.data.content").value("<p>" + PREFIX + "H 的正文</p>"))
                .andExpect(jsonPath("$.data.statusText").value("已发布"))
                .andExpect(jsonPath("$.data.publishTime").isNotEmpty());
    }

    @Test
    @DisplayName("未登录不能看公司公告")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/notices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

}
