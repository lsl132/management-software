package com.management.software.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 下拉框选项。
 *
 * <p>使用普通类而非 record：模板（Thymeleaf/SpEL）只识别 {@code getXxx()} 形式的属性访问器。
 */
@Data
@AllArgsConstructor
public class SelectOption {

    private String value;
    private String label;

    public static SelectOption of(Object value, String label) {
        return new SelectOption(value == null ? null : String.valueOf(value), label);
    }

}
