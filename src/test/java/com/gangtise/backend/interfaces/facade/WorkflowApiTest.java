package com.gangtise.backend.interfaces.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.gangtise.backend.domain.aggregate.Role;
import com.gangtise.backend.domain.aggregate.User;
import com.gangtise.backend.domain.aggregate.Workflow;
import com.gangtise.backend.domain.aggregate.entity.WorkflowNode;
import com.gangtise.backend.infrastructure.enums.ApprovalModeEnum;
import com.gangtise.backend.infrastructure.enums.ApprovalTypeEnum;
import com.gangtise.backend.infrastructure.enums.RoleStatusEnum;
import com.gangtise.backend.infrastructure.enums.WorkflowNodeTypeEnum;
import com.gangtise.backend.infrastructure.enums.WorkflowStatusEnum;
import com.gangtise.backend.infrastructure.repository.mapper.RoleMapper;
import com.gangtise.backend.infrastructure.repository.mapper.WorkflowMapper;
import com.gangtise.backend.infrastructure.repository.mapper.WorkflowNodeMapper;
import com.gangtise.backend.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 流程（审批规则）接口集成测试。
 *
 * <p>重点锁五件事：
 * <ol>
 *   <li><b>未登录不可读写</b>——审批规则决定谁能审什么，属于管理面数据；</li>
 *   <li><b>节点链的形状</b>：起始/结束各一个且守在首尾、审批节点配置齐全，
 *       这些约束一旦放过，库里就会出现「打不开或推不动」的规则；</li>
 *   <li><b>起始/结束节点不带审批配置</b>：客户端硬塞进来也要被服务端清掉；</li>
 *   <li><b>节点是差量同步而不是整批重建</b>：老节点的 id 必须保住
 *       （{@code work_order.create_node_id} 指向它）；</li>
 *   <li><b>删除流程要级联删节点</b>，否则留下永远查不到的孤儿节点。</li>
 * </ol>
 *
 * <p>用例自建数据并随事务回滚，不污染本地库。
 */
class WorkflowApiTest extends ApiTestSupport {

    private static final int NODE_START = 0;
    private static final int NODE_APPROVAL = 1;
    private static final int NODE_END = 2;

    @Autowired
    private WorkflowMapper workflowMapper;

    @Autowired
    private WorkflowNodeMapper workflowNodeMapper;

    @Autowired
    private RoleMapper roleMapper;

    /* ------------------------------------------------------------------ 鉴权 */

    @Test
    @DisplayName("未登录不能查询、新增、修改、删除流程")
    void requiresLogin() throws Exception {
        Integer id = insertFlow("IT-WF-ANON");

        mockMvc.perform(get("/api/workflows")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/workflows/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(flowBody("IT-WF-ANON-NEW",
                                WorkflowStatusEnum.ENABLED.getValue(),
                                simpleChain(anyUserId(), "主管审批")))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/workflows/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(flowBody("IT-WF-ANON-EDIT",
                                WorkflowStatusEnum.ENABLED.getValue(),
                                simpleChain(anyUserId(), "主管审批")))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/workflows/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/workflows/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isUnauthorized());

        assertEquals(WorkflowStatusEnum.ENABLED.getValue(),
                workflowMapper.selectById(id).getStatus(), "匿名请求不应改动任何数据");
    }

    /* ------------------------------------------------------------------ 新增 */

