package com.management.software.interfaces.facade;

import com.management.software.domain.aggregate.entity.UserSchedule;
import com.management.software.infrastructure.repository.mapper.UserScheduleMapper;
import com.management.software.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
 * 我的日程接口集成测试。
 *
 * <p>时间统一落在 2027 年，避免与「当前月」相关的用例互相干扰。
 * 全部用例自建数据并随事务回滚，不污染本地库。
 */
class ScheduleApiTest extends ApiTestSupport {

    private static final String PREFIX = "IT-日程-";
    private static final String MONTH_MAR = "2027-03";
    private static final String MONTH_APR = "2027-04";

    /** 不属于任何登录用户的值，用于构造「别人的日程」。 */
    private static final int FOREIGN_USER_ID = 999999;

    @Autowired
    private UserScheduleMapper scheduleMapper;

    @Test
    @DisplayName("未登录不能访问日程接口")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("日程增删改查全流程")
    void crudFlow() throws Exception {
        MockHttpSession session = login();

        long id = create(session, PREFIX + "评审会", "2027-03-15 09:30:00", "2027-03-15 09:00:00",
                "季度评审");
        UserSchedule created = scheduleMapper.selectById(id);
        assertNotNull(created, "新增后应能查到日程");
        assertNotNull(created.getUserId(), "用户ID应由服务端写入");

        mockMvc.perform(get("/api/schedules").session(session).param("month", MONTH_MAR))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.id == '" + id + "')].title", hasItem(PREFIX + "评审会")));

        mockMvc.perform(get("/api/schedules/" + id).session(session))
                .andExpect(jsonPath("$.data.title").value(PREFIX + "评审会"))
                .andExpect(jsonPath("$.data.scheduleTime").value("2027-03-15 09:30:00"))
                .andExpect(jsonPath("$.data.remindTime").value("2027-03-15 09:00:00"));

        // 改标题 + 改时间 + 取消提醒（remindTime 传 null）
        mockMvc.perform(put("/api/schedules/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduleTime", "2027-03-16 14:00:00",
                                "title", PREFIX + "评审会（改）"))))
                .andExpect(jsonPath("$.code").value(0));

        UserSchedule updated = scheduleMapper.selectById(id);
        assertEquals(PREFIX + "评审会（改）", updated.getTitle());
        assertNull(updated.getRemindTime(), "不传提醒时间即取消提醒");
        assertNull(updated.getIntro(), "不传简介即清空简介");

        mockMvc.perform(delete("/api/schedules/" + id).session(session))
                .andExpect(jsonPath("$.code").value(0));
        assertNull(scheduleMapper.selectById(id), "删除后应查不到日程");
    }

    @Test
    @DisplayName("按月查询只返回该月日程，且按预约时间升序")
    void listByMonthOnlyReturnsThatMonth() throws Exception {
        MockHttpSession session = login();
        create(session, PREFIX + "三月-晚", "2027-03-20 15:00:00", null, null);
        create(session, PREFIX + "三月-早", "2027-03-02 08:00:00", null, null);
        create(session, PREFIX + "四月", "2027-04-02 08:00:00", null, null);

        MvcResult result = mockMvc.perform(get("/api/schedules").session(session).param("month", MONTH_MAR))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.title == '" + PREFIX + "四月')]", empty()))
                .andReturn();

        var titles = readJson(result).path("data").findValuesAsText("title");
        assertEquals(2, titles.size());
        assertEquals(PREFIX + "三月-早", titles.get(0), "应按预约时间升序");
        assertEquals(PREFIX + "三月-晚", titles.get(1));
    }

    @Test
    @DisplayName("关键字只过滤标题")
    void keywordFiltersTitle() throws Exception {
        MockHttpSession session = login();
        create(session, PREFIX + "关键字-命中", "2027-03-05 09:00:00", null, null);
        create(session, PREFIX + "关键字-无关", "2027-03-06 09:00:00", null, null);

        mockMvc.perform(get("/api/schedules").session(session)
                        .param("month", MONTH_MAR)
                        .param("keyword", "命中"))
                .andExpect(jsonPath("$.data[?(@.title == '" + PREFIX + "关键字-命中')].title",
                        hasItem(PREFIX + "关键字-命中")))
                .andExpect(jsonPath("$.data[?(@.title == '" + PREFIX + "关键字-无关')].title", empty()));
    }

    @Test
    @DisplayName("月份格式非法被拒绝")
    void invalidMonthRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/schedules").session(session).param("month", "2027-13"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("月份格式不正确，应为 yyyy-MM"));

        mockMvc.perform(get("/api/schedules").session(session).param("month", "202703"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("月份格式不正确，应为 yyyy-MM"));
    }

    @Test
    @DisplayName("看不到也改不了别人的日程")
    void crossUserAccessRejected() throws Exception {
        MockHttpSession session = login();

        UserSchedule foreign = new UserSchedule();
        foreign.setUserId(FOREIGN_USER_ID);
        foreign.setScheduleTime(java.time.LocalDateTime.of(2027, 3, 18, 10, 0));
        foreign.setTitle(PREFIX + "别人的日程");
        scheduleMapper.insert(foreign);
        long foreignId = foreign.getId();

        mockMvc.perform(get("/api/schedules/" + foreignId).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("日程不存在或不属于当前登录用户"));

        mockMvc.perform(put("/api/schedules/" + foreignId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduleTime", "2027-03-18 11:00:00",
                                "title", PREFIX + "篡改"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("日程不存在或不属于当前登录用户"));

        mockMvc.perform(delete("/api/schedules/" + foreignId).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("日程不存在或不属于当前登录用户"));

        assertEquals(PREFIX + "别人的日程", scheduleMapper.selectById(foreignId).getTitle(),
                "越权请求不应改动数据");

        // 别人的日程也不应出现在我的月列表里
        mockMvc.perform(get("/api/schedules").session(session).param("month", MONTH_MAR))
                .andExpect(jsonPath("$.data[?(@.title == '" + PREFIX + "别人的日程')]", empty()));
    }

    @Test
    @DisplayName("提醒时间晚于预约时间被拒绝")
    void remindLaterThanScheduleRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/schedules").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduleTime", "2027-03-10 09:00:00",
                                "remindTime", "2027-03-10 09:30:00",
                                "title", PREFIX + "提醒过晚"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("提醒时间不能晚于预约时间"));

        long id = create(session, PREFIX + "提醒校验", "2027-03-10 09:00:00", null, null);
        mockMvc.perform(put("/api/schedules/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduleTime", "2027-03-10 09:00:00",
                                "remindTime", "2027-03-11 09:00:00",
                                "title", PREFIX + "提醒校验"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("提醒时间不能晚于预约时间"));
    }

    @Test
    @DisplayName("缺少预约时间或标题被拒绝")
    void requiredFieldsRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/schedules").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", PREFIX + "缺时间"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("scheduleTime 不能为空"));

        mockMvc.perform(post("/api/schedules").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduleTime", "2027-03-10 09:00:00",
                                "title", "  "))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("title 不能为空"));
    }

    @Test
    @DisplayName("日程管理页正常渲染")
    void pageRenders() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/schedules").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("我的日程")))
                .andExpect(content().string(containsString("/js/schedules.js")))
                // 提醒时间固定为「提前量」下拉，5 档由前端按预约时间换算成时刻
                .andExpect(content().string(containsString("id=\"remindOffset\"")))
                .andExpect(content().string(containsString("前15分钟")))
                .andExpect(content().string(containsString("id=\"remindPreview\"")));
    }

    @Test
    @DisplayName("日程页提醒时间为 5 档下拉，不再是自由输入的时刻控件")
    void remindIsPresetDropdown() throws Exception {
        MockHttpSession session = login();

        String html = mockMvc.perform(get("/schedules").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String label : new String[]{"开始时", "前5分钟", "前10分钟", "前15分钟", "前30分钟"}) {
            assertTrue(html.contains(label), "下拉应包含选项：" + label);
        }
        // 旧实现是 <input type="datetime-local" id="remindTime">，不允许回退
        assertFalse(html.contains("id=\"remindTime\""), "提醒时间不应再是自由输入的时刻控件");
    }

    /* ------------------------------------------------------------------ 工具 */

    private long create(MockHttpSession session, String title, String scheduleTime,
                        String remindTime, String intro) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("scheduleTime", scheduleTime);
        body.put("remindTime", remindTime);
        body.put("intro", intro);

        MvcResult result = mockMvc.perform(post("/api/schedules").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readJson(result).path("data").asLong();
    }

}
