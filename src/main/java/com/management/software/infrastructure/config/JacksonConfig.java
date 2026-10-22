package com.management.software.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * 接口层 Jackson 序列化配置（DTO 出入参统一走 Jackson）。
 *
 * <p>要点：
 * <ul>
 *   <li>时间统一格式化为 {@code yyyy-MM-dd HH:mm:ss}，时区 GMT+8；</li>
 *   <li>Long/long 序列化为字符串，避免雪花 ID 在前端 JS 侧丢失精度；</li>
 *   <li>反序列化忽略未知字段，提升接口向后兼容性。</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String TIME_ZONE = "GMT+8";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.timeZone(TimeZone.getTimeZone(TIME_ZONE));

            // 时间类型
            builder.serializers(
                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)),
                    new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
            builder.deserializers(
                    new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)),
                    new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));

            // 大整数转字符串，避免前端精度丢失
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);

            builder.featuresToDisable(
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                    SerializationFeature.FAIL_ON_EMPTY_BEANS,
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        };
    }

}
