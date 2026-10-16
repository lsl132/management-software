package com.gangtise.backend.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gangtise.backend.domain.aggregate.Workflow;

/**
 * 流程（审批流定义）Mapper。
 *
 * <p>没有自定义 SQL：流程本身的增删改查都是单表操作，唯一需要额外说明的是
 * {@code workflow_name} 上的唯一索引 {@code uk_workflow_name}——重名流程会让审批人
 * 分不清该提交哪一个，所以在库侧就拦掉，服务侧另做一次前置校验以便给出友好提示。
 *
 * <p>节点（{@code workflow_node}）见 {@link WorkflowNodeMapper}。
 */
public interface WorkflowMapper extends BaseMapper<Workflow> {
}
