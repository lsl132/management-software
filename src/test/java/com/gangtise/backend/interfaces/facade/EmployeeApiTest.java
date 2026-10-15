package com.gangtise.backend.interfaces.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.gangtise.backend.domain.aggregate.Department;
import com.gangtise.backend.domain.aggregate.entity.DepartmentRank;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentMapper;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentRankMapper;
import com.gangtise.backend.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 员工增删改查接口集成测试。
 */
class EmployeeApiTest extends ApiTestSupport {

    private static final String JSON_TYPE = MediaType.APPLICATION_JSON_VALUE;

    private Map<String, Object> payload(String workNo, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("workNo", workNo);
        body.put("name", name);
        body.put("alias", "小名");
        body.put("gender", 1);
        body.put("identityCard", "110101199001011234");
        body.put("telephone", "13800000000");
        body.put("email", "zhangsan@example.com");
        body.put("address", "北京市朝阳区");
        body.put("status", 1);
        body.put("birthday", 19900101);
        body.put("education", 3);
        return body;
    }

    private int createEmployee(MockHttpSession session, String workNo, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employees")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(workNo, name))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode data = readJson(result).path("data");
        return data.asInt();
    }

    @Test
    @DisplayName("新增 -> 查询 -> 修改 -> 删除 全链路")
    void crudFlow() throws Exception {
        MockHttpSession session = login();
        String workNo = "EMP-CRUD-001";

        // 新增
        int id = createEmployee(session, workNo, "张三");

        // 列表中能查到，且关联字段与枚举文案已补齐
        mockMvc.perform(get("/api/employees").session(session).param("keyword", workNo))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("张三"))
                .andExpect(jsonPath("$.data.records[0].genderText").value("男"))
                .andExpect(jsonPath("$.data.records[0].statusText").value("正式"))
                .andExpect(jsonPath("$.data.records[0].educationText").value("本科"))
                .andExpect(jsonPath("$.data.records[0].birthday").value(19900101));

        // 修改（全量覆盖，可选项传 null 表示清空）
        Map<String, Object> update = payload(workNo, "张三丰");
        update.put("alias", null);
        update.put("status", 3);
        mockMvc.perform(put("/api/employees/" + id)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/employees/" + id).session(session))
                .andExpect(jsonPath("$.data.name").value("张三丰"))
                .andExpect(jsonPath("$.data.statusText").value("离职"))
                .andExpect(jsonPath("$.data.alias").doesNotExist());

        // 删除
        mockMvc.perform(delete("/api/employees/" + id).session(session))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/employees/" + id).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("员工不存在或已被删除"));
    }

    @Test
    @DisplayName("工号重复时给出明确提示")
    void duplicateWorkNoRejected() throws Exception {
        MockHttpSession session = login();
        String workNo = "EMP-DUP-001";
        createEmployee(session, workNo, "张三");

        mockMvc.perform(post("/api/employees")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload(workNo, "李四"))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("员工工号已存在")));
    }

    @Test
    @DisplayName("必填项校验失败时返回字段级提示")
    void validationRejectsBlankFields() throws Exception {
        MockHttpSession session = login();
        Map<String, Object> body = payload("EMP-VALID-001", "王五");
        body.put("name", "");

        mockMvc.perform(post("/api/employees")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("name")));
    }

    @Test
    @DisplayName("分页与状态过滤生效")
    void pagingAndFilterWork() throws Exception {
        MockHttpSession session = login();
        createEmployee(session, "EMP-PAGE-001", "分页一");
        createEmployee(session, "EMP-PAGE-002", "分页二");

        mockMvc.perform(get("/api/employees").session(session)
                        .param("keyword", "EMP-PAGE-")
                        .param("status", "1")
                        .param("current", "1")
                        .param("size", "1"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1));
    }

    @Test
    @DisplayName("未登录不能操作员工数据")
    void requiresLogin() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("EMP-ANON-001", "匿名"))))
                .andExpect(status().isUnauthorized());
    }

    /* -------------------------------------------------- 部门 / 职级 归属校验 */

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private DepartmentRankMapper departmentRankMapper;

    private Integer departmentId(String departName) {
        Department department = departmentMapper.selectOne(new LambdaQueryWrapper<Department>()
                .eq(Department::getDepartName, departName)
                .last("limit 1"));
        assertNotNull(department, "部门字典缺失，请先执行 docs/db/seed-department.sql：" + departName);
        return department.getId();
    }

    private Integer rankId(String code) {
        DepartmentRank rank = departmentRankMapper.selectOne(new LambdaQueryWrapper<DepartmentRank>()
                .eq(DepartmentRank::getCode, code)
                .last("limit 1"));
        assertNotNull(rank, "职级字典缺失，请先执行 docs/db/seed-department-rank.sql：" + code);
        return rank.getId();
    }

    @Test
    @DisplayName("职级与部门匹配时保存成功，并回显部门与职级名称")
    void rankAndDepartmentMatched() throws Exception {
        MockHttpSession session = login();
        Map<String, Object> body = payload("EMP-RANK-001", "赵六");
        body.put("department", departmentId("生产部"));
        body.put("rank", rankId("O2"));

        MvcResult result = mockMvc.perform(post("/api/employees")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        int id = readJson(result).path("data").asInt();

        mockMvc.perform(get("/api/employees/" + id).session(session))
                .andExpect(jsonPath("$.data.departmentName").value("生产部"))
                .andExpect(jsonPath("$.data.rankName").value("O2（中级工）"));
    }

    @Test
    @DisplayName("职级不属于所选部门时拒绝保存")
    void rankFromOtherDepartmentRejected() throws Exception {
        MockHttpSession session = login();
        Map<String, Object> body = payload("EMP-RANK-002", "钱七");
        // 部门=生产部，职级=T1（技术研发部）
        body.put("department", departmentId("生产部"));
        body.put("rank", rankId("T1"));

        mockMvc.perform(post("/api/employees")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("职级不属于该部门")));
    }

    @Test
    @DisplayName("只填职级不填部门时拒绝保存")
    void rankWithoutDepartmentRejected() throws Exception {
        MockHttpSession session = login();
        Map<String, Object> body = payload("EMP-RANK-003", "孙八");
        body.put("rank", rankId("O1"));

        mockMvc.perform(post("/api/employees")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("请先选择所属部门")));
    }

    @Test
    @DisplayName("改部门后沿用旧部门职级时拒绝保存")
    void updateWithStaleRankRejected() throws Exception {
        MockHttpSession session = login();
        Map<String, Object> body = payload("EMP-RANK-004", "周九");
        body.put("department", departmentId("生产部"));
        body.put("rank", rankId("O3"));

        MvcResult result = mockMvc.perform(post("/api/employees")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        int id = readJson(result).path("data").asInt();

        // 部门改为销售部，但仍传生产部的职级 O3
        Map<String, Object> update = payload("EMP-RANK-004", "周九");
        update.put("department", departmentId("销售部"));
        update.put("rank", rankId("O3"));

        mockMvc.perform(put("/api/employees/" + id)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("职级不属于该部门")));
    }

}
