package com.yunpu.yunpumusic.credentials;

import com.yunpu.yunpumusic.YunpuMusic;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 服务端凭据的对称加密工具。
 *
 * <p>密钥由世界种子 + 存档内随机盐经 PBKDF2-HMAC-SHA256 派生，
 * 密文采用 AES-256-GCM，并把随机 IV 前置到密文里。
 * 这里的目标不是抵御拿到整台服务器控制权的攻击者，而是避免凭据以明文形式散落在存档目录中。
 */
final class CredentialCipher {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int SALT_LENGTH = 16;
    private static final int PBKDF2_ITERATIONS = 65_536;
    private static final int KEY_LENGTH = 256;

    private CredentialCipher() {
    }

    @Nullable
    static String encrypt(String plain, MinecraftServer server, byte[] salt) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(server, salt), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            YunpuMusic.LOGGER.error("加密服务端凭据失败", e);
            return null;
        }
    }

    @Nullable
    static String decrypt(String encoded, MinecraftServer server, byte[] salt) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= IV_LENGTH) {
                return null;
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(server, salt), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            YunpuMusic.LOGGER.warn("解密服务端凭据失败（世界种子或盐值可能已变化）", e);
            return null;
        }
    }

    private static SecretKeySpec key(MinecraftServer server, byte[] salt) throws Exception {
        byte[] seed = Long.toString(server.getWorldData().worldGenOptions().seed())
                .getBytes(StandardCharsets.UTF_8);
        byte[] effectiveSalt = salt.length >= SALT_LENGTH ? salt : padSalt(salt);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(
                new String(seed, StandardCharsets.UTF_8).toCharArray(), effectiveSalt, PBKDF2_ITERATIONS, KEY_LENGTH);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static byte[] padSalt(byte[] salt) {
        byte[] padded = new byte[SALT_LENGTH];
        System.arraycopy(salt, 0, padded, 0, Math.min(salt.length, SALT_LENGTH));
        return padded;
    }
}
