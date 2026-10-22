package com.management.software.application.dto;

/**
 * SM2 公钥下发结果。
 *
 * @param keyId     本次密钥标识，登录时需原样带回，服务端据此取出私钥
 * @param publicKey SM2 公钥，130 位十六进制（带 04 前缀），与前端 sm-crypto 对应
 */
public record Sm2PublicKeyDTO(String keyId, String publicKey) {
}
