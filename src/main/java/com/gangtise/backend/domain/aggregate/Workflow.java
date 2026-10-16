package com.gangtise.backend.domain.aggregate;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程
 */
@Data
@TableName("workflow")
public class Workflow  implements Serializable {

    private static final long serialVersionUID = 1L;

    //自增数据id
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    //图标
    private String icon;
    //名称
    private String workflowName;
    //描述说明
    private String description;
    //模板 内容是JSON
    private String template;
    //状态 1启用 2禁用
    private Integer status;

    //更改时间
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;


}
