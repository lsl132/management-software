package com.gangtise.backend.application.service;

import com.gangtise.backend.application.dto.Sm2PublicKeyDTO;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.utils.Sm2Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 登录传输加密服务：下发一次性 SM2 密钥对，并解密密文。
 *
 * <p>私钥不落盘、不硬编码，而是按 {@code keyId} 暂存到 Redis：
 * <ul>
 *   <li>有效期默认 5 分钟（{@code app.security.sm2.key-ttl}），过期后无法再用于解密；</li>
 *   <li>解密即删除，同一 keyId 只能用一次，防止密文重放；</li>
 *   <li>私钥存 Redis 而非本地缓存，多实例部署时任意节点都能解密。</li>
 * </ul>
 */
@Service
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);

    private static final String KEY_PREFIX = "backend:crypto:sm2:";

    private final StringRedisTemplate stringRedisTemplate;
    private final Duration keyTtl;

    public CryptoService(StringRedisTemplate stringRedisTemplate,
                         @Value("${app.security.sm2.key-ttl:5m}") Duration keyTtl) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyTtl = keyTtl;
    }

    /**
     * 生成一次性 SM2 密钥对，私钥暂存 Redis，公钥返回给前端。
     */
    public Sm2PublicKeyDTO issueKeyPair() {
        Sm2Utils.Sm2KeyPair keyPair = Sm2Utils.generateKeyPair();
        String keyId = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + keyId, keyPair.privateKey(), keyTtl);
        return new Sm2PublicKeyDTO(keyId, keyPair.publicKey());
    }

    /**
     * 取出私钥解密并立即销毁该密钥。
     *
     * @param keyId     公钥标识
     * @param cipherHex SM2 密文（sm-crypto 输出格式）
     * @return 明文
     */
    public String decryptOnce(String keyId, String cipherHex) {
        String cacheKey = KEY_PREFIX + keyId;
        String privateKey = stringRedisTemplate.opsForValue().get(cacheKey);
        if (privateKey == null) {
            throw new BizException("加密凭证已失效，请刷新页面后重试");
        }
        // 先销毁再用，确保同一密钥不会因为解密失败而被反复尝试
        stringRedisTemplate.delete(cacheKey);
        try {
            return Sm2Utils.decrypt(cipherHex, privateKey);
        } catch (RuntimeException e) {
            log.warn("[CRYPTO] SM2 解密失败 -> keyId={}, reason={}", keyId, e.getMessage());
            throw new BizException("登录凭证解析失败，请刷新页面后重试");
        }
    }

}