    @Test
    @DisplayName("新增流程：节点按提交顺序落库，审批配置拼成逗号分隔的ID串")
    void createsFlowWithOrderedNodes() throws Exception {
        MockHttpSession session = login();
        Integer approver = anyUserId();
        String name = uniqueName("请假申请");

        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(node(null, "发起申请", NODE_START));
        nodes.add(approvalNode(null, "部门主管审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                List.of(approver), ApprovalModeEnum.ANY.getValue()));
        nodes.add(node(null, "审批完成", NODE_END));

        MvcResult result = mockMvc.perform(post("/api/workflows").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                flowBody(name, WorkflowStatusEnum.ENABLED.getValue(), nodes))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Integer flowId = readJson(result).path("data").asInt();

        List<WorkflowNode> stored = nodesOf(flowId);
        assertEquals(3, stored.size());
        assertEquals(List.of(0, 1, 2), stored.stream().map(WorkflowNode::getSort).toList(),
                "sort 由服务端按下标重排，0 起连续");
        assertEquals(NODE_START, stored.get(0).getType());
        assertEquals(NODE_APPROVAL, stored.get(1).getType());
        assertEquals(NODE_END, stored.get(2).getType());
        assertEquals(String.valueOf(approver), stored.get(1).getApprovalValues());
        assertEquals(ApprovalTypeEnum.ASSIGNEE.getValue(), stored.get(1).getApprovalType());
        assertEquals(ApprovalModeEnum.ANY.getValue(), stored.get(1).getApprovalMode());
    }

    @Test
    @DisplayName("起始与结束节点被硬塞审批配置时，服务端一律置空")
    void clearsApprovalConfigOnStartAndEndNodes() throws Exception {
        MockHttpSession session = login();
        Integer approver = anyUserId();

        Map<String, Object> start = node(null, "发起申请", NODE_START);
        start.put("approvalType", ApprovalTypeEnum.ASSIGNEE.getValue());
        start.put("approvalValues", List.of(approver));
        start.put("approvalMode", ApprovalModeEnum.ANY.getValue());

        Map<String, Object> end = node(null, "审批完成", NODE_END);
        end.put("approvalType", ApprovalTypeEnum.ROLE.getValue());
        end.put("approvalValues", List.of(1));
        end.put("approvalMode", ApprovalModeEnum.ALL.getValue());

        List<Map<String, Object>> nodes = List.of(start,
                approvalNode(null, "主管审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                        List.of(approver), ApprovalModeEnum.ANY.getValue()),
                end);

        Integer flowId = createFlow(session, uniqueName("边界流程"), nodes);
        List<WorkflowNode> stored = nodesOf(flowId);

        for (WorkflowNode item : stored) {
            if (WorkflowNodeTypeEnum.isApproval(item.getType())) {
                continue;
            }
            assertNull(item.getApprovalType(),
                    "起始/结束节点不该带审批方式，实际：" + item.getApprovalType());
            assertNull(item.getApprovalValues(), "起始/结束节点不该带审批对象");
            assertNull(item.getApprovalMode(), "起始/结束节点不该带审批模式");
        }
    }

    @Test
    @DisplayName("流程名称唯一：重名直接拒绝并给出可读提示")
    void rejectsDuplicateName() throws Exception {
        MockHttpSession session = login();
        String name = uniqueName("入职审批");
        createFlow(session, name, simpleChain(anyUserId(), "主管审批"));

        mockMvc.perform(post("/api/workflows").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                flowBody(name, WorkflowStatusEnum.ENABLED.getValue(),
                                        simpleChain(anyUserId(), "主管审批")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("流程名称已存在：" + name));

        assertEquals(1, flowsNamed(name).size(), "拒绝之后不应多出一条");
    }

    /* ------------------------------------------------------------ 节点链校验 */

    @Test
    @DisplayName("缺少起始或结束节点、或两者不在首尾，都被拒绝")
    void rejectsMalformedChain() throws Exception {
        MockHttpSession session = login();
        Integer approver = anyUserId();

        // 只有审批 + 结束，没有起始
        assertRejected(session, "缺起始节点",
                List.of(approvalNode(null, "主管审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                                List.of(approver), ApprovalModeEnum.ANY.getValue()),
                        node(null, "审批完成", NODE_END)),
                "流程必须有且只有一个起始节点，当前：0 个");

        // 只有起始 + 审批，没有结束
        assertRejected(session, "缺结束节点",
                List.of(node(null, "发起申请", NODE_START),
                        approvalNode(null, "主管审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                                List.of(approver), ApprovalModeEnum.ANY.getValue())),
                "流程必须有且只有一个结束节点，当前：0 个");

        // 起始不在首位
        assertRejected(session, "起始不在首位",
                List.of(approvalNode(null, "主管审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                                List.of(approver), ApprovalModeEnum.ANY.getValue()),
                        node(null, "发起申请", NODE_START),
                        node(null, "审批完成", NODE_END)),
                "起始节点必须排在第一位");

        // 结束不在末位
        assertRejected(session, "结束不在末位",
                List.of(node(null, "发起申请", NODE_START),
                        node(null, "审批完成", NODE_END),
                        approvalNode(null, "主管审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                                List.of(approver), ApprovalModeEnum.ANY.getValue())),
                "结束节点必须排在最后一位");

        // 只有一个节点
        assertRejected(session, "节点不足",
                List.of(node(null, "发起申请", NODE_START)),
                "流程至少要包含「起始」与「结束」两个节点");
    }

    @Test
    @DisplayName("审批节点缺审批方式 / 审批对象 / 审批模式都被拒绝")
    void rejectsIncompleteApprovalNode() throws Exception {
        MockHttpSession session = login();
        Integer approver = anyUserId();

        assertRejected(session, "缺审批方式",
                List.of(node(null, "发起申请", NODE_START),
                        approvalNode(null, "主管审批", null, List.of(approver),
                                ApprovalModeEnum.ANY.getValue()),
                        node(null, "审批完成", NODE_END)),
                "第 1 个审批节点未选择审批方式");

        assertRejected(session, "缺审批人",
                List.of(node(null, "发起申请", NODE_START),
                        approvalNode(null, "主管审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                                List.of(), ApprovalModeEnum.ANY.getValue()),
                        node(null, "审批完成", NODE_END)),
                "第 1 个审批节点未指定审批人");

        assertRejected(session, "缺审批模式",
                List.of(node(null, "发起申请", NODE_START),
                        approvalNode(null, "主管审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                                List.of(approver), null),
                        node(null, "审批完成", NODE_END)),
                "第 1 个审批节点未选择审批模式（或签 / 会签）");
    }

    @Test
    @DisplayName("「直属上级」当前不可用：配了也执行不了，直接拒绝")
    void rejectsUnsupportedApprovalType() throws Exception {
        MockHttpSession session = login();

        assertRejected(session, "直属上级",
                List.of(node(null, "发起申请", NODE_START),
                        approvalNode(null, "上级审批", ApprovalTypeEnum.SUPERIOR.getValue(),
                                List.of(anyUserId()), ApprovalModeEnum.ANY.getValue()),
                        node(null, "审批完成", NODE_END)),
                "第 1 个节点的审批方式「直属上级」当前版本不支持，请改用「指定人员」或「指定角色」");
    }

    @Test
    @DisplayName("审批人 / 审批角色必须真实存在")
    void rejectsUnknownApproverOrRole() throws Exception {
        MockHttpSession session = login();

        assertRejected(session, "审批人不存在",
                List.of(node(null, "发起申请", NODE_START),
                        approvalNode(null, "主管审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                                List.of(99999999), ApprovalModeEnum.ANY.getValue()),
                        node(null, "审批完成", NODE_END)),
                "第 1 个审批节点选中的审批人已不存在，请重新选择");

        assertRejected(session, "审批角色不存在",
                List.of(node(null, "发起申请", NODE_START),
                        approvalNode(null, "角色审批", ApprovalTypeEnum.ROLE.getValue(),
                                List.of(99999999), ApprovalModeEnum.ANY.getValue()),
                        node(null, "审批完成", NODE_END)),
                "第 1 个审批节点选中的审批角色已被删除，请重新选择");
    }

    /* ------------------------------------------------------------------ 修改 */

    @Test
    @DisplayName("修改流程是差量同步：老节点保住 id，新节点插入，没提交的节点被删掉")
    void updatesNodesIncrementally() throws Exception {
        MockHttpSession session = login();
        Integer approver = anyUserId();

        List<Map<String, Object>> created = new ArrayList<>();
        created.add(node(null, "发起申请", NODE_START));
        created.add(approvalNode(null, "一级审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                List.of(approver), ApprovalModeEnum.ANY.getValue()));
        created.add(approvalNode(null, "二级审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                List.of(approver), ApprovalModeEnum.ALL.getValue()));
        created.add(node(null, "审批完成", NODE_END));
        Integer flowId = createFlow(session, uniqueName("两级审批"), created);

        List<WorkflowNode> before = nodesOf(flowId);
        assertEquals(4, before.size());
        Integer startId = before.get(0).getId();
        Integer firstApprovalId = before.get(1).getId();
        Integer secondApprovalId = before.get(2).getId();
        Integer endId = before.get(3).getId();

        // 保留起始与结束、给一级审批改名并改成会签、去掉二级审批、新增一个角色审批节点
        List<Map<String, Object>> updated = List.of(
                node(startId, "发起申请", NODE_START),
                approvalNode(firstApprovalId, "一级审批（已改名）", ApprovalTypeEnum.ASSIGNEE.getValue(),
                        List.of(approver), ApprovalModeEnum.ALL.getValue()),
                approvalNode(null, "三级审批", ApprovalTypeEnum.ROLE.getValue(),
                        List.of(anyRoleId()), ApprovalModeEnum.ANY.getValue()),
                node(endId, "审批完成", NODE_END));

        mockMvc.perform(put("/api/workflows/" + flowId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                flowBody(uniqueName("两级审批-改"), WorkflowStatusEnum.ENABLED.getValue(),
                                        updated))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<WorkflowNode> after = nodesOf(flowId);
        assertEquals(4, after.size());
        assertEquals(startId, after.get(0).getId(), "起始节点是同一行（id 不变），不是被重建的");
        assertEquals(firstApprovalId, after.get(1).getId(), "一级审批是同一行");
        assertEquals(endId, after.get(3).getId(), "结束节点是同一行");
        assertEquals("一级审批（已改名）", after.get(1).getNodeName());
        assertEquals(ApprovalModeEnum.ALL.getValue(), after.get(1).getApprovalMode());
        assertEquals(ApprovalTypeEnum.ROLE.getValue(), after.get(2).getApprovalType());
        assertTrue(nodesOf(flowId).stream().noneMatch(item -> secondApprovalId.equals(item.getId())),
                "本次未提交的二级审批节点应被删除");
        assertEquals(List.of(0, 1, 2, 3), after.stream().map(WorkflowNode::getSort).toList(),
                "改完顺序要重新连续排号");
    }

    @Test
    @DisplayName("带上不属于本流程的节点ID时拒绝，而不是悄悄改到别的流程上")
    void rejectsForeignKeyNodeId() throws Exception {
        MockHttpSession session = login();
        Integer approver = anyUserId();

        Integer flowA = createFlow(session, uniqueName("流程A"),
                simpleChain(approver, "主管审批"));
        Integer flowB = createFlow(session, uniqueName("流程B"),
                simpleChain(approver, "主管审批"));
        Integer foreignNodeId = nodesOf(flowB).get(1).getId();

        String body = objectMapper.writeValueAsString(flowBody(uniqueName("流程A-改"),
                WorkflowStatusEnum.ENABLED.getValue(),
                List.of(node(null, "发起申请", NODE_START),
                        approvalNode(foreignNodeId, "主管审批", ApprovalTypeEnum.ASSIGNEE.getValue(),
                                List.of(approver), ApprovalModeEnum.ANY.getValue()),
                        node(null, "审批完成", NODE_END))));

        mockMvc.perform(put("/api/workflows/" + flowA).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(
                        "节点数据已变化（id=" + foreignNodeId + "），请刷新页面后重新编辑"));

        assertEquals("主管审批", nodesOf(flowB).get(1).getNodeName(), "别的流程的节点不应被动过");
        assertEquals(3, nodesOf(flowA).size(), "校验失败时不应留下半截数据");
    }

    /* ------------------------------------------------------------ 详情与列表 */

    @Test
    @DisplayName("详情返回节点链摘要与可读的审批人；列表不带节点明细")
    void detailExposesReadableApprovers() throws Exception {
        MockHttpSession session = login();
        Integer approver = anyUserId();
        Integer flowId = createFlow(session, uniqueName("报销审批"),
                simpleChain(approver, "主管审批"));

        MvcResult detail = mockMvc.perform(get("/api/workflows/" + flowId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode data = readJson(detail).path("data");

        assertEquals("发起申请 → 主管审批 → 审批完成", data.path("nodeSummary").asText());
        assertEquals(3, data.path("nodeCount").asInt());
        assertEquals(1, data.path("approvalNodeCount").asInt());
        assertEquals(3, data.path("nodes").size());
        assertEquals(TEST_USERNAME, data.path("nodes").get(1).path("approvalValueText").asText(),
                "审批人应展示成名字，而不是埋一个用户ID");
        assertTrue(data.path("nodes").get(0).path("approvalValueText").isMissingNode(),
                "起始节点没有审批人，不该编一个出来");

        MvcResult page = mockMvc.perform(get("/api/workflows")
                        .param("keyword", uniqueKeywordOf(flowId))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode record = readJson(page).path("data").path("records").get(0);
        assertEquals("发起申请 → 主管审批 → 审批完成", record.path("nodeSummary").asText());
        assertTrue(record.path("nodes").isMissingNode(),
                "列表不需要节点明细：每条流程都塞一遍节点，翻一页要多传几十个对象");
    }

    @Test
    @DisplayName("按状态筛选只返回该状态的流程")
    void filtersByStatus() throws Exception {
        MockHttpSession session = login();
        String keyword = "IT-WF-STATUS-" + System.nanoTime();

        createFlow(session, keyword + "-启用", simpleChain(anyUserId(), "主管审批"));
        Integer disabledId = createFlowWithStatus(session, keyword + "-禁用",
                simpleChain(anyUserId(), "主管审批"), WorkflowStatusEnum.DISABLED.getValue());

        MvcResult result = mockMvc.perform(get("/api/workflows")
                        .param("keyword", keyword)
                        .param("status", String.valueOf(WorkflowStatusEnum.DISABLED.getValue()))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = readJson(result).path("data");
        assertEquals(1, data.path("total").asInt(), "只应有那条被禁用的流程");
        assertEquals(disabledId.intValue(), data.path("records").get(0).path("id").asInt());
        assertEquals("禁用", data.path("records").get(0).path("statusText").asText());
    }

    /* ------------------------------------------------------------ 启停与删除 */

    @Test
    @DisplayName("启停：能改；重复设置同一状态与非法状态都被拒绝")
    void changeStatusRules() throws Exception {
        MockHttpSession session = login();
        Integer flowId = createFlow(session, uniqueName("转正审批"),
                simpleChain(anyUserId(), "主管审批"));

        mockMvc.perform(post("/api/workflows/" + flowId + "/status").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(WorkflowStatusEnum.DISABLED.getValue(),
                workflowMapper.selectById(flowId).getStatus());

        mockMvc.perform(post("/api/workflows/" + flowId + "/status").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该流程已是「禁用」状态，无需重复操作"));

        mockMvc.perform(post("/api/workflows/" + flowId + "/status").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":9}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("流程状态不正确：9"));

        mockMvc.perform(post("/api/workflows/" + flowId + "/status").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("status 不能为空"));
    }

    @Test
    @DisplayName("删除流程时连同节点一起清掉，不留孤儿节点")
    void deletesNodesWithFlow() throws Exception {
        MockHttpSession session = login();
        Integer flowId = createFlow(session, uniqueName("离职审批"),
                simpleChain(anyUserId(), "主管审批"));
        assertEquals(3, nodesOf(flowId).size());

        mockMvc.perform(delete("/api/workflows/" + flowId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertNull(workflowMapper.selectById(flowId));
        assertTrue(nodesOf(flowId).isEmpty(),
                "孤儿节点查不出来也删不掉，只会越积越多");
    }

    @Test
    @DisplayName("流程不存在时读写都报业务错误")
    void rejectsUnknownFlow() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/workflows/99999999").session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("流程不存在或已被删除"));

        mockMvc.perform(put("/api/workflows/99999999").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(flowBody("任意名称",
                                WorkflowStatusEnum.ENABLED.getValue(),
                                simpleChain(anyUserId(), "主管审批")))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("流程不存在或已被删除"));

        mockMvc.perform(delete("/api/workflows/99999999").session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("流程不存在或已被删除"));
    }

    /* ------------------------------------------------------------------ 工具 */

    /** 断言请求被业务校验拒绝，并且库里没有留下任何新流程（先按提示文案锁住原因）。 */
    private void assertRejected(MockHttpSession session, String label,
                                List<Map<String, Object>> nodes, String expectedMessage) throws Exception {
        long before = workflowMapper.selectCount(null);
        String name = label + "-" + System.nanoTime();

        mockMvc.perform(post("/api/workflows").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                flowBody(name, WorkflowStatusEnum.ENABLED.getValue(), nodes))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(expectedMessage));

        assertEquals(before, workflowMapper.selectCount(null),
                "校验失败不该留下半截流程：" + label);
        assertTrue(flowsNamed(name).isEmpty());
    }

    private Integer createFlow(MockHttpSession session, String name,
                               List<Map<String, Object>> nodes) throws Exception {
        return createFlowWithStatus(session, name, nodes, WorkflowStatusEnum.ENABLED.getValue());
    }

    private Integer createFlowWithStatus(MockHttpSession session, String name,
                                         List<Map<String, Object>> nodes, Integer status) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workflows").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(flowBody(name, status, nodes))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Integer id = readJson(result).path("data").asInt();
        assertTrue(id > 0, "新增应返回流程ID");
        return id;
    }

    private Map<String, Object> flowBody(String name, Integer status, List<Map<String, Object>> nodes) {
        Map<String, Object> body = new HashMap<>();
        body.put("workflowName", name);
        body.put("status", status);
        body.put("nodes", nodes);
        return body;
    }

    /** 起始 + 一个审批 + 结束，最简链路。 */
    private List<Map<String, Object>> simpleChain(Integer approverId, String approvalName) {
        return List.of(
                node(null, "发起申请", NODE_START),
                approvalNode(null, approvalName, ApprovalTypeEnum.ASSIGNEE.getValue(),
                        List.of(approverId), ApprovalModeEnum.ANY.getValue()),
                node(null, "审批完成", NODE_END));
    }

    private static Map<String, Object> node(Integer id, String name, int type) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("nodeName", name);
        node.put("type", type);
        return node;
    }

    private static Map<String, Object> approvalNode(Integer id, String name, Integer approvalType,
                                                    List<Integer> values, Integer mode) {
        Map<String, Object> node = node(id, name, NODE_APPROVAL);
        node.put("approvalType", approvalType);
        node.put("approvalValues", values);
        node.put("approvalMode", mode);
        return node;
    }

    /** 直接插一条流程（绕过接口），只用来验证「匿名请求不动数据」。 */
    private Integer insertFlow(String name) {
        Workflow workflow = new Workflow();
        workflow.setWorkflowName(name + "-" + System.nanoTime());
        workflow.setStatus(WorkflowStatusEnum.ENABLED.getValue());
        workflowMapper.insert(workflow);
        assertNotNull(workflow.getId());
        return workflow.getId();
    }

    private List<WorkflowNode> nodesOf(Integer workflowId) {
        return workflowNodeMapper.selectList(new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowId, workflowId)
                .orderByAsc(WorkflowNode::getSort));
    }

    private List<Workflow> flowsNamed(String name) {
        return workflowMapper.selectList(new LambdaQueryWrapper<Workflow>()
                .eq(Workflow::getWorkflowName, name));
    }

    private Integer anyUserId() {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, TEST_USERNAME).last("limit 1"));
        assertNotNull(user, "基类应已准备好测试账号");
        return user.getId();
    }

    private Integer anyRoleId() {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .orderByAsc(Role::getId).last("limit 1"));
        if (role != null) {
            return role.getId();
        }
        Role created = new Role();
        created.setName("流程测试角色-" + System.nanoTime());
        created.setStatus(RoleStatusEnum.ENABLED.getValue());
        roleMapper.insert(created);
        return created.getId();
    }

    /** 名称带纳秒后缀：同一个测试方法里要建多条流程，而名称是唯一的。 */
    private static String uniqueName(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    /** 列表断言用的关键字：直接复用流程名，避免与库里既有数据混淆。 */
    private String uniqueKeywordOf(Integer flowId) {
        return workflowMapper.selectById(flowId).getWorkflowName();
    }

}
