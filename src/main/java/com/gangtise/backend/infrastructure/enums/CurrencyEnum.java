package com.gangtise.backend.infrastructure.enums;

import lombok.Getter;

@Getter
public enum CurrencyEnum {

    CNY("CNY","人民币"),
    USD("USD","美元"),
    HKD("HKD","港币"),
    EUR("EUR","欧元"),
    GBP("GBP","英镑"),
    JPY("JPY","日元"),
    KRW("KRW","韩元"),
    SGD("SGD","新加坡元"),
    RUB("RUB","俄罗斯卢布"),
    INR("INR","印度卢比");

    private String value;
    private String description;

    CurrencyEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }


}
