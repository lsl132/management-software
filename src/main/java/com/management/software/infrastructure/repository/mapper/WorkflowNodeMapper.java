package com.management.software.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.management.software.domain.aggregate.entity.WorkflowNode;

/**
 * 流程节点 Mapper。
 *
 * <p>节点的写入由 {@code WorkflowService} 做差量同步（有 id 的改、无 id 的加、
 * 本次未提交的删），**不用「先清空再重建」**：{@code work_order.create_node_id} 指向
 * 具体节点，整表重建会让在途工单的当前节点变成悬空外键——这类错不会报错，
 * 只会体现在某张单子再也推不动。
 */
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNode> {
}
