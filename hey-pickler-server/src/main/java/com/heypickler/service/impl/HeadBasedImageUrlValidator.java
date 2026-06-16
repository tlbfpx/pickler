package com.heypickler.service.impl;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.service.ImageUrlValidator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class HeadBasedImageUrlValidator implements ImageUrlValidator {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;

    @Override
    public void validate(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("HEAD");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                    "图片地址不可访问（HTTP " + code + "）");
            }
            String contentType = conn.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                    "图片地址返回非图片内容: " + (contentType == null ? "未知" : contentType));
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                "图片地址校验失败: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
