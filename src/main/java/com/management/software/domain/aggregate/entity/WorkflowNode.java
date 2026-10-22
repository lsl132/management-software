package com.management.software.domain.aggregate.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程节点
 */
@Data
@TableName("workflow_node")
public class WorkflowNode implements Serializable {

    private static final long serialVersionUID = 1L;

    //自增数据id
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    //流程关联ID
    @TableField(value = "workflow_id", updateStrategy = FieldStrategy.ALWAYS)
    private Integer workflowId;
    //节点排序
    private Integer sort;
    //节点名称
    @TableField(value = "node_name", updateStrategy = FieldStrategy.ALWAYS)
    private String nodeName;
    //节点类型 0起始节点，1审批节点，2结束节点
    private Integer type;
    //审批类型1指定人员，2直属上级3指定角色
    @TableField(value = "approval_type", updateStrategy = FieldStrategy.ALWAYS)
    private Integer approvalType;
    //审批配置值：用户id（多个用逗号分隔）,角色id
    @TableField(value = "approval_values", updateStrategy = FieldStrategy.ALWAYS)
    private String approvalValues;
    //审批模式：1或签（一人通过即生效） 2会签（全员通过才生效）
    @TableField(value = "approval_mode", updateStrategy = FieldStrategy.ALWAYS)
    private Integer approvalMode;
    //更改时间
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;



}
