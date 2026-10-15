package com.gangtise.backend.domain.aggregate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("workflow_node")
public class WorkflowInstanceActivity implements Serializable {

    private static final long serialVersionUID = 1L;
    //自增id
    private Long id;
    //Workflow 关联id
    private Integer workflowId;
    //WorkflowNode 关联id
    private Integer nodeId;
    //workflowNode 快照数据
    private String nodeName;
    //状态 0未开始 1待处理 2已通过 3已转交 9驳回
    private Integer status;
    //操作人
    private Integer operatorUserId;
    //操作时间
    @TableField(value = "operator_time")
    private LocalDateTime operatorTime;

    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
