package com.management.software.application.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.List;

/**
 * 统一分页返回结构。
 *
 * <p><b>为什么三个计数要单独指定序列化器</b>：全局策略把 {@code Long}/{@code long} 都序列化成字符串
 * （见 {@code JacksonConfig}，目的是避免雪花 ID 在前端 JS 侧丢精度），而分页计数恰好是原生 {@code long}，
 * 于是接口下发的是 {@code "total":"20"}、{@code "current":"1"} 而不是数字。前端拿到字符串后，
 * {@code state.current + 1} 走的是<b>字符串拼接</b>，得到 {@code "11"} —— 点一次「下一页」直接跳到第 11 页。
 *
 * <p>计数与 ID 的量级差着十几个数量级：行数、页码离 {@code 2^53} 远得看不见，不存在精度问题。
 * 所以这里把三个计数显式还原成 JSON 数字，真正的 ID 仍旧按字符串下发。
 * 这是「ID 防精度丢失」策略的边界，不要把它推广到其他 Long 字段上。
 *
 * @param total   总条数
 * @param current 当前页码（从 1 开始）
 * @param size    每页条数
 * @param records 当前页数据
 * @param <T>     记录类型
 */
public record PageResult<T>(
        @JsonSerialize(using = PageResult.CountAsNumber.class) long total,
        @JsonSerialize(using = PageResult.CountAsNumber.class) long current,
        @JsonSerialize(using = PageResult.CountAsNumber.class) long size,
        List<T> records) {

    public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
        return new PageResult<>(total, current, size, records);
    }

    public static <T> PageResult<T> empty(long current, long size) {
        return new PageResult<>(0L, current, size, List.of());
    }

    /**
     * 把计数值按 JSON 数字写出，绕开全局的 Long -> 字符串策略。
     *
     * <p>用 {@code StdSerializer<Object>} 承接是刻意的：属性声明类型是原生 {@code long}，
     * 与框架自带的 {@code ToStringSerializer} 同一种写法，避免装箱类型与原始类型在解析序列化器时对不上。
     */
    public static final class CountAsNumber extends StdSerializer<Object> {

        public CountAsNumber() {
            super(Object.class);
        }

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            if (value instanceof Number number) {
                generator.writeNumber(number.longValue());
            } else {
                provider.defaultSerializeNull(generator);
            }
        }

    }

}
