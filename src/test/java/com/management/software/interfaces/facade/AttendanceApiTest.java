package com.management.software.interfaces.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.management.software.domain.aggregate.Employee;
import com.management.software.domain.aggregate.entity.AttendanceRecord;
import com.management.software.infrastructure.config.AttendanceProperties;
import com.management.software.infrastructure.enums.AttendanceRecordStatusEnum;
import com.management.software.infrastructure.enums.EmployeeStatusEnum;
import com.management.software.infrastructure.repository.mapper.AttendanceRecordMapper;
import com.management.software.infrastructure.repository.mapper.EmployeeMapper;
import com.management.software.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 员工打卡接口集成测试。
 *
 * <p>重点锁四件事：**不需要登录**（打卡机没有会话）、**来源网段是唯一门禁**、
 * 打卡时间由服务端决定（客户端塞的时间不作数）、只放行在职员工。
 * 用例自建员工数据并随事务回滚，不污染本地库。
 */
class AttendanceApiTest extends ApiTestSupport {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private AttendanceRecordMapper attendanceRecordMapper;

    @Autowired
    private AttendanceProperties attendanceProperties;

    @Test
    @DisplayName("不带任何会话即可打卡（打卡机是设备，没有登录态）")
    void punchWorksWithoutLogin() throws Exception {
        String workNo = newEmployee("IT-PUNCH-NO-SESSION", "打卡测试-免登录", EmployeeStatusEnum.FORMAL);

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(workNo, "总部 A 座 1F")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.workNo").value(workNo));

        assertEquals(1, recordsOf(workNo).size());
    }

    @Test
    @DisplayName("默认白名单只放行本机回环，不含任何办公网段")
    void defaultWhitelistIsLoopbackOnly() {
        List<String> networks = attendanceProperties.getAllowedNetworks();
        assertEquals(List.of("127.0.0.1/32", "::1/128"), networks,
                "默认值必须收敛到回环；放行内网要靠显式配置，不能靠默认值");
    }

