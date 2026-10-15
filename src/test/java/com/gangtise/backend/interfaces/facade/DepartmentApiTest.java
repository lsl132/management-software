package com.gangtise.backend.interfaces.facade;

import com.gangtise.backend.domain.aggregate.Employee;
import com.gangtise.backend.domain.aggregate.entity.DepartmentRank;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentMapper;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentRankMapper;
import com.gangtise.backend.infrastructure.repository.mapper.EmployeeMapper;
import com.gangtise.backend.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 部门管理接口集成测试。
 *
 * <p>全部用例自建数据（不依赖 seed），配合 {@code @Transactional} 回滚，不污染本地库。
 */
class DepartmentApiTest extends ApiTestSupport {

    private static final String PREFIX = "IT-部门-";

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private DepartmentRankMapper departmentRankMapper;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Test
    @DisplayName("未登录不能访问部门接口")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("部门增删改查全流程")
    void crudFlow() throws Exception {
        MockHttpSession session = login();

        Integer id = create(session, PREFIX + "研发", null);
        assertNotNull(departmentMapper.selectById(id), "新增后应能查到部门");

        mockMvc.perform(get("/api/departments").session(session))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")].departName",
                        hasItem(PREFIX + "研发")));

        // 改名 + 补简介
        mockMvc.perform(put("/api/departments/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "departName", PREFIX + "研究院",
                                "description", "负责技术研发"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/departments/" + id).session(session))
                .andExpect(jsonPath("$.data.departName").value(PREFIX + "研究院"))
                .andExpect(jsonPath("$.data.description").value("负责技术研发"))
                .andExpect(jsonPath("$.data.rankCount").value(0))
                .andExpect(jsonPath("$.data.employeeCount").value(0));

        mockMvc.perform(delete("/api/departments/" + id).session(session))
                .andExpect(jsonPath("$.code").value(0));
        assertNull(departmentMapper.selectById(id), "删除后应查不到部门");
    }

    @Test
    @DisplayName("部门名称重复被拒绝")
    void duplicateNameRejected() throws Exception {
        MockHttpSession session = login();
        String name = PREFIX + "重复名";
        create(session, name, null);

        mockMvc.perform(post("/api/departments").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("departName", name))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("部门名称已存在：" + name));
    }

    @Test
    @DisplayName("部门名称为空被拒绝")
    void blankNameRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/departments").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("departName", "   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("departName 不能为空"));
    }

    @Test
    @DisplayName("上级部门不能选择本部门自身")
    void parentSelfRejected() throws Exception {
        MockHttpSession session = login();
        Integer id = create(session, PREFIX + "自环", null);

        mockMvc.perform(put("/api/departments/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "departName", PREFIX + "自环",
                                "parentId", id))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("上级部门不能选择本部门或其下级部门"));
    }

    @Test
    @DisplayName("上级部门不能选择自己的下级，避免形成环")
    void parentCycleRejected() throws Exception {
        MockHttpSession session = login();
        Integer parent = create(session, PREFIX + "父级", null);
        Integer child = create(session, PREFIX + "子级", parent);

        mockMvc.perform(put("/api/departments/" + parent).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "departName", PREFIX + "父级",
                                "parentId", child))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("上级部门不能选择本部门或其下级部门"));
    }

    @Test
    @DisplayName("存在子部门时不允许删除")
    void deleteWithChildRejected() throws Exception {
        MockHttpSession session = login();
        Integer parent = create(session, PREFIX + "父级2", null);
        create(session, PREFIX + "子级2", parent);

        mockMvc.perform(delete("/api/departments/" + parent).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该部门下还有 1 个子部门，请先处理子部门"));
    }

    @Test
    @DisplayName("存在职级时不允许删除")
    void deleteWithRankRejected() throws Exception {
        MockHttpSession session = login();
        Integer id = create(session, PREFIX + "带职级", null);

        DepartmentRank rank = new DepartmentRank();
        rank.setCode(PREFIX + "CODE1");
        rank.setType(1);
        rank.setDepartment(id);
        departmentRankMapper.insert(rank);

        mockMvc.perform(delete("/api/departments/" + id).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该部门下还有 1 个职级，请先删除职级"));
    }

    @Test
    @DisplayName("存在员工时不允许删除")
    void deleteWithEmployeeRejected() throws Exception {
        MockHttpSession session = login();
        Integer id = create(session, PREFIX + "带员工", null);

        Employee employee = new Employee();
        employee.setWorkNo(PREFIX + "EMP1");
        employee.setName("部门删除测试员工");
        employee.setStatus(1);
        employee.setDepartment(id);
        employeeMapper.insert(employee);

        mockMvc.perform(delete("/api/departments/" + id).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该部门下还有 1 名员工，请先调整员工所属部门"));
    }

    /* ------------------------------------------------------------------ 工具 */

    private Integer create(MockHttpSession session, String departName, Integer parentId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("departName", departName);
        body.put("parentId", parentId);

        MvcResult result = mockMvc.perform(post("/api/departments").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readJson(result).path("data").asInt();
    }

}
