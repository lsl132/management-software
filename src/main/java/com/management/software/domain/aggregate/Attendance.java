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
 * 考勤统计
 */
@Data
@TableName("attendance")
public class Attendance implements Serializable {

    private static final long serialVersionUID = 1L;

    //自增id
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    //员工工号
    private String workNo;
    //日期yyyyMMdd
    private Integer day;
    //最早打卡时间
    private Long first;
    //最晚打卡时间
    private Long last;
    //加班时间
    private Double extra;
    //状态
    private Integer status;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
