package com.gangtise.backend.domain.aggregate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户日程（预约 + 提醒）
 *
 * <p>{@code remindTime} / {@code intro} 用 {@code updateStrategy = ALWAYS}：
 * 编辑时传 null 即「取消提醒」「清空简介」。
 */
@Data
@TableName("user_schedule")
public class UserSchedule implements Serializable {

    private static final long serialVersionUID = 1L;

    //自增id
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    //用户ID
    private Integer userId;
    //预约时间
    private LocalDateTime scheduleTime;
    //提醒时间（可不填，为空表示不提醒）
    @TableField(value = "remind_time", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime remindTime;
    //标题
    private String title;
    //简介
    @TableField(value = "intro", updateStrategy = FieldStrategy.ALWAYS)
    private String intro;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}

