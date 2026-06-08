package com.heypickler.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class AesUtil {

    @Value("${hey-pickler.aes.key}")
    private String key;

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    public String encrypt(String plainText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    key.substring(0, 16).getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    public String decrypt(String cipherText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    key.substring(0, 16).getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed", e);
        }
    }
}
