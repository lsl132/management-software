package com.management.software.infrastructure.utils;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * 国密 SM2 加解密工具（BouncyCastle 实现）。
 *
 * <p><b>与前端 sm-crypto 的格式约定（重要）</b>
 * <ul>
 *   <li>公钥：130 位十六进制，带非压缩前缀 {@code 04}（sm-crypto 的 {@code doEncrypt} 只接受这种格式）；</li>
 *   <li>私钥：64 位十六进制（32 字节 D 值，左侧补零）；</li>
 *   <li>密文：{@code C1 || C3 || C2} 顺序（即 C1C3C2 模式），
 *       且 <b>C1 不含 {@code 04} 前缀</b>，与 {@code sm-crypto.sm2.doEncrypt(msg, publicKey, 1)} 的输出完全一致。</li>
 * </ul>
 * 因此本工具的解密方法会对入参密文统一补回 {@code 04} 前缀后再交给 BouncyCastle 处理，
 * 不允许直接传入已带前缀的密文，避免出现歧义。
 */
public final class Sm2Utils {

    /** SM2 推荐曲线（sm2p256v1）。 */
    private static final X9ECParameters X9 = GMNamedCurves.getByName("sm2p256v1");

    private static final ECDomainParameters DOMAIN =
            new ECDomainParameters(X9.getCurve(), X9.getG(), X9.getN(), X9.getH());

    /** 坐标字节长度，SM2 固定为 32。 */
    private static final int FIELD_SIZE = (DOMAIN.getCurve().getFieldSize() + 7) / 8;

    /** 无前缀密文的最小长度：C1(64) + C3(32) + 至少 1 字节 C2。 */
    private static final int MIN_CIPHER_BYTES = 2 * FIELD_SIZE + 32 + 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Sm2Utils() {
    }

    /**
     * SM2 密钥对（均为十六进制字符串）。
     *
     * @param privateKey 私钥，64 位十六进制
     * @param publicKey  公钥，130 位十六进制，带 04 前缀
     */
    public record Sm2KeyPair(String privateKey, String publicKey) {
    }

    /**
     * 生成一对 SM2 密钥。
     */
    public static Sm2KeyPair generateKeyPair() {
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(DOMAIN, RANDOM));
        AsymmetricCipherKeyPair pair = generator.generateKeyPair();

        ECPrivateKeyParameters privateKey = (ECPrivateKeyParameters) pair.getPrivate();
        ECPublicKeyParameters publicKey = (ECPublicKeyParameters) pair.getPublic();

        return new Sm2KeyPair(toFixedHex(privateKey.getD()), encodePublicKey(publicKey.getQ()));
    }

    /**
     * 公钥加密，输出与 sm-crypto 一致的「无 04 前缀 + C1C3C2」密文，主要供服务端自测与联调使用。
     *
     * @param plainText    明文
     * @param publicKeyHex 公钥（130 位，带 04 前缀）
     */
    public static String encrypt(String plainText, String publicKeyHex) {
        byte[] plain = plainText.getBytes(StandardCharsets.UTF_8);
        ECPublicKeyParameters publicKey = new ECPublicKeyParameters(decodePoint(publicKeyHex), DOMAIN);

        SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
        engine.init(true, new ParametersWithRandom(publicKey, RANDOM));

        byte[] cipher = processBlock(engine, plain);
        // BouncyCastle 的输出带 04 前缀（比明文多 97 字节），统一去掉以对齐 sm-crypto
        if (cipher.length == 1 + 2 * FIELD_SIZE + 32 + plain.length && (cipher[0] & 0xFF) == 0x04) {
            byte[] stripped = new byte[cipher.length - 1];
            System.arraycopy(cipher, 1, stripped, 0, stripped.length);
            cipher = stripped;
        }
        return Hex.toHexString(cipher);
    }

    /**
     * 私钥解密，入参为 sm-crypto 格式（无 04 前缀）的密文。
     *
     * @param cipherHex     密文十六进制
     * @param privateKeyHex 私钥，64 位十六进制
     * @return 明文字符串（UTF-8）
     */
    public static String decrypt(String cipherHex, String privateKeyHex) {
        if (cipherHex == null || cipherHex.isEmpty()) {
            throw new IllegalArgumentException("密文不能为空");
        }
        byte[] cipher;
        try {
            cipher = Hex.decode(cipherHex);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("密文不是合法的十六进制字符串");
        }
        if (cipher.length < MIN_CIPHER_BYTES) {
            throw new IllegalArgumentException("密文长度不合法");
        }

        // 补齐 04 前缀，BouncyCastle 要求 C1 为非压缩点编码（65 字节）
        byte[] withPrefix = new byte[cipher.length + 1];
        withPrefix[0] = 0x04;
        System.arraycopy(cipher, 0, withPrefix, 1, cipher.length);

        ECPrivateKeyParameters privateKey =
                new ECPrivateKeyParameters(new BigInteger(1, Hex.decode(privateKeyHex)), DOMAIN);
        SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
        engine.init(false, privateKey);

        return new String(processBlock(engine, withPrefix), StandardCharsets.UTF_8);
    }

    /**
     * 校验公钥是否为合法的非压缩 SM2 公钥。
     */
    public static boolean isValidPublicKey(String publicKeyHex) {
        try {
            decodePoint(publicKeyHex);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] processBlock(SM2Engine engine, byte[] input) {
        try {
            return engine.processBlock(input, 0, input.length);
        } catch (InvalidCipherTextException e) {
            // 解密失败多因密文被篡改、密钥不匹配或明文格式不符，向上抛更明确的语义
            throw new IllegalArgumentException("SM2 加解密失败：" + e.getMessage(), e);
        }
    }

    private static ECPoint decodePoint(String publicKeyHex) {
        if (publicKeyHex == null || publicKeyHex.isEmpty()) {
            throw new IllegalArgumentException("公钥不能为空");
        }
        return DOMAIN.getCurve().decodePoint(Hex.decode(publicKeyHex));
    }

    private static String encodePublicKey(ECPoint point) {
        return Hex.toHexString(point.getEncoded(false));
    }

    /** D 值固定输出 64 位十六进制，避免前端/其他实现解析时丢前导零。 */
    private static String toFixedHex(BigInteger value) {
        String hex = value.toString(16);
        if (hex.length() >= 2 * FIELD_SIZE) {
            return hex;
        }
        return "0".repeat(2 * FIELD_SIZE - hex.length()) + hex;
    }

}
