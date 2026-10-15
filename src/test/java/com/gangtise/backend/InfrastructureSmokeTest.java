package com.gangtise.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class InfrastructureSmokeTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void datasourceWorks() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String version = jdbcTemplate.queryForObject("select version()", String.class);
        System.out.println("[SMOKE] datasource -> " + dataSource.getClass().getName());
        System.out.println("[SMOKE] postgres   -> " + version);
    }

    @Test
    void redisWorks() {
        String key = "backend:smoke:test";
        redisTemplate.opsForValue().set(key, "ok", Duration.ofMinutes(1));
        Object value = redisTemplate.opsForValue().get(key);
        System.out.println("[SMOKE] redis      -> " + value);
        assertEquals("ok", value);
        redisTemplate.delete(key);
    }

}
