package com.gangtise.backend.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工列表/详情返回结构。
 *
 * <p>字段值中的状态、学历等原始码值保留，同时附带 {@code xxxText} 便于前端直接展示；
 * 关联字段附带名称，避免前端为展示再做一次查询。
 */
@Data
public class EmployeeVO {

    private Integer id;
    private String workNo;
    private String name;
    private String alias;

    private Integer gender;
    private String genderText;

    private String identityCard;
    private String telephone;
    private String email;
    private String address;

    private Integer status;
    private String statusText;

    /** 生日 yyyyMMdd。 */
    private Integer birthday;

    private Integer education;
    private String educationText;

    private Integer rank;
    private String rankName;

    private Integer department;
    private String departmentName;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
