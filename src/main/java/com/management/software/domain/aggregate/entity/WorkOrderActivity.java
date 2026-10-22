package com.management.software.domain.aggregate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单活动记录
 * @author gangtise
 */
@Data
@TableName("work_order_activity")
public class WorkOrderActivity implements Serializable {

    private static final long serialVersionUID = 1L;
    //自增id
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    //工单ID（关联 work_order.id，后者为 bigint，此处必须用 Long 保持类型一致）
    private Long workOrderId;
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
