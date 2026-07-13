package com.codeforge.ai.infrastructure.security;

import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class CredentialEncryptionService {

    public static final String MASTER_KEY_ENV = "CODEFORGE_CREDENTIAL_MASTER_KEY";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public boolean isMasterKeyConfigured() {
        return resolveMasterKeyBytes() != null;
    }

    public EncryptedPayload encrypt(String plaintext) {
        byte[] key = requireMasterKeyBytes();
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedPayload(ciphertext, iv, 1);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "凭据加密失败");
        }
    }

    public String decrypt(byte[] ciphertext, byte[] nonce, int keyVersion) {
        byte[] key = requireMasterKeyBytes();
        if (keyVersion != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的凭据密钥版本");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "凭据解密失败");
        }
    }

    public void requireMasterKeyForEncryptedStorage() {
        if (!isMasterKeyConfigured()) {
            throw new BusinessException(ErrorCode.CREDENTIAL_ENCRYPTION_UNAVAILABLE);
        }
    }

    private byte[] requireMasterKeyBytes() {
        byte[] key = resolveMasterKeyBytes();
        if (key == null) {
            throw new BusinessException(ErrorCode.CREDENTIAL_ENCRYPTION_UNAVAILABLE);
        }
        return key;
    }

    private byte[] resolveMasterKeyBytes() {
        String raw = System.getenv(MASTER_KEY_ENV);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            if (decoded.length == KEY_LENGTH_BYTES) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        byte[] utf8 = trimmed.getBytes(StandardCharsets.UTF_8);
        if (utf8.length == KEY_LENGTH_BYTES) {
            return utf8;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(utf8);
        } catch (NoSuchAlgorithmException exception) {
            return null;
        }
    }

    public record EncryptedPayload(byte[] ciphertext, byte[] nonce, int keyVersion) {
    }
}
