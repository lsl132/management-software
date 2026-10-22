package com.management.software.infrastructure.enums;

import lombok.Getter;

@Getter
public enum SettlementSalaryStatusEnum {

    INITIAL(0,"初始创建"),
    REVIEW(1,"审核通过"),
    PAYMENT(2,"已支付"),
    CANCEL(9,"已撤销");

    private Integer status;
    private String description;

    SettlementSalaryStatusEnum(Integer status, String description) {
        this.status = status;
        this.description = description;
    }
}
