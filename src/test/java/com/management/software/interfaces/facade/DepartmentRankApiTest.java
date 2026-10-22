package com.management.software.interfaces.facade;

import com.management.software.domain.aggregate.Department;
import com.management.software.domain.aggregate.Employee;
import com.management.software.domain.aggregate.entity.DepartmentRank;
import com.management.software.infrastructure.repository.mapper.DepartmentMapper;
import com.management.software.infrastructure.repository.mapper.DepartmentRankMapper;
import com.management.software.infrastructure.repository.mapper.EmployeeMapper;
import com.management.software.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 部门职级管理接口集成测试。
 *
 * <p>职级隶属于部门，接口路径为 {@code /api/departments/{departmentId}/ranks}；
 * 全部用例自建部门与职级（不依赖 seed），配合 {@code @Transactional} 回滚。
 */
class DepartmentRankApiTest extends ApiTestSupport {

    private static final String PREFIX = "IT-职级-";

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private DepartmentRankMapper departmentRankMapper;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Test
    @DisplayName("未登录不能访问职级接口")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/departments/1/ranks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("职级增删改查全流程")
    void crudFlow() throws Exception {
        MockHttpSession session = login();
        Integer departmentId = newDepartment(PREFIX + "研发部");

        Integer rankId = createRank(session, departmentId, PREFIX + "A1", 1, "初级");
        assertNotNull(departmentRankMapper.selectById(rankId), "新增后应能查到职级");

        mockMvc.perform(get("/api/departments/" + departmentId + "/ranks").session(session))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value(PREFIX + "A1"))
                .andExpect(jsonPath("$.data[0].typeText").value("O 一线操作工种"))
                .andExpect(jsonPath("$.data[0].department").value(departmentId))
                .andExpect(jsonPath("$.data[0].departmentName").value(PREFIX + "研发部"));

        // 改编码 / 改序列 / 清空说明（说明传 null 走 ALWAYS 策略置空）
        Map<String, Object> update = new HashMap<>();
        update.put("code", PREFIX + "B1");
        update.put("type", 2);
        update.put("intro", null);
        mockMvc.perform(put("/api/departments/" + departmentId + "/ranks/" + rankId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        DepartmentRank saved = departmentRankMapper.selectById(rankId);
        assertNotNull(saved);
        org.junit.jupiter.api.Assertions.assertEquals(PREFIX + "B1", saved.getCode());
        org.junit.jupiter.api.Assertions.assertEquals(2, saved.getType());
        assertNull(saved.getIntro(), "说明传 null 应被清空");

        mockMvc.perform(delete("/api/departments/" + departmentId + "/ranks/" + rankId).session(session))
                .andExpect(jsonPath("$.code").value(0));
        assertNull(departmentRankMapper.selectById(rankId), "删除后应查不到职级");
    }

    @Test
    @DisplayName("职级列表按序列 + 编码排序")
    void listOrderedByTypeAndCode() throws Exception {
        MockHttpSession session = login();
        Integer departmentId = newDepartment(PREFIX + "排序部");

        createRank(session, departmentId, PREFIX + "C2", 2, "中级");
        createRank(session, departmentId, PREFIX + "C1", 2, "初级");
        createRank(session, departmentId, PREFIX + "A1", 1, "操作");

        mockMvc.perform(get("/api/departments/" + departmentId + "/ranks").session(session))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].code").value(PREFIX + "A1"))
                .andExpect(jsonPath("$.data[1].code").value(PREFIX + "C1"))
                .andExpect(jsonPath("$.data[2].code").value(PREFIX + "C2"));
    }

    @Test
    @DisplayName("职级编码重复被拒绝")
    void duplicateCodeRejected() throws Exception {
        MockHttpSession session = login();
        Integer departmentId = newDepartment(PREFIX + "重复部");
        String code = PREFIX + "DUP1";
        createRank(session, departmentId, code, 1, null);

        mockMvc.perform(post("/api/departments/" + departmentId + "/ranks").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code, "type", 1))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("职级编码已存在：" + code));
    }

    @Test
    @DisplayName("职级序列取值非法被拒绝")
    void invalidTypeRejected() throws Exception {
        MockHttpSession session = login();
        Integer departmentId = newDepartment(PREFIX + "序列部");

        // 超出 RankTypeEnum 范围
        mockMvc.perform(post("/api/departments/" + departmentId + "/ranks").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", PREFIX + "E9", "type", 9))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("type 取值不合法"));

        // 序列缺失
        mockMvc.perform(post("/api/departments/" + departmentId + "/ranks").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", PREFIX + "E0"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("type 不能为空"));
    }

    @Test
    @DisplayName("部门不存在时不能维护职级")
    void unknownDepartmentRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/departments/99999999/ranks").session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("部门不存在或已被删除"));
    }

    @Test
    @DisplayName("不能通过其他部门的路径修改或删除职级")
    void crossDepartmentRejected() throws Exception {
        MockHttpSession session = login();
        Integer ownerId = newDepartment(PREFIX + "归属部");
        Integer otherId = newDepartment(PREFIX + "其他部");
        Integer rankId = createRank(session, ownerId, PREFIX + "F1", 1, "归属测试");

        mockMvc.perform(put("/api/departments/" + otherId + "/ranks/" + rankId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", PREFIX + "F1", "type", 1))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该职级不属于当前部门，请刷新页面后重试"));

        mockMvc.perform(delete("/api/departments/" + otherId + "/ranks/" + rankId).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该职级不属于当前部门，请刷新页面后重试"));

        assertNotNull(departmentRankMapper.selectById(rankId), "越权删除不应生效");
    }

    @Test
    @DisplayName("职级已被员工引用时不允许删除")
    void deleteUsedRankRejected() throws Exception {
        MockHttpSession session = login();
        Integer departmentId = newDepartment(PREFIX + "占用部");
        Integer rankId = createRank(session, departmentId, PREFIX + "G1", 1, "被占用");

        Employee employee = new Employee();
        employee.setWorkNo(PREFIX + "EMP-G1");
        employee.setName("职级占用测试员工");
        employee.setStatus(1);
        employee.setDepartment(departmentId);
        employee.setRank(rankId);
        employeeMapper.insert(employee);

        mockMvc.perform(delete("/api/departments/" + departmentId + "/ranks/" + rankId).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该职级已被 1 名员工使用，请先调整员工职级"));

        assertNotNull(departmentRankMapper.selectById(rankId), "被引用的职级不应被删除");
    }

    /* ------------------------------------------------------------------ 工具 */

    private Integer newDepartment(String departName) {
        Department department = new Department();
        department.setDepartName(departName);
        departmentMapper.insert(department);
        return department.getId();
    }

    private Integer createRank(MockHttpSession session, Integer departmentId,
                               String code, int type, String intro) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("type", type);
        body.put("intro", intro);

        MvcResult result = mockMvc.perform(post("/api/departments/" + departmentId + "/ranks")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readJson(result).path("data").asInt();
    }

}
