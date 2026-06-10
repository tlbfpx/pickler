package com.heypickler.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Decrypts WeChat mini-program encrypted data (phone number, user info)
 * using AES/CBC/PKCS5Padding with session_key and iv.
 *
 * @see <a href="https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/signature.html">WeChat signature and encryption</a>
 */
@Component
public class WxBizDataCrypt {

    @org.springframework.beans.factory.annotation.Value("${hey-pickler.wechat.appid}")
    private String appId;

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Decrypts WeChat encrypted data and extracts the phone number.
     *
     * @param sessionKey   Base64-encoded session key from wx.login
     * @param encryptedData Base64-encoded encrypted data
     * @param iv           Base64-encoded initial vector
     * @return decrypted phone number
     */
    public String decryptPhoneNumber(String sessionKey, String encryptedData, String iv) {
        try {
            byte[] sessionKeyBytes = java.util.Base64.getDecoder().decode(sessionKey);
            byte[] encryptedBytes = java.util.Base64.getDecoder().decode(encryptedData);
            byte[] ivBytes = java.util.Base64.getDecoder().decode(iv);

            SecretKeySpec keySpec = new SecretKeySpec(sessionKeyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(encryptedBytes);

            String json = new String(decrypted, StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(json);

            JsonNode watermark = node.get("watermark");
            if (watermark != null) {
                String watermarkAppId = watermark.has("appid") ? watermark.get("appid").asText() : "";
                if (!appId.equals(watermarkAppId)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "数据校验失败");
                }
            }

            JsonNode phoneNode = node.get("phoneNumber");
            if (phoneNode == null || phoneNode.asText().isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "无法获取手机号");
            }
            return phoneNode.asText();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号解密失败，请重新登录");
        }
    }
}
