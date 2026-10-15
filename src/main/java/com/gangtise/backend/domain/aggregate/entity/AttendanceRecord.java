package com.gangtise.backend.domain.aggregate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 考勤记录
 */
@Data
@TableName("attendance_record")
public class AttendanceRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    //自增id
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    //员工工号
    private String workNo;
    //打卡位置
    private String location;
    //打卡时间
    private Long punchTime;
    //是否有效 1 有效 2无效
    private Integer status;


}
