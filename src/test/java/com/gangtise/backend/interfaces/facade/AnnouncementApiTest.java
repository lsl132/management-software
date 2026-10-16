package com.gangtise.backend.interfaces.facade;

import com.gangtise.backend.domain.aggregate.entity.Announcement;
import com.gangtise.backend.infrastructure.enums.AnnouncementStatusEnum;
import com.gangtise.backend.infrastructure.repository.mapper.AnnouncementMapper;
import com.gangtise.backend.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公告管理接口集成测试。
 *
 * <p>全部用例自建数据（前缀 {@code IT-公告-}），配合 {@code @Transactional} 回滚，不污染本地库。
 */
class AnnouncementApiTest extends ApiTestSupport {

    private static final String PREFIX = "IT-公告-";

    @Autowired
    private AnnouncementMapper announcementMapper;

    @Test
    @DisplayName("未登录不能访问公告接口")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/announcements"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("公告增删改查全流程：草稿 → 发布 → 下线 → 删除")
    void crudFlow() throws Exception {
        MockHttpSession session = login();

        long id = create(session, PREFIX + "流程", "正文内容", null, null);
        Announcement draft = announcementMapper.selectById(id);
        assertNotNull(draft, "新增后应能查到公告");
        assertEquals(AnnouncementStatusEnum.DRAFT.getValue(), draft.getStatus(), "不传状态时默认草稿");
        assertNull(draft.getPublishTime(), "草稿态发布时间应为空");

        // 全局 Jackson 配了 default-property-inclusion: non_null，空值字段不会出现在返回体里
        mockMvc.perform(get("/api/announcements/" + id).session(session))
                .andExpect(jsonPath("$.data.title").value(PREFIX + "流程"))
                .andExpect(jsonPath("$.data.statusText").value("草稿"))
                .andExpect(jsonPath("$.data.publishTime").doesNotExist());

        // 编辑：补附件
        mockMvc.perform(put("/api/announcements/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", PREFIX + "流程（改）",
                                "content", "正文内容已更新",
                                "attachment", "/files/announcement/it.pdf"))))
                .andExpect(jsonPath("$.code").value(0));

        Announcement updated = announcementMapper.selectById(id);
        assertEquals(PREFIX + "流程（改）", updated.getTitle());
        assertEquals("/files/announcement/it.pdf", updated.getAttachment());
        assertEquals(AnnouncementStatusEnum.DRAFT.getValue(), updated.getStatus(), "编辑不带状态时不应改变发布状态");

        // 发布
        mockMvc.perform(post("/api/announcements/" + id + "/publish").session(session))
                .andExpect(jsonPath("$.code").value(0));
        Announcement published = announcementMapper.selectById(id);
        assertEquals(AnnouncementStatusEnum.PUBLISHED.getValue(), published.getStatus());
        assertNotNull(published.getPublishTime(), "发布时应写入发布时间");
        LocalDateTime firstPublishTime = published.getPublishTime();

        // 编辑已发布的公告：发布时间不能被改写
        mockMvc.perform(put("/api/announcements/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", PREFIX + "流程（再改）",
                                "content", "正文内容再次更新"))))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(firstPublishTime, announcementMapper.selectById(id).getPublishTime(),
                "发布时间只在首次发布时确定，编辑不应改动");

        // 下线
        mockMvc.perform(post("/api/announcements/" + id + "/offline").session(session))
                .andExpect(jsonPath("$.code").value(0));
        Announcement offline = announcementMapper.selectById(id);
        assertEquals(AnnouncementStatusEnum.OFFLINE.getValue(), offline.getStatus());
        assertNotNull(offline.getPublishTime(), "下线后保留原发布时间，便于追溯");

        // 删除
        mockMvc.perform(delete("/api/announcements/" + id).session(session))
                .andExpect(jsonPath("$.code").value(0));
        assertNull(announcementMapper.selectById(id), "删除后应查不到公告");
    }

    @Test
    @DisplayName("直接发布建单会写入发布时间")
    void createPublishedWritesPublishTime() throws Exception {
        MockHttpSession session = login();
        long id = create(session, PREFIX + "直接发布", "正文", null,
                AnnouncementStatusEnum.PUBLISHED.getValue());

        Announcement record = announcementMapper.selectById(id);
        assertEquals(AnnouncementStatusEnum.PUBLISHED.getValue(), record.getStatus());
        assertNotNull(record.getPublishTime());
    }

    @Test
    @DisplayName("作者取当前登录账号，请求体传作者也不生效")
    void authorComesFromSession() throws Exception {
        MockHttpSession session = login();

        Map<String, Object> body = new HashMap<>();
        body.put("title", PREFIX + "作者");
        body.put("content", "正文");
        body.put("author", "伪造的作者");

        MvcResult result = mockMvc.perform(post("/api/announcements").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        long id = readJson(result).path("data").asLong();
        assertEquals(TEST_USERNAME, announcementMapper.selectById(id).getAuthor(),
                "作者应由服务端按登录用户写入");
    }

    @Test
    @DisplayName("标题或内容为空被拒绝")
    void blankFieldsRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/announcements").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "   ",
                                "content", "正文"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("title 不能为空"));

        mockMvc.perform(post("/api/announcements").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", PREFIX + "缺正文",
                                "content", ""))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("content 不能为空"));
    }

    @Test
    @DisplayName("非法状态值被拒绝")
    void invalidStatusRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/announcements").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", PREFIX + "非法状态",
                                "content", "正文",
                                "status", 9))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("status 取值只能是 1 草稿 / 2 已发布 / 3 已下线"));
    }

    @Test
    @DisplayName("重复发布、下线草稿都被拒绝")
    void invalidStatusTransitionRejected() throws Exception {
        MockHttpSession session = login();
        long draftId = create(session, PREFIX + "状态流转", "正文", null, null);

        mockMvc.perform(post("/api/announcements/" + draftId + "/offline").session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只有已发布的公告才能下线"));

        mockMvc.perform(post("/api/announcements/" + draftId + "/publish").session(session))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/announcements/" + draftId + "/publish").session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该公告已是发布状态，无需重复发布"));
    }

    @Test
    @DisplayName("操作不存在的公告被拒绝")
    void missingAnnouncementRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/announcements/99999999").session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("公告不存在或已被删除"));

        mockMvc.perform(delete("/api/announcements/99999999").session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("公告不存在或已被删除"));
    }

    @Test
    @DisplayName("列表支持标题模糊与状态过滤")
    void pageFiltersWork() throws Exception {
        MockHttpSession session = login();
        create(session, PREFIX + "筛选-草稿", "正文", null, null);
        create(session, PREFIX + "筛选-发布", "正文", null, AnnouncementStatusEnum.PUBLISHED.getValue());

        mockMvc.perform(get("/api/announcements").session(session)
                        .param("keyword", PREFIX + "筛选-")
                        .param("size", "50"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[?(@.title == '" + PREFIX + "筛选-草稿')].statusText",
                        hasItem("草稿")))
                .andExpect(jsonPath("$.data.records[?(@.title == '" + PREFIX + "筛选-发布')].statusText",
                        hasItem("已发布")));

        mockMvc.perform(get("/api/announcements").session(session)
                        .param("keyword", PREFIX + "筛选-")
                        .param("status", String.valueOf(AnnouncementStatusEnum.PUBLISHED.getValue()))
                        .param("size", "50"))
                .andExpect(jsonPath("$.data.records[?(@.title == '" + PREFIX + "筛选-发布')].statusText",
                        hasItem("已发布")))
                .andExpect(jsonPath("$.data.records[?(@.title == '" + PREFIX + "筛选-草稿')].statusText",
                        empty()));
    }

    @Test
    @DisplayName("最近公告接口只返回已发布公告，且按发布时间倒序")
    void recentReturnsOnlyPublishedInDescOrder() throws Exception {
        MockHttpSession session = login();
        create(session, PREFIX + "最近-草稿", "正文", null, null);

        long older = create(session, PREFIX + "最近-早发布", "正文", null,
                AnnouncementStatusEnum.PUBLISHED.getValue());
        long newer = create(session, PREFIX + "最近-晚发布", "正文", null,
                AnnouncementStatusEnum.PUBLISHED.getValue());

        // 手工改写发布时间，确保排序断言稳定（同一秒内创建的记录无法只靠时间区分）
        Announcement olderRecord = announcementMapper.selectById(older);
        olderRecord.setPublishTime(LocalDateTime.now().minusDays(2));
        announcementMapper.updateById(olderRecord);
        Announcement newerRecord = announcementMapper.selectById(newer);
        newerRecord.setPublishTime(LocalDateTime.now().minusMinutes(1));
        announcementMapper.updateById(newerRecord);

        MvcResult result = mockMvc.perform(get("/api/announcements/recent").session(session)
                        .param("limit", "50"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        var titles = readJson(result).path("data").findValuesAsText("title");
        var statuses = readJson(result).path("data").findValuesAsText("statusText");
        assertTrue(titles.contains(PREFIX + "最近-晚发布"));
        assertTrue(titles.contains(PREFIX + "最近-早发布"));
        assertFalse(titles.contains(PREFIX + "最近-草稿"), "草稿不应出现在已发布公告里");
        assertTrue(statuses.stream().allMatch("已发布"::equals), "只应返回已发布的公告");

        int newerIndex = titles.indexOf(PREFIX + "最近-晚发布");
        int olderIndex = titles.indexOf(PREFIX + "最近-早发布");
        assertTrue(newerIndex < olderIndex, "应按发布时间倒序，晚发布的排在前面");
    }

    @Test
    @DisplayName("富文本正文按白名单落库：排版标签保留，脚本与事件属性被清洗")
    void richTextContentSanitizedOnSave() throws Exception {
        MockHttpSession session = login();

        long id = create(session, PREFIX + "富文本",
                "<p>正文<strong>加粗</strong></p><script>alert('xss')</script>"
                        + "<p onclick=\"steal()\">带事件属性</p>",
                null, null);

        String stored = announcementMapper.selectById(id).getContent();
        assertEquals("<p>正文<strong>加粗</strong></p><p>带事件属性</p>", stored,
                "白名单内的排版标签应原样保留，白名单外的标签与属性应被清洗");
    }

    @Test
    @DisplayName("绕过页面直接提交脚本也不会入库（前端清洗挡不住这条路，靠服务端兜底）")
    void scriptContentStrippedServerSide() throws Exception {
        MockHttpSession session = login();

        long id = create(session, PREFIX + "直接提交脚本",
                "<p>正常正文</p><img src=x onerror=alert(1)>", null, null);

        String stored = announcementMapper.selectById(id).getContent();
        assertFalse(stored.contains("script"), "脚本标签必须被剥掉");
        assertFalse(stored.contains("onerror"), "事件属性必须被剥掉");
        assertFalse(stored.contains("<img"), "不在白名单的标签必须被剥掉");
        assertTrue(stored.contains("正常正文"), "白名单内的正文要保留");
    }

    @Test
    @DisplayName("清洗后没有实际文字的内容被拒绝")
    void contentEmptyAfterSanitizeRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/announcements").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", PREFIX + "空正文",
                                "content", "<script>alert(1)</script>"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("公告内容不能为空"));
    }

    @Test
    @DisplayName("正文按纯文字限长：排版标记不占字数")
    void contentLengthCountedByPlainText() throws Exception {
        MockHttpSession session = login();

        // 20001 字：超限，即使套了标签也拒绝
        mockMvc.perform(post("/api/announcements").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", PREFIX + "超长",
                                "content", "<p>" + "字".repeat(20001) + "</p>"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("公告正文最长 20000 字，当前 20001 字"));

        // 纯文字正好 20000 字：标签不该把额度吃掉
        long id = create(session, PREFIX + "临界长度",
                "<p><strong>" + "字".repeat(20000) + "</strong></p>", null, null);
        assertNotNull(announcementMapper.selectById(id), "纯文字 20000 字应当通过");
    }

    @Test
    @DisplayName("公告管理页正常渲染")
    void pageRenders() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/announcements").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("公告管理")))
                .andExpect(content().string(containsString("/js/announcements.js")))
                // 富文本：编辑器挂载点 + 本地内置的编辑器资源（不依赖外网）
                .andExpect(content().string(containsString("id=\"contentEditor\"")))
                .andExpect(content().string(containsString("/vendor/quill/quill.js")))
                .andExpect(content().string(containsString("/vendor/quill/quill.snow.css")))
                .andExpect(content().string(containsString("/vendor/dompurify/purify.min.js")))
                // 只读详情弹窗：列表里看不到正文，必须能打开详情查看
                .andExpect(content().string(containsString("id=\"announcementDetailModal\"")))
                .andExpect(content().string(containsString("id=\"announcementDetailContent\"")))
                .andExpect(content().string(containsString("id=\"announcementDetailAttachment\"")))
                .andExpect(content().string(containsString("公告详情")))
                // 详情底部要有「编辑」，从详情能直接进编辑（列表操作列在窄屏会被横向滚动藏起来）
                .andExpect(content().string(containsString("id=\"btnDetailEdit\"")));
    }

    @Test
    @DisplayName("编辑器静态资源属于放行目录（未登录也能取到，页面才能正常加载）")
    void editorAssetsArePublic() throws Exception {
        mockMvc.perform(get("/vendor/quill/quill.js")).andExpect(status().isOk());
        mockMvc.perform(get("/vendor/quill/quill.snow.css")).andExpect(status().isOk());
        mockMvc.perform(get("/vendor/dompurify/purify.min.js")).andExpect(status().isOk());
    }

    /* ------------------------------------------------------------------ 工具 */

    private long create(MockHttpSession session, String title, String content,
                        String attachment, Integer status) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("attachment", attachment);
        body.put("status", status);

        MvcResult result = mockMvc.perform(post("/api/announcements").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readJson(result).path("data").asLong();
    }

}
