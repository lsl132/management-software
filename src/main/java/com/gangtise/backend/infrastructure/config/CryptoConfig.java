package com.gangtise.backend.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码散列配置。
 *
 * <p>只引入 spring-security-crypto 提供 {@link BCryptPasswordEncoder}，
 * 不引入完整 Spring Security，避免其默认安全链接管所有请求。
 */
@Configuration
public class CryptoConfig {

    /** BCrypt 强度，10 为默认值（约 2^10 次迭代）。 */
    private static final int BCRYPT_STRENGTH = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

}
