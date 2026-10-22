package com.management.software.interfaces.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.management.software.domain.aggregate.Employee;
import com.management.software.domain.aggregate.entity.EmployeeSalary;
import com.management.software.infrastructure.enums.EmployeeStatusEnum;
import com.management.software.infrastructure.repository.mapper.EmployeeMapper;
import com.management.software.infrastructure.repository.mapper.EmployeeSalaryMapper;
import com.management.software.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 员工薪酬接口集成测试。
 *
 * <p>重点锁四件事：**未登录不可读写**（薪酬是敏感数据）、**一人一行**（重复保存是覆盖不是追加）、
 * **入参边界**（币种白名单、负数、小数位、不可缺省）、**删除员工要级联清掉薪酬**——
 * 少了最后一条，工号被再次启用时新员工会静默继承前任的薪酬，这种错不会报任何异常。
 *
 * <p>用例自建员工数据并随事务回滚，不污染本地库。
 */
class EmployeeSalaryApiTest extends ApiTestSupport {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private EmployeeSalaryMapper employeeSalaryMapper;

    @Test
    @DisplayName("未登录不能查询与保存薪酬")
    void requiresLogin() throws Exception {
        Integer employeeId = newEmployee("IT-SAL-ANON", "薪酬测试-未登录");

        mockMvc.perform(get(salaryUrl(employeeId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(salaryUrl(employeeId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CNY", 10000, 120000, 459.77)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("尚未设置时 data 为空，而不是一条全 0 记录")
    void returnsEmptyWhenNotConfigured() throws Exception {
        MockHttpSession session = login();
        Integer employeeId = newEmployee("IT-SAL-EMPTY", "薪酬测试-未设置");

        MvcResult result = mockMvc.perform(get(salaryUrl(employeeId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        assertFalse(readJson(result).has("data"),
                "未设置过应不带 data 字段：「没设过」和「设成 0」是两件事，实际："
                        + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("员工不存在时读写都报业务错误")
    void rejectsUnknownEmployee() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/employees/99999999/salary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("员工不存在或已被删除"));

        mockMvc.perform(put("/api/employees/99999999/salary")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CNY", 1000, 12000, 45.98)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("员工不存在或已被删除"));

        assertTrue(rowsOf("IT-SAL-NOBODY").isEmpty());
    }

    @Test
    @DisplayName("保存成功：回参带币种中文与操作人，库里恰好一行")
    void savesSalary() throws Exception {
        MockHttpSession session = login();
        String workNo = "IT-SAL-OK";
        Integer employeeId = newEmployee(workNo, "薪酬测试-保存");

        MvcResult result = mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CNY", 10000, 120000, 459.77)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.workNo").value(workNo))
                .andExpect(jsonPath("$.data.employeeName").value("薪酬测试-保存"))
                .andExpect(jsonPath("$.data.currency").value("CNY"))
                .andExpect(jsonPath("$.data.currencyText").value("人民币"))
                .andExpect(jsonPath("$.data.updateBy").value(TEST_USERNAME))
                .andReturn();

        JsonNode data = readJson(result).path("data");
        assertEquals(10000.0D, data.path("monthSalary").asDouble(), 0.001D);
        assertEquals(120000.0D, data.path("yearSalary").asDouble(), 0.001D);
        assertEquals(459.77D, data.path("daySalary").asDouble(), 0.001D);
        assertFalse(data.path("createTime").asText().isEmpty(), "首次设置时间应回传");
        assertFalse(data.path("updateTime").asText().isEmpty(), "最后修改时间应回传");

        List<EmployeeSalary> rows = rowsOf(workNo);
        assertEquals(1, rows.size());
        assertEquals("CNY", rows.get(0).getCurrency());
        assertEquals(10000.0D, rows.get(0).getMonthSalary(), 0.001D);

        // 查回来应与保存的一致（走的是同一套 VO 组装）
        MvcResult detail = mockMvc.perform(get(salaryUrl(employeeId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        assertEquals(459.77D, readJson(detail).path("data").path("daySalary").asDouble(), 0.001D);
    }

    @Test
    @DisplayName("重复保存是覆盖同一行，不是追加")
    void savingTwiceOverwritesSingleRow() throws Exception {
        MockHttpSession session = login();
        String workNo = "IT-SAL-UPSERT";
        Integer employeeId = newEmployee(workNo, "薪酬测试-覆盖");

        mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CNY", 10000, 120000, 459.77)))
                .andExpect(jsonPath("$.code").value(0));
        Long firstId = rowsOf(workNo).get(0).getId();

        mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("USD", 20000, 240000, 919.54)))
                .andExpect(jsonPath("$.code").value(0));

        List<EmployeeSalary> rows = rowsOf(workNo);
        assertEquals(1, rows.size(), "同一工号重复保存只能有一行，靠唯一索引 + upsert 保证");
        assertEquals(firstId, rows.get(0).getId(), "覆盖应落在原来那行上");
        assertEquals("USD", rows.get(0).getCurrency());
        assertEquals(20000.0D, rows.get(0).getMonthSalary(), 0.001D);
    }

    @Test
    @DisplayName("覆盖保存不刷新首次设置时间，只刷新最后修改时间与修改人")
    void keepsCreateTimeOnOverwrite() throws Exception {
        MockHttpSession session = login();
        String workNo = "IT-SAL-CREATE-TIME";
        Integer employeeId = newEmployee(workNo, "薪酬测试-首次时间");

        mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CNY", 10000, 120000, 459.77)))
                .andExpect(jsonPath("$.code").value(0));

        // 先塞一个可辨认的旧时间进 create_time：整套用例在同一个事务里，
        // current_timestamp 取的是事务开始时间，比较两次保存的时间差证明不了任何事
        LocalDateTime marker = LocalDateTime.of(2000, 1, 1, 0, 0);
        EmployeeSalary patch = new EmployeeSalary();
        patch.setId(rowsOf(workNo).get(0).getId());
        patch.setCreateTime(marker);
        employeeSalaryMapper.updateById(patch);

        mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CNY", 30000, 360000, 1379.31)))
                .andExpect(jsonPath("$.code").value(0));

        EmployeeSalary row = rowsOf(workNo).get(0);
        assertEquals(marker, row.getCreateTime(), "覆盖时 create_time 必须保持首次设置的时刻");
        assertEquals(30000.0D, row.getMonthSalary(), 0.001D);
        assertNotNull(row.getUpdateTime());
        assertEquals(TEST_USERNAME, row.getUpdateBy());
    }

    @Test
    @DisplayName("金额统一按两位小数上行，整数也补 .00")
    void padsAmountToTwoDecimals() throws Exception {
        MockHttpSession session = login();
        Integer employeeId = newEmployee("IT-SAL-SCALE", "薪酬测试-精度");

        MvcResult result = mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CNY", 1000.5, 12006, 46)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        // 断言原始报文而不是重新解析后的节点：JsonNode 会把 1000.50 读成 1000.5，
        // 那样这条「两位小数」的约定就锁不住了
        String raw = result.getResponse().getContentAsString();
        assertTrue(raw.contains("\"monthSalary\":1000.50"), "实际报文：" + raw);
        assertTrue(raw.contains("\"yearSalary\":12006.00"), "实际报文：" + raw);
        assertTrue(raw.contains("\"daySalary\":46.00"), "实际报文：" + raw);
    }

    @Test
    @DisplayName("币种必须在白名单内")
    void rejectsUnsupportedCurrency() throws Exception {
        MockHttpSession session = login();
        String workNo = "IT-SAL-CURRENCY";
        Integer employeeId = newEmployee(workNo, "薪酬测试-币种");

        mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("XXX", 1000, 12000, 45.98)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不支持的币种：XXX"));

        assertTrue(rowsOf(workNo).isEmpty());
    }

    @Test
    @DisplayName("负数与超过两位小数的金额被校验拦下")
    void rejectsInvalidAmount() throws Exception {
        MockHttpSession session = login();
        String workNo = "IT-SAL-INVALID";
        Integer employeeId = newEmployee(workNo, "薪酬测试-非法金额");

        mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CNY", -1, 12000, 45.98)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("monthSalary 不能为负数"));

        mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CNY", 1000, 12000, 45.981)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("daySalary 最多 16 位整数、2 位小数"));

        assertTrue(rowsOf(workNo).isEmpty());
    }

    @Test
    @DisplayName("金额字段不可缺省：漏传一个字段是 400，而不是静默清零")
    void rejectsMissingAmount() throws Exception {
        MockHttpSession session = login();
        String workNo = "IT-SAL-MISSING";
        Integer employeeId = newEmployee(workNo, "薪酬测试-缺字段");

        Map<String, Object> payload = new HashMap<>();
        payload.put("currency", "CNY");
        payload.put("monthSalary", 1000);
        payload.put("yearSalary", 12000);
        // 故意不传 daySalary

        mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("daySalary 不能为空"));

        assertTrue(rowsOf(workNo).isEmpty());
    }

    @Test
    @DisplayName("币种为空被校验拦下")
    void rejectsBlankCurrency() throws Exception {
        MockHttpSession session = login();
        Integer employeeId = newEmployee("IT-SAL-BLANK-CURRENCY", "薪酬测试-空币种");

        mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ", 1000, 12000, 45.98)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("currency 不能为空"));
    }

    @Test
    @DisplayName("币种大小写与空格被归一后仍可保存")
    void normalizesCurrencyCode() throws Exception {
        MockHttpSession session = login();
        String workNo = "IT-SAL-CURRENCY-CASE";
        Integer employeeId = newEmployee(workNo, "薪酬测试-币种归一");

        mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(" usd ", 1000, 12000, 45.98)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.currencyText").value("美元"));

        assertEquals("USD", rowsOf(workNo).get(0).getCurrency());
    }

    @Test
    @DisplayName("删除员工时一并清掉薪酬，不留孤儿行")
    void deletesSalaryWithEmployee() throws Exception {
        MockHttpSession session = login();
        String workNo = "IT-SAL-CASCADE";
        Integer employeeId = newEmployee(workNo, "薪酬测试-级联");

        mockMvc.perform(put(salaryUrl(employeeId))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CNY", 10000, 120000, 459.77)))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(1, rowsOf(workNo).size());

        mockMvc.perform(delete("/api/employees/" + employeeId).session(session))
                .andExpect(jsonPath("$.code").value(0));

        assertTrue(rowsOf(workNo).isEmpty(),
                "工号被再次启用时，孤儿薪酬会被新员工静默继承，所以删除员工必须连带清理");
    }

    /* ------------------------------------------------------------------ 工具 */

    private String salaryUrl(Integer employeeId) {
        return "/api/employees/" + employeeId + "/salary";
    }

    private String body(String currency, Object month, Object year, Object day) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("currency", currency);
        payload.put("monthSalary", month);
        payload.put("yearSalary", year);
        payload.put("daySalary", day);
        return objectMapper.writeValueAsString(payload);
    }

    /** 造一个员工，返回员工主键（接口路径用的是主键，不是工号）。 */
    private Integer newEmployee(String workNo, String name) {
        Employee employee = new Employee();
        employee.setWorkNo(workNo);
        employee.setName(name);
        employee.setStatus(EmployeeStatusEnum.FORMAL.getStatus());
        employeeMapper.insert(employee);
        assertNotNull(employee.getId());
        return employee.getId();
    }

    private List<EmployeeSalary> rowsOf(String workNo) {
        return employeeSalaryMapper.selectList(new LambdaQueryWrapper<EmployeeSalary>()
                .eq(EmployeeSalary::getWorkNo, workNo));
    }

}
