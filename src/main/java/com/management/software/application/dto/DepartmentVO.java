package com.management.software.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门列表/详情返回结构。
 *
 * <p>{@code rankCount} / {@code employeeCount} 为归属统计，供页面展示并作为删除前置提示。
 */
@Data
public class DepartmentVO {

    private Integer id;

    private Integer parentId;
    /** 上级部门名称，顶级部门为 null。 */
    private String parentName;

    private String departName;
    private String description;

    /** 该部门下的职级数量。 */
    private Integer rankCount;
    /** 该部门下的员工数量。 */
    private Integer employeeCount;

    private LocalDateTime createTime;

}
