package com.heypickler.common.util;

import com.heypickler.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Loop-v11 coverage sprint — moves WxBizDataCrypt from 7.2% to ~90%+.
 * Builds real AES/CBC/PKCS5Padding-encrypted payloads to exercise
 * the happy path, then drives the failure branches with bad input.
 */
class WxBizDataCryptTest {

    private WxBizDataCrypt crypt;
    private static final String APP_ID = "test-appid";

    @BeforeEach
    void setUp() {
        crypt = new WxBizDataCrypt();
        ReflectionTestUtils.setField(crypt, "appId", APP_ID);
    }

    private String[] encryptPayload(String json, String sessionKeyBase64) throws Exception {
        byte[] sessionKeyBytes = Base64.getDecoder().decode(sessionKeyBase64);
        byte[] ivBytes = new byte[16];
        new SecureRandom().nextBytes(ivBytes);

        SecretKeySpec keySpec = new SecretKeySpec(sessionKeyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));

        return new String[] {
                sessionKeyBase64,
                Base64.getEncoder().encodeToString(encrypted),
                Base64.getEncoder().encodeToString(ivBytes)
        };
    }

    private String randomSessionKey() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void decryptPhoneNumber_happyPath_returnsPhone() throws Exception {
        String json = "{\"phoneNumber\":\"13800001111\",\"watermark\":{\"appid\":\"" + APP_ID + "\",\"timestamp\":12345}}";
        String[] parts = encryptPayload(json, randomSessionKey());
        String phone = crypt.decryptPhoneNumber(parts[0], parts[1], parts[2]);
        assertEquals("13800001111", phone);
    }

    @Test
    void decryptPhoneNumber_appIdMismatch_throwsParam() throws Exception {
        String json = "{\"phoneNumber\":\"13800001111\",\"watermark\":{\"appid\":\"other-appid\",\"timestamp\":1}}";
        String[] parts = encryptPayload(json, randomSessionKey());
        assertThrows(BizException.class,
                () -> crypt.decryptPhoneNumber(parts[0], parts[1], parts[2]));
    }

    @Test
    void decryptPhoneNumber_missingPhoneNode_throwsParam() throws Exception {
        String json = "{\"watermark\":{\"appid\":\"" + APP_ID + "\"}}";
        String[] parts = encryptPayload(json, randomSessionKey());
        BizException ex = assertThrows(BizException.class,
                () -> crypt.decryptPhoneNumber(parts[0], parts[1], parts[2]));
        assertEquals("无法获取手机号", ex.getMessage());
    }

    @Test
    void decryptPhoneNumber_emptyPhoneNode_throwsParam() throws Exception {
        String json = "{\"phoneNumber\":\"\",\"watermark\":{\"appid\":\"" + APP_ID + "\"}}";
        String[] parts = encryptPayload(json, randomSessionKey());
        assertThrows(BizException.class,
                () -> crypt.decryptPhoneNumber(parts[0], parts[1], parts[2]));
    }

    @Test
    void decryptPhoneNumber_noWatermark_succeeds() throws Exception {
        String json = "{\"phoneNumber\":\"13900000000\"}";
        String[] parts = encryptPayload(json, randomSessionKey());
        assertEquals("13900000000", crypt.decryptPhoneNumber(parts[0], parts[1], parts[2]));
    }

    @Test
    void decryptPhoneNumber_wrongSessionKey_throwsParam() throws Exception {
        String json = "{\"phoneNumber\":\"13800001111\",\"watermark\":{\"appid\":\"" + APP_ID + "\"}}";
        String[] parts = encryptPayload(json, randomSessionKey());
        // Use a different key for decryption
        String wrongKey = randomSessionKey();
        BizException ex = assertThrows(BizException.class,
                () -> crypt.decryptPhoneNumber(wrongKey, parts[1], parts[2]));
        assertEquals("手机号解密失败，请重新登录", ex.getMessage());
    }

    @Test
    void decryptPhoneNumber_invalidBase64_throwsParam() {
        // Non-base64 input goes through the catch-all "decryption failed" branch
        assertThrows(BizException.class,
                () -> crypt.decryptPhoneNumber("@@@", "@@@", "@@@"));
    }

    @Test
    void decryptPhoneNumber_watermarkWithoutAppid_throwsParam() throws Exception {
        // Watermark exists but has no appid field → "" != configured appid
        String json = "{\"phoneNumber\":\"13700000000\",\"watermark\":{\"timestamp\":99}}";
        String[] parts = encryptPayload(json, randomSessionKey());
        assertThrows(BizException.class,
                () -> crypt.decryptPhoneNumber(parts[0], parts[1], parts[2]));
    }
}