    @Test
    @DisplayName("来源不在允许网段内被拒（403）")
    void rejectsSourceOutsideWhitelist() throws Exception {
        String workNo = newEmployee("IT-PUNCH-OUTSIDE", "打卡测试-外部来源", EmployeeStatusEnum.FORMAL);

        mockMvc.perform(post("/api/attendance/punch")
                        .with(remoteAddr("203.0.113.9"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(workNo, "总部")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("来源地址不在允许网段内，无法打卡"));

        assertTrue(recordsOf(workNo).isEmpty(), "被拒绝的请求不应留下打卡记录");
    }

    @Test
    @DisplayName("伪造 X-Forwarded-For 不能绕过来源校验（白名单只看 remoteAddr）")
    void spoofedForwardedHeaderCannotBypassWhitelist() throws Exception {
        String workNo = newEmployee("IT-PUNCH-SPOOF", "打卡测试-伪造来源", EmployeeStatusEnum.FORMAL);

        mockMvc.perform(post("/api/attendance/punch")
                        .with(remoteAddr("203.0.113.9"))
                        .header("X-Forwarded-For", "127.0.0.1")
                        .header("X-Real-IP", "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(workNo, "总部")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertTrue(recordsOf(workNo).isEmpty());
    }

    @Test
    @DisplayName("IPv4 映射写法与 IPv6 回环都算命中白名单")
    void acceptsMappedAndIpv6Loopback() throws Exception {
        String mapped = newEmployee("IT-PUNCH-MAPPED", "打卡测试-映射回环", EmployeeStatusEnum.FORMAL);
        String ipv6 = newEmployee("IT-PUNCH-IPV6", "打卡测试-IPv6回环", EmployeeStatusEnum.FORMAL);

        mockMvc.perform(post("/api/attendance/punch")
                        .with(remoteAddr("::ffff:127.0.0.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(mapped, "总部")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/attendance/punch")
                        .with(remoteAddr("0:0:0:0:0:0:0:1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ipv6, "总部")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("在职员工打卡成功：落一条有效记录，时间取服务端时钟")
    void punchSucceeds() throws Exception {
        String workNo = newEmployee("IT-PUNCH-OK", "打卡测试-正常", EmployeeStatusEnum.FORMAL);

        // 用 epoch 毫秒夹逼而不是 LocalDateTime：不依赖跑测试的机器时区
        long before = System.currentTimeMillis() - 1000L;

        MvcResult result = mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(workNo, "总部 A 座 1F")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.workNo").value(workNo))
                .andExpect(jsonPath("$.data.employeeName").value("打卡测试-正常"))
                .andExpect(jsonPath("$.data.location").value("总部 A 座 1F"))
                .andReturn();

        long after = System.currentTimeMillis() + 1000L;

        JsonNode data = readJson(result).path("data");
        assertTrue(data.path("id").asLong() > 0, "应返回打卡记录ID");

        List<AttendanceRecord> records = recordsOf(workNo);
        assertEquals(1, records.size(), "打卡一次只应落一条记录");
        AttendanceRecord record = records.get(0);
        assertEquals(AttendanceRecordStatusEnum.VALID.getValue(), record.getStatus(), "新打卡固定为有效");
        assertEquals("总部 A 座 1F", record.getLocation());
        assertTrue(record.getPunchTime() >= before && record.getPunchTime() <= after,
                "落库时间应落在请求前后，实际 " + record.getPunchTime());

        // 回参是可读时间文本（不是毫秒数），且换算回毫秒后与落库值一致（秒级精度）——
        // 顺带锁住「VO 按配置时区转换」这件事，时区配错这里对不上
        LocalDateTime returned = LocalDateTime.parse(data.path("punchTime").asText().replace(' ', 'T'));
        long returnedMillis = returned.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        assertTrue(Math.abs(returnedMillis - record.getPunchTime()) < 1000L,
                "回参时间应与落库时间一致，回参=" + returnedMillis + "，落库=" + record.getPunchTime());
    }

    @Test
    @DisplayName("试用期员工同样可以打卡")
    void trialEmployeeCanPunch() throws Exception {
        String workNo = newEmployee("IT-PUNCH-TRIAL", "打卡测试-试用期", EmployeeStatusEnum.TRIAL);

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(workNo, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        AttendanceRecord record = recordsOf(workNo).get(0);
        assertNull(record.getLocation(), "位置为空时应落 null，而不是空串");
    }

    @Test
    @DisplayName("客户端传的 punchTime 被忽略，不能伪造打卡时间")
    void clientSuppliedPunchTimeIsIgnored() throws Exception {
        String workNo = newEmployee("IT-PUNCH-FAKE", "打卡测试-伪造时间", EmployeeStatusEnum.FORMAL);

        Map<String, Object> payload = new HashMap<>();
        payload.put("workNo", workNo);
        payload.put("location", "总部");
        // 伪造一个 2020 年的时间，期望服务端不采纳
        payload.put("punchTime", 1577808000000L);

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        long stored = recordsOf(workNo).get(0).getPunchTime();
        assertTrue(stored > 1577808000000L + 86_400_000L,
                "落库时间不应是客户端传入的 2020 年时间，实际 " + stored);
    }

    @Test
    @DisplayName("工号不存在时拒绝打卡")
    void rejectsUnknownWorkNo() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("IT-PUNCH-NOBODY", "总部")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("工号 IT-PUNCH-NOBODY 不存在，无法打卡"));

        assertTrue(recordsOf("IT-PUNCH-NOBODY").isEmpty(), "被拒绝的打卡不应留下记录");
    }

    @Test
    @DisplayName("离职员工不能打卡")
    void rejectsResignedEmployee() throws Exception {
        String workNo = newEmployee("IT-PUNCH-RESIGN", "打卡测试-离职", EmployeeStatusEnum.RESIGN);

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(workNo, "总部")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("员工「打卡测试-离职」已离职，无法打卡"));

        assertTrue(recordsOf(workNo).isEmpty());
    }

    @Test
    @DisplayName("辞退员工不能打卡")
    void rejectsDismissedEmployee() throws Exception {
        String workNo = newEmployee("IT-PUNCH-DISMISS", "打卡测试-辞退", EmployeeStatusEnum.DISMISS);

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(workNo, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("员工「打卡测试-辞退」已辞退，无法打卡"));
    }

    @Test
    @DisplayName("工号为空或超长被校验拦下")
    void validatesWorkNo() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ", "总部")))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("workNo 不能为空"));

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("W".repeat(33), "总部")))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("workNo 长度不能超过 32"));
    }

    @Test
    @DisplayName("打卡位置超长被校验拦下")
    void validatesLocation() throws Exception {
        String workNo = newEmployee("IT-PUNCH-LOC", "打卡测试-位置", EmployeeStatusEnum.FORMAL);

        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(workNo, "位".repeat(256))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("location 长度不能超过 255"));

        assertTrue(recordsOf(workNo).isEmpty());
    }

    @Test
    @DisplayName("缺请求体被判为参数错误，而不是 500")
    void rejectsMissingBody() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400));
    }

    /* ------------------------------------------------------------------ 工具 */

    /** 把请求的来源地址改成指定值，模拟从不同网络位置来的调用。 */
    private RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /** 造一个员工，返回工号。 */
    private String newEmployee(String workNo, String name, EmployeeStatusEnum status) {
        Employee employee = new Employee();
        employee.setWorkNo(workNo);
        employee.setName(name);
        employee.setStatus(status.getStatus());
        employeeMapper.insert(employee);
        assertNotNull(employee.getId());
        return workNo;
    }

    private List<AttendanceRecord> recordsOf(String workNo) {
        return attendanceRecordMapper.selectList(new LambdaQueryWrapper<AttendanceRecord>()
                .eq(AttendanceRecord::getWorkNo, workNo)
                .orderByAsc(AttendanceRecord::getId));
    }

    private String body(String workNo, String location) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("workNo", workNo);
        if (location != null) {
            payload.put("location", location);
        }
        return objectMapper.writeValueAsString(payload);
    }

}
