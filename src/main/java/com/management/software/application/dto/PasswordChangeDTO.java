package com.management.software.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改密码入参：只有一次性公钥标识与密文，**请求体里不出现任何明文密码**。
 *
 * <p>为什么是一个 {@code cipher} 而不是「原密码密文 + 新密码密文」两个：SM2 密钥是一次一密的，
 * 同一个 {@code keyId} 解密一次即销毁（见 {@code CryptoService#decryptOnce}）。
 * 两段密码只能合在一起用同一把公钥加密，服务端解密后再拆开——
 * 否则要么申请两把公钥（两次往返），要么第二次解密必然失败。
 */
@Data
public class PasswordChangeDTO {

    /** 一次性公钥标识（Redis 里的私钥键）。 */
    @NotBlank(message = "不能为空")
    private String keyId;

    /** SM2 密文，明文是 {@code {"oldPassword":"...","newPassword":"..."}}。 */
    @NotBlank(message = "不能为空")
    private String cipher;

}
