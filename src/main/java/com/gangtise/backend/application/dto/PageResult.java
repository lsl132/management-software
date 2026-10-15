package com.gangtise.backend.application.dto;

import java.util.List;

/**
 * 统一分页返回结构。
 *
 * @param total   总条数
 * @param current 当前页码（从 1 开始）
 * @param size    每页条数
 * @param records 当前页数据
 * @param <T>     记录类型
 */
public record PageResult<T>(long total, long current, long size, List<T> records) {

    public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
        return new PageResult<>(total, current, size, records);
    }

    public static <T> PageResult<T> empty(long current, long size) {
        return new PageResult<>(0L, current, size, List.of());
    }

}
