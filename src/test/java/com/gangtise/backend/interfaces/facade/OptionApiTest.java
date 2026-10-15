package com.gangtise.backend.interfaces.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gangtise.backend.domain.aggregate.Department;
import com.gangtise.backend.domain.aggregate.entity.DepartmentRank;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentMapper;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentRankMapper;
import com.gangtise.backend.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 下拉选项接口集成测试（依赖 docs/db/seed-*.sql 已执行）。
 *
 * <p>重点验证「职级隶属于部门」：职级下拉必须按部门过滤。
 */
class OptionApiTest extends ApiTestSupport {

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

    @Test
    @DisplayName("职级下拉按部门过滤，只返回该部门下的职级")
    void ranksFollowDepartment() throws Exception {
        MockHttpSession session = login();

        // 生产部 -> O 序列 5 级
        mockMvc.perform(get("/api/options/ranks").session(session)
                        .param("department", String.valueOf(departmentId("生产部"))))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].label").value("O1（初级工）"))
                .andExpect(jsonPath("$.data[4].label").value("O5（车间主任）"));

        // 销售部 -> S 序列 4 级
        mockMvc.perform(get("/api/options/ranks").session(session)
                        .param("department", String.valueOf(departmentId("销售部"))))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].label").value("S1（销售代表）"));

        // 技术研发部 -> T 序列 5 级
        mockMvc.perform(get("/api/options/ranks").session(session)
                        .param("department", String.valueOf(departmentId("技术研发部"))))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].label").value("T1（初级工程师）"));
    }

    @Test
    @DisplayName("职级下拉必须带部门参数，未传时返回空集合")
    void ranksRequireDepartment() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/options/ranks").session(session))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("每个职级都必须有隶属于部门")
    void everyRankHasDepartment() {
        Long orphan = departmentRankMapper.selectCount(new LambdaQueryWrapper<DepartmentRank>()
                .isNull(DepartmentRank::getDepartment));
        assertNotNull(orphan);
        org.junit.jupiter.api.Assertions.assertEquals(0L, orphan, "存在未隶属于部门的职级，请检查 seed 脚本");
    }

    @Test
    @DisplayName("未登录不能获取下拉选项")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/options/ranks").param("department", "1"))
                .andExpect(status().isUnauthorized());
    }

}
