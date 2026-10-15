package com.gangtise.backend.interfaces.facade;

import com.fasterxml.jackson.databind.JsonNode;
import com.gangtise.backend.domain.aggregate.entity.UserLoginRecord;
import com.gangtise.backend.infrastructure.repository.mapper.UserLoginRecordMapper;
import com.gangtise.backend.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人工作台（欢迎页）接口与页面集成测试。
 */
class DashboardApiTest extends ApiTestSupport {

    /** 不属于任何登录用户的值，用于验证趋势只统计本人。 */
    private static final int FOREIGN_USER_ID = 999999;

    @Autowired
    private UserLoginRecordMapper userLoginRecordMapper;

    @Test
    @DisplayName("未登录不能访问工作台接口")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/dashboard/login-trend"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登录趋势默认返回连续 10 天，最后一天是今天且包含本次登录")
    void loginTrendHasContinuousPoints() throws Exception {
        MockHttpSession session = login();
        JsonNode data = trend(session, null);

        assertEquals(10, data.path("days").asInt());
        assertEquals(10, data.path("points").size(), "缺失日期应补 0，点数与天数一致");

        LocalDate today = LocalDate.now();
        JsonNode first = data.path("points").get(0);
        JsonNode last = data.path("points").get(9);
        assertEquals(today.minusDays(9).toString(), first.path("date").asText());
        assertEquals(today.toString(), last.path("date").asText());
        assertEquals(today.minusDays(9).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")),
                first.path("label").asText());

        assertTrue(data.path("total").asInt() >= 1, "本次登录应被统计");
        assertTrue(last.path("count").asInt() >= 1, "今天的登录次数至少为 1");
        assertTrue(data.path("max").asInt() >= 1, "纵轴上限至少为 1，避免除零");
        assertFalse(data.path("lastLoginTime").asText().isEmpty(), "最近登录时间不应为空");
    }

    @Test
    @DisplayName("趋势只统计当前登录用户")
    void loginTrendCountsOnlyCurrentUser() throws Exception {
        MockHttpSession session = login();
        int before = trend(session, null).path("total").asInt();

        UserLoginRecord foreign = new UserLoginRecord();
        foreign.setUserId(FOREIGN_USER_ID);
        foreign.setLoginIp("10.0.0.9");
        userLoginRecordMapper.insert(foreign);

        assertEquals(before, trend(session, null).path("total").asInt(),
                "别人的登录记录不应计入我的趋势");
    }

    @Test
    @DisplayName("天数参数被限制在 1~90，且非法值不会报错")
    void trendDaysClamped() throws Exception {
        MockHttpSession session = login();

        assertEquals(90, trend(session, 999).path("days").asInt());
        assertEquals(90, trend(session, 999).path("points").size());
        assertEquals(1, trend(session, 0).path("days").asInt());
        assertEquals(30, trend(session, 30).path("days").asInt());
    }

    @Test
    @DisplayName("欢迎页只展示与个人相关的内容")
    void homePageShowsPersonalBlocks() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/home").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("我的登录记录")))
                .andExpect(content().string(containsString("我的日历")))
                .andExpect(content().string(containsString("发布的公告")))
                .andExpect(content().string(containsString("/js/home.js")))
                // 改版前与个人无关的统计卡片与架构介绍应已移除
                .andExpect(content().string(not(containsString("员工总数"))))
                .andExpect(content().string(not(containsString("系统用户"))))
                .andExpect(content().string(not(containsString("登录安全"))))
                .andExpect(content().string(not(containsString("分层结构"))));
    }

    @Test
    @DisplayName("欢迎页未登录时重定向到登录页")
    void homePageRequiresLogin() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection());
    }

    /* ------------------------------------------------------------------ 工具 */

    private JsonNode trend(MockHttpSession session, Integer days) throws Exception {
        var request = get("/api/dashboard/login-trend").session(session);
        if (days != null) {
            request = request.param("days", String.valueOf(days));
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readJson(result).path("data");
    }

}
