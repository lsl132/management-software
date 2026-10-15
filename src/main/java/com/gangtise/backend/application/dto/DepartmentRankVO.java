package com.gangtise.backend.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门职级列表/详情返回结构。
 */
@Data
public class DepartmentRankVO {

    private Integer id;
    private String code;

    private Integer type;
    /** 序列描述，如「O 一线操作工种」，见 RankTypeEnum。 */
    private String typeText;

    /** 归属部门ID，关联 department.id。 */
    private Integer department;
    private String departmentName;

    private String intro;

    private LocalDateTime createTime;

}
