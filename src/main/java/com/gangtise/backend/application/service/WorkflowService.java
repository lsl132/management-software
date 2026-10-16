package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gangtise.backend.application.dto.PageResult;
import com.gangtise.backend.application.dto.WorkflowNodeSaveDTO;
import com.gangtise.backend.application.dto.WorkflowNodeVO;
import com.gangtise.backend.application.dto.WorkflowQueryDTO;
import com.gangtise.backend.application.dto.WorkflowSaveDTO;
import com.gangtise.backend.application.dto.WorkflowVO;
import com.gangtise.backend.domain.aggregate.Role;
import com.gangtise.backend.domain.aggregate.User;
import com.gangtise.backend.domain.aggregate.Workflow;
import com.gangtise.backend.domain.aggregate.entity.WorkflowNode;
import com.gangtise.backend.infrastructure.enums.ApprovalModeEnum;
import com.gangtise.backend.infrastructure.enums.ApprovalTypeEnum;
import com.gangtise.backend.infrastructure.enums.WorkflowNodeTypeEnum;
import com.gangtise.backend.infrastructure.enums.WorkflowStatusEnum;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.repository.mapper.RoleMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserMapper;
import com.gangtise.backend.infrastructure.repository.mapper.WorkflowMapper;
import com.gangtise.backend.infrastructure.repository.mapper.WorkflowNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程应用服务（权限字典里的「流程管理」，即审批规则）。
 *
 * <p>一条审批规则 = 一个流程头（{@code workflow}）+ 一串有序节点（{@code workflow_node}）。
 * 节点的形状是「起始 → 若干审批 → 结束」，所以保存时按以下规则校验，任一不满足直接拒绝：
 * <ol>
 *   <li>至少 2 个节点；起始节点与结束节点**恰好各一个**，且分别位于首尾；</li>
 *   <li>审批节点必须有审批方式与审批模式；指定人员/指定角色时还必须给定对象
 *       （且对象在库里真实存在）；</li>
 *   <li>起始与结束节点**不允许**带审批配置——服务端一律置空，而不是信任前端不填。</li>
 * </ol>
 *
 * <p><b>节点顺序不由客户端传</b>：直接取数组下标，保存时统一重排（0、1、2…）。
 * 顺序本身就是数组，再传一份 sort 只会出现两份互相矛盾的次序。
 *
 * <p><b>节点用差量同步而不是「清空重建」</b>：{@code work_order.create_node_id} 指向具体节点，
 * 整批删掉再插入会让在途工单的当前节点变成悬空引用——不报错，只是那张单子再也推不动。
 * 所以只对「本次未提交的节点」做删除。
 *
 * <p><b>已知缺口</b>：删除流程时**没有**校验 {@code work_order} 是否引用它——工单功能尚未实现
 * （只有建表语句与实体，没有 Mapper / Service / 页面），表里恒为空。等工单落地后，
 * 这里必须补上引用校验，否则会把在途的单子指向一个已不存在的流程。
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    /** 节点链的下限：起始 + 结束。只有这两个节点的流程等于「提交即结束」，是合法的最简审批。 */
    private static final int MIN_NODE_COUNT = 2;

    /** approval_values 的多值分隔符，与列注释一致。 */
    private static final String VALUE_SEPARATOR = ",";

    /** 节点链路摘要的分隔符。 */
    private static final String SUMMARY_ARROW = " → ";

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    public WorkflowService(WorkflowMapper workflowMapper,
                           WorkflowNodeMapper workflowNodeMapper,
                           UserMapper userMapper,
                           RoleMapper roleMapper) {
        this.workflowMapper = workflowMapper;
        this.workflowNodeMapper = workflowNodeMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    /* ------------------------------------------------------------------ 查询 */

    /** 分页查询，支持关键字（流程名称/描述）与状态过滤。 */
    public PageResult<WorkflowVO> page(WorkflowQueryDTO query) {
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1L : query.getCurrent();
        long size = query.getSize() == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<>();
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        if (keyword != null) {
            wrapper.and(w -> w.like(Workflow::getWorkflowName, keyword)
                    .or().like(Workflow::getDescription, keyword));
        }
        if (query.getStatus() != null) {
            wrapper.eq(Workflow::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Workflow::getId);

        Page<Workflow> page = workflowMapper.selectPage(new Page<>(current, size), wrapper);

        // 一次把这页的节点全查回来再分组，避免每条流程各查一次（翻一页 10 条就是 10 次查询）
        Map<Integer, List<WorkflowNode>> nodesByWorkflow = nodesByWorkflow(
                page.getRecords().stream().map(Workflow::getId).toList());
        ValueNames names = loadValueNames(nodesByWorkflow);

        List<WorkflowVO> records = page.getRecords().stream()
                .map(workflow -> toVO(workflow,
                        nodesByWorkflow.getOrDefault(workflow.getId(), List.of()), names, false))
                .toList();

        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /** 流程详情，带完整节点链（编辑弹窗与详情弹窗共用）。 */
    public WorkflowVO detail(Integer id) {
        Workflow workflow = requireWorkflow(id);
        List<WorkflowNode> nodes = listNodes(id);
        return toVO(workflow, nodes, loadValueNames(Map.of(id, nodes)), true);
    }

    /* ------------------------------------------------------------------ 写入 */

    /** 新增流程（连同节点链，单事务）。 */
    @Transactional(rollbackFor = Exception.class)
    public Integer create(WorkflowSaveDTO dto) {
        String name = dto.getWorkflowName().trim();
        ensureNameUnique(name, null);
        ensureStatusValid(dto.getStatus());
        List<WorkflowNode> nodes = normalizeNodes(dto.getNodes());

        Workflow workflow = new Workflow();
        workflow.setWorkflowName(name);
        workflow.setIcon(trimToNull(dto.getIcon()));
        workflow.setDescription(trimToNull(dto.getDescription()));
        workflow.setStatus(dto.getStatus());
        workflowMapper.insert(workflow);

        for (WorkflowNode node : nodes) {
            node.setWorkflowId(workflow.getId());
            workflowNodeMapper.insert(node);
        }
        log.info("[WORKFLOW] 新增流程 -> workflowId={}, name={}, status={}, 节点 {} 个（审批 {} 个）",
                workflow.getId(), name, dto.getStatus(), nodes.size(), countApproval(nodes));
        return workflow.getId();
    }

    /**
     * 修改流程：流程头与节点链整体覆盖。
     *
     * <p>{@code template} 不在本次表单范围内，显式不动它——用 {@code updateById} 会因为
     * 实体上 {@code workflowName} 是 {@code ALWAYS} 策略而把未传字段一并写空，
     * 所以这里同样走显式列更新。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer id, WorkflowSaveDTO dto) {
        requireWorkflow(id);
        String name = dto.getWorkflowName().trim();
        ensureNameUnique(name, id);
        ensureStatusValid(dto.getStatus());
        List<WorkflowNode> nodes = normalizeNodes(dto.getNodes());

        workflowMapper.update(null, Wrappers.<Workflow>lambdaUpdate()
                .eq(Workflow::getId, id)
                .set(Workflow::getWorkflowName, name)
                .set(Workflow::getIcon, trimToNull(dto.getIcon()))
                .set(Workflow::getDescription, trimToNull(dto.getDescription()))
                .set(Workflow::getStatus, dto.getStatus())
                .set(Workflow::getUpdateTime, LocalDateTime.now()));

        int removed = syncNodes(id, nodes);
        log.info("[WORKFLOW] 修改流程 -> workflowId={}, name={}, 节点 {} 个（审批 {} 个），清理 {} 个",
                id, name, nodes.size(), countApproval(nodes), removed);
    }

    /** 删除流程：同事务级联删除其全部节点，避免留下永远查不到的孤儿节点。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        Workflow existing = requireWorkflow(id);
        int nodes = workflowNodeMapper.delete(Wrappers.<WorkflowNode>lambdaQuery()
                .eq(WorkflowNode::getWorkflowId, id));
        workflowMapper.deleteById(id);
        log.info("[WORKFLOW] 删除流程 -> workflowId={}, name={}, 一并清理节点 {} 个",
                id, existing.getWorkflowName(), nodes);
    }

    /**
     * 启用 / 禁用流程（列表行内快捷操作）。
     *
     * <p>只改状态、不动节点：停用一条规则是「先别用」，不是「改它」。
     * 重复设置同一状态直接提示，避免用户以为点了没生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Integer id, Integer status) {
        Workflow existing = requireWorkflow(id);
        ensureStatusValid(status);
        if (status.equals(existing.getStatus())) {
            throw new BizException("该流程已是「" + WorkflowStatusEnum.descriptionOf(status) + "」状态，无需重复操作");
        }
        workflowMapper.update(null, Wrappers.<Workflow>lambdaUpdate()
                .eq(Workflow::getId, id)
                .set(Workflow::getStatus, status)
                .set(Workflow::getUpdateTime, LocalDateTime.now()));
        log.info("[WORKFLOW] 流程状态变更 -> workflowId={}, name={}, {} -> {}",
                id, existing.getWorkflowName(), existing.getStatus(), status);
    }

    /* -------------------------------------------------------------- 节点同步 */

    /**
     * 把提交的节点链同步到库里：有 id 的改、无 id 的插、本次未出现的删。
     *
     * @return 删除的节点数
     */
    private int syncNodes(Integer workflowId, List<WorkflowNode> normalized) {
        List<WorkflowNode> existing = listNodes(workflowId);
        Set<Integer> existingIds = existing.stream()
                .map(WorkflowNode::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Integer> submittedIds = new LinkedHashSet<>();
        for (WorkflowNode node : normalized) {
            Integer nodeId = node.getId();
            if (nodeId == null) {
                continue;
            }
            // 带了一个不属于本流程的节点ID：要么是页面数据过期，要么是有人在改别的流程，一律拒绝
            if (!existingIds.contains(nodeId)) {
                throw new BizException("节点数据已变化（id=" + nodeId + "），请刷新页面后重新编辑");
            }
            submittedIds.add(nodeId);
        }

        for (WorkflowNode node : normalized) {
            // workflow_id 在实体上是 FieldStrategy.ALWAYS：更新分支若不带值，会把列写成 NULL，
            // 而它是 not null——报出来就是一个看不懂的约束冲突。这里统一补齐（老节点本就属于该流程）
            node.setWorkflowId(workflowId);
            if (node.getId() == null) {
                workflowNodeMapper.insert(node);
            } else {
                workflowNodeMapper.updateById(node);
            }
        }

        List<Integer> removedIds = existingIds.stream()
                .filter(nodeId -> !submittedIds.contains(nodeId))
                .toList();
        if (!removedIds.isEmpty()) {
            workflowNodeMapper.delete(Wrappers.<WorkflowNode>lambdaQuery()
                    .in(WorkflowNode::getId, removedIds));
        }
        return removedIds.size();
    }

    /* ------------------------------------------------------------------ 校验 */

    /**
     * 校验并归一化节点链。
     *
     * <p>归一化不是「顺手清理」，而是保证库里不会出现自相矛盾的节点：起始/结束节点
     * 被塞了审批人、审批节点缺审批人这类数据，读出来谁也不知道该按哪份为准。
     *
     * @return 已重排 sort 的节点实体（{@code id} 为空表示新增）
     */
    private List<WorkflowNode> normalizeNodes(List<WorkflowNodeSaveDTO> nodes) {
        if (nodes == null || nodes.size() < MIN_NODE_COUNT) {
            throw new BizException("流程至少要包含「起始」与「结束」两个节点");
        }

        int startCount = 0;
        int endCount = 0;
        for (int i = 0; i < nodes.size(); i++) {
            Integer type = nodes.get(i).getType();
            if (WorkflowNodeTypeEnum.isStart(type)) {
                startCount++;
            } else if (WorkflowNodeTypeEnum.isEnd(type)) {
                endCount++;
            } else if (!WorkflowNodeTypeEnum.isApproval(type)) {
                throw new BizException("第 " + (i + 1) + " 个节点的类型不正确：" + type);
            }
        }
        if (startCount != 1) {
            throw new BizException("流程必须有且只有一个起始节点，当前：" + startCount + " 个");
        }
        if (endCount != 1) {
            throw new BizException("流程必须有且只有一个结束节点，当前：" + endCount + " 个");
        }
        if (!WorkflowNodeTypeEnum.isStart(nodes.get(0).getType())) {
            throw new BizException("起始节点必须排在第一位");
        }
        if (!WorkflowNodeTypeEnum.isEnd(nodes.get(nodes.size() - 1).getType())) {
            throw new BizException("结束节点必须排在最后一位");
        }

        List<WorkflowNode> result = new ArrayList<>(nodes.size());
        // 报错文案里的序号只数审批节点：用户看到的是「起始 / 审批 / 审批 / 结束」混排的行，
        // 说「第 1 个审批节点」他立刻能对上，说「第 2 个节点」还得自己数一遍
        int approvalPosition = 0;
        for (int i = 0; i < nodes.size(); i++) {
            WorkflowNodeSaveDTO dto = nodes.get(i);
            WorkflowNode node = new WorkflowNode();
            node.setId(dto.getId());
            node.setSort(i);
            node.setNodeName(dto.getNodeName().trim());
            node.setType(dto.getType());

            if (WorkflowNodeTypeEnum.isApproval(dto.getType())) {
                fillApproval(node, dto, ++approvalPosition);
            }
            // 起始/结束节点不带审批配置（实体上是 ALWAYS 策略，置 null 即清空历史值）
            result.add(node);
        }
        return result;
    }

    /** 审批节点的审批方式 / 审批对象 / 审批模式，三者都要有值且真实存在。 */
    private void fillApproval(WorkflowNode node, WorkflowNodeSaveDTO dto, int position) {
        Integer approvalType = dto.getApprovalType();
        if (approvalType == null) {
            throw new BizException("第 " + position + " 个审批节点未选择审批方式");
        }
        if (!ApprovalTypeEnum.isSupported(approvalType)) {
            throw new BizException("第 " + position + " 个节点的审批方式「"
                    + ApprovalTypeEnum.descriptionOf(approvalType)
                    + "」当前版本不支持，请改用「指定人员」或「指定角色」");
        }
        if (!ApprovalModeEnum.isValid(dto.getApprovalMode())) {
            throw new BizException("第 " + position + " 个审批节点未选择审批模式（或签 / 会签）");
        }

        List<Integer> values = normalizeValues(dto.getApprovalValues());
        if (values.isEmpty()) {
            String target = approvalType == ApprovalTypeEnum.ASSIGNEE.getValue() ? "审批人" : "审批角色";
            throw new BizException("第 " + position + " 个审批节点未指定" + target);
        }
        if (approvalType == ApprovalTypeEnum.ASSIGNEE.getValue()) {
            ensureUsersExist(values, position);
        } else {
            ensureRolesExist(values, position);
        }

        node.setApprovalType(approvalType);
        node.setApprovalValues(values.stream().map(String::valueOf)
                .collect(Collectors.joining(VALUE_SEPARATOR)));
        node.setApprovalMode(dto.getApprovalMode());
    }

    /** 去掉空值并按首次出现的顺序去重：同一个人被选两次没有意义，但顺序要稳定。 */
    private List<Integer> normalizeValues(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private void ensureUsersExist(List<Integer> userIds, int position) {
        long found = userMapper.selectCount(new LambdaQueryWrapper<User>().in(User::getId, userIds));
        if (found != userIds.size()) {
            throw new BizException("第 " + position + " 个审批节点选中的审批人已不存在，请重新选择");
        }
    }

    private void ensureRolesExist(List<Integer> roleIds, int position) {
        long found = roleMapper.selectCount(new LambdaQueryWrapper<Role>().in(Role::getId, roleIds));
        if (found != roleIds.size()) {
            throw new BizException("第 " + position + " 个审批节点选中的审批角色已被删除，请重新选择");
        }
    }

    private void ensureStatusValid(Integer status) {
        if (!WorkflowStatusEnum.isValid(status)) {
            throw new BizException("流程状态不正确：" + status);
        }
    }

    private Workflow requireWorkflow(Integer id) {
        Workflow workflow = id == null ? null : workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BizException("流程不存在或已被删除");
        }
        return workflow;
    }

    /** 流程名称唯一：库侧还有 {@code uk_workflow_name} 兜底，这里只是为了给出可读的提示。 */
    private void ensureNameUnique(String name, Integer excludeId) {
        Long count = workflowMapper.selectCount(new LambdaQueryWrapper<Workflow>()
                .eq(Workflow::getWorkflowName, name)
                .ne(excludeId != null, Workflow::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("流程名称已存在：" + name);
        }
    }

    /* ------------------------------------------------------------------ 装配 */

    private List<WorkflowNode> listNodes(Integer workflowId) {
        return workflowNodeMapper.selectList(Wrappers.<WorkflowNode>lambdaQuery()
                .eq(WorkflowNode::getWorkflowId, workflowId)
                .orderByAsc(WorkflowNode::getSort)
                .orderByAsc(WorkflowNode::getId));
    }

    /** 一次查出这批流程的全部节点，按 workflow_id 分组（组内已按 sort 升序）。 */
    private Map<Integer, List<WorkflowNode>> nodesByWorkflow(List<Integer> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return Map.of();
        }
        return workflowNodeMapper.selectList(Wrappers.<WorkflowNode>lambdaQuery()
                        .in(WorkflowNode::getWorkflowId, workflowIds)
                        .orderByAsc(WorkflowNode::getWorkflowId)
                        .orderByAsc(WorkflowNode::getSort)
                        .orderByAsc(WorkflowNode::getId))
                .stream()
                .filter(node -> node.getWorkflowId() != null)
                .collect(Collectors.groupingBy(WorkflowNode::getWorkflowId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    /** 审批配置值里的用户ID / 角色ID → 展示名。 */
    private ValueNames loadValueNames(Map<Integer, List<WorkflowNode>> nodesByWorkflow) {
        Set<Integer> userIds = new LinkedHashSet<>();
        Set<Integer> roleIds = new LinkedHashSet<>();

        for (List<WorkflowNode> nodes : nodesByWorkflow.values()) {
            for (WorkflowNode node : nodes) {
                Integer approvalType = node.getApprovalType();
                if (approvalType == null) {
                    continue;
                }
                for (Integer valueId : parseValues(node.getApprovalValues())) {
                    if (approvalType == ApprovalTypeEnum.ASSIGNEE.getValue()) {
                        userIds.add(valueId);
                    } else if (approvalType == ApprovalTypeEnum.ROLE.getValue()) {
                        roleIds.add(valueId);
                    }
                }
            }
        }

        Map<Integer, String> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, userIds)).stream()
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        Map<Integer, String> roles = roleIds.isEmpty() ? Map.of()
                : roleMapper.selectList(new LambdaQueryWrapper<Role>().in(Role::getId, roleIds)).stream()
                .collect(Collectors.toMap(Role::getId, Role::getName, (a, b) -> a));

        return new ValueNames(users, roles);
    }

    private WorkflowVO toVO(Workflow workflow, List<WorkflowNode> nodes,
                            ValueNames names, boolean withNodes) {
        WorkflowVO vo = new WorkflowVO();
        vo.setId(workflow.getId());
        vo.setIcon(workflow.getIcon());
        vo.setWorkflowName(workflow.getWorkflowName());
        vo.setDescription(workflow.getDescription());
        vo.setStatus(workflow.getStatus());
        vo.setStatusText(WorkflowStatusEnum.descriptionOf(workflow.getStatus()));
        vo.setNodeCount(nodes.size());
        vo.setApprovalNodeCount(countApproval(nodes));
        vo.setNodeSummary(nodes.stream()
                .map(WorkflowNode::getNodeName)
                .collect(Collectors.joining(SUMMARY_ARROW)));
        vo.setCreateTime(workflow.getCreateTime());
        vo.setUpdateTime(workflow.getUpdateTime());
        vo.setNodes(withNodes
                ? nodes.stream().map(node -> toNodeVO(node, names)).toList()
                : null);
        return vo;
    }

    private WorkflowNodeVO toNodeVO(WorkflowNode node, ValueNames names) {
        WorkflowNodeVO vo = new WorkflowNodeVO();
        vo.setId(node.getId());
        vo.setSort(node.getSort());
        vo.setNodeName(node.getNodeName());
        vo.setType(node.getType());
        vo.setTypeText(WorkflowNodeTypeEnum.descriptionOf(node.getType()));
        vo.setApprovalType(node.getApprovalType());
        vo.setApprovalTypeText(ApprovalTypeEnum.descriptionOf(node.getApprovalType()));
        vo.setApprovalValues(parseValues(node.getApprovalValues()));
        vo.setApprovalValueText(valueText(node, names));
        vo.setApprovalMode(node.getApprovalMode());
        vo.setApprovalModeText(ApprovalModeEnum.descriptionOf(node.getApprovalMode()));
        vo.setUpdateTime(node.getUpdateTime());
        return vo;
    }

    /** 审批对象的可读文案；对象已被删除时保留 {@code #id}，而不是留一片空白让人以为没配。 */
    private String valueText(WorkflowNode node, ValueNames names) {
        List<Integer> values = parseValues(node.getApprovalValues());
        if (values.isEmpty() || node.getApprovalType() == null) {
            return null;
        }
        Map<Integer, String> source = node.getApprovalType() == ApprovalTypeEnum.ROLE.getValue()
                ? names.roles()
                : names.users();
        return values.stream()
                .map(valueId -> source.getOrDefault(valueId, "#" + valueId))
                .collect(Collectors.joining("、"));
    }

    /** 逗号分隔的ID串 → ID 列表；非数字段跳过并记 warn（历史脏数据不该让整个页面查不出来）。 */
    private static List<Integer> parseValues(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (String part : raw.split(VALUE_SEPARATOR)) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                result.add(Integer.valueOf(text));
            } catch (NumberFormatException e) {
                log.warn("[WORKFLOW] 审批配置值存在非法ID，已跳过 -> raw={}, part={}", raw, text);
            }
        }
        return result;
    }

    private static int countApproval(List<WorkflowNode> nodes) {
        return (int) nodes.stream().filter(node -> WorkflowNodeTypeEnum.isApproval(node.getType())).count();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 审批对象的展示名映射（指定人员看用户名，指定角色看角色名）。 */
    private record ValueNames(Map<Integer, String> users, Map<Integer, String> roles) {
    }

}
