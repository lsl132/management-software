package com.gangtise.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求。
 *
 * <p>密码字段不传明文：前端用服务端下发的 SM2 公钥加密后，以 {@code cipher} 提交。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "不能为空")
    private String username;

    /** 公钥标识（取自 /api/auth/public-key）。 */
    @NotBlank(message = "不能为空")
    private String keyId;

    /** SM2 密文，C1C3C2 模式、C1 不含 04 前缀（sm-crypto 输出格式）。 */
    @NotBlank(message = "不能为空")
    private String cipher;

}
