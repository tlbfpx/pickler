package com.heypickler.service.impl;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.service.ImageUrlValidator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * 图片外链校验：HEAD 探活 + Content-Type=image/*。
 * <p>
 * SSRF 防护：仅 http/https、解析主机拒绝私网/回环/链路本地/CGNAT、不跟随重定向、
 * 错误信息不回显内部状态/类型（避免被当端口扫描/IMDS 探测反馈）。
 * <p>
 * {@code allowPrivateHosts} 默认 false（生产严格）；个别内部 CDN 场景或单测需对回环地址
 * 测试 HTTP 逻辑时传 true 跳过主机校验（HTTP 探活逻辑仍走）。
 */
@Component
public class HeadBasedImageUrlValidator implements ImageUrlValidator {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final boolean allowPrivateHosts;

    public HeadBasedImageUrlValidator() {
        this(false);
    }

    /** 测试/内部 CDN 钩子：true 时跳过私网主机校验（HTTP 探活逻辑仍执行）。生产用无参（严格）。 */
    public HeadBasedImageUrlValidator(boolean allowPrivateHosts) {
        this.allowPrivateHosts = allowPrivateHosts;
    }

    @Override
    public void validate(String url) {
        URL parsed;
        try {
            parsed = new URL(url);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址格式非法");
        }
        // 仅 http/https（拒 file/ftp/gopher 等伪协议）
        if (!ALLOWED_SCHEMES.contains(parsed.getProtocol().toLowerCase())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址仅支持 http/https");
        }
        if (!allowPrivateHosts) {
            assertPublicHost(parsed.getHost());
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) parsed.openConnection();
            // 不跟随重定向：重定向 Location 可指向内网，绕过上面的主机校验
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("HEAD");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new BizException(ErrorCode.PARAM_ERROR, "图片地址不可访问");
            }
            String contentType = conn.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new BizException(ErrorCode.PARAM_ERROR, "图片地址返回非图片内容");
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            // 不回显 e.getMessage()（可能含内网拓扑/端口信息）
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址校验失败");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 主机解析到的全部 IP 必须为公网地址，否则拒（SSRF 防护）。 */
    private void assertPublicHost(String host) {
        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址主机无法解析");
        }
        for (InetAddress addr : addrs) {
            if (isPrivateOrInternal(addr)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "图片地址主机不允许指向内网");
            }
        }
    }

    private boolean isPrivateOrInternal(InetAddress addr) {
        return addr.isAnyLocalAddress()      // 0.0.0.0
                || addr.isLoopbackAddress()   // 127.0.0.0/8, ::1
                || addr.isLinkLocalAddress()  // 169.254.0.0/16（含 AWS IMDS）
                || addr.isSiteLocalAddress()  // 10/8, 172.16/12, 192.168/16
                || addr.isMulticastAddress()  // 224.0.0.0/4
                || isCarrierGradeNat(addr);   // 100.64.0.0/10
    }

    private boolean isCarrierGradeNat(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length != 4) return false; // 仅 IPv4
        return (b[0] & 0xff) == 100 && (b[1] & 0xff) >= 64 && (b[1] & 0xff) <= 127;
    }
}
