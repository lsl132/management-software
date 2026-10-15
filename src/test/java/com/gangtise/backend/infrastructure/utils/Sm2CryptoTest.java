package com.gangtise.backend.infrastructure.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SM2 工具类单测，关键点是「与前端 sm-crypto 的格式互通」。
 */
class Sm2CryptoTest {

    /**
     * 由前端 sm-crypto 0.5.7 真实生成的测试向量：
     * <pre>
     * const sm2 = require('sm-crypto').sm2
     * const kp = sm2.generateKeyPairHex()
     * sm2.doEncrypt('admin123', kp.publicKey, 1)  // cipherMode = 1 即 C1C3C2
     * </pre>
     * 后端必须能解开它，否则前端加密后必然登录失败。
     */
    private static final String SM_CRYPTO_PRIVATE_KEY =
            "c1cef63336ee661c02199525e51fa2b0237c8d9178c9d5457d9f2b020cb3e7b8";

    private static final String SM_CRYPTO_CIPHER =
            "1f297dfe2f04db94cfad3bbaf9ba2538eca73c9405911aa7e42794097029361a"
                    + "1178c016648389ad763c1fb373927b482351cc87acf956d64f4151d10cbe1fbd"
                    + "fce8c43eb2d44e759532976a422af1a0682ae686624c68c677e2065fc31040"
                    + "0188ca92200c60a9f0";

    @Test
    @DisplayName("能解开前端 sm-crypto 生成的密文")
    void decryptSmCryptoCipher() {
        assertEquals("admin123", Sm2Utils.decrypt(SM_CRYPTO_CIPHER, SM_CRYPTO_PRIVATE_KEY));
    }

    @Test
    @DisplayName("密钥格式符合约定：公钥 130 位带 04 前缀，私钥 64 位")
    void keyPairFormat() {
        Sm2Utils.Sm2KeyPair keyPair = Sm2Utils.generateKeyPair();

        assertEquals(130, keyPair.publicKey().length());
        assertTrue(keyPair.publicKey().startsWith("04"));
        assertEquals(64, keyPair.privateKey().length());
        assertTrue(Sm2Utils.isValidPublicKey(keyPair.publicKey()));
    }

    @Test
    @DisplayName("加密结果不含 04 前缀，且可自解回明文（含中文）")
    void encryptThenDecrypt() {
        Sm2Utils.Sm2KeyPair keyPair = Sm2Utils.generateKeyPair();
        String plainText = "P@ssw0rd-国密加解密";

        String cipherHex = Sm2Utils.encrypt(plainText, keyPair.publicKey());

        // 密文总长 = C1(64) + C3(32) + C2(明文长度)，不含 04 前缀
        assertEquals((96 + plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length) * 2,
                cipherHex.length());
        assertEquals(plainText, Sm2Utils.decrypt(cipherHex, keyPair.privateKey()));
    }

    @Test
    @DisplayName("换一对密钥解不开，密文被篡改也解不开")
    void decryptRejectsWrongKeyAndTamperedCipher() {
        Sm2Utils.Sm2KeyPair keyPair = Sm2Utils.generateKeyPair();
        Sm2Utils.Sm2KeyPair other = Sm2Utils.generateKeyPair();
        String cipherHex = Sm2Utils.encrypt("admin123", keyPair.publicKey());

        assertThrows(IllegalArgumentException.class, () -> Sm2Utils.decrypt(cipherHex, other.privateKey()));

        String tampered = "ff" + cipherHex.substring(2);
        assertThrows(IllegalArgumentException.class, () -> Sm2Utils.decrypt(tampered, keyPair.privateKey()));
    }

    @Test
    @DisplayName("非法密文给出明确异常")
    void decryptRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Sm2Utils.decrypt("", SM_CRYPTO_PRIVATE_KEY));
        assertThrows(IllegalArgumentException.class, () -> Sm2Utils.decrypt("zzzz", SM_CRYPTO_PRIVATE_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> Sm2Utils.decrypt("abcd", SM_CRYPTO_PRIVATE_KEY));
    }

}
