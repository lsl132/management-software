package com.management.software.domain.aggregate;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
/**
 * 工单
 * @author gangtise
 * @since 2026-06-05
 */
@Data
@TableName("work_order")
public class WorkOrder implements Serializable {

    private static final long serialVersionUID = 1L;
    //自增数据
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    //发起人
    private Integer createUserId;
    //流程id
    private Integer workflowId;
    //模版数据 JSON
    private String templateData;
    //当前节点ID
    private Integer createNodeId;
    //起始时间
    @TableField(value = "start_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime startTime;
    //截止时间
    @TableField(value = "end_time", fill = FieldFill.INSERT)
    private LocalDateTime endTime;
    //状态 0审批中 1已通过 2拒绝 3撤销
    private Integer status;
    //更改时间
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;



}
