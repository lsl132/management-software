package com.gangtise.backend.infrastructure.enums;

import lombok.Getter;

import java.util.Arrays;

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

    /**
     * 码值是否受支持。
     *
     * <p>币种用白名单而不是格式校验：{@code currency} 列是 varchar，格式校验放得过 {@code "ABC"}，
     * 而「支持的币种」本来就是一份封闭清单——将来加币种只改这个枚举。
     */
    public static boolean isSupported(String value) {
        return value != null && Arrays.stream(values()).anyMatch(item -> item.value.equals(value.trim()));
    }

    /** 码值转中文描述，未匹配时原样返回码值。 */
    public static String descriptionOf(String value) {
        if (value == null) {
            return null;
        }
        String code = value.trim();
        return Arrays.stream(values())
                .filter(item -> item.value.equals(code))
                .map(CurrencyEnum::getDescription)
                .findFirst()
                .orElse(code);
    }

}
