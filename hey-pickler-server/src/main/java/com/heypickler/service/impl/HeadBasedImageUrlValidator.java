package com.heypickler.service.impl;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.service.DnsResolver;
import com.heypickler.service.ImageUrlValidator;
import org.springframework.stereotype.Component;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * 图片外链校验：HEAD 探活 + Content-Type=image/*。
 * <p>
 * SSRF 防护（含 DNS rebinding）：
 * <ul>
 *   <li>仅 http/https 协议（拒 file/ftp/gopher 等伪协议）</li>
 *   <li>解析主机后拒绝私网/回环/链路本地/CGNAT/IMDS 地址</li>
 *   <li>不跟随重定向（Location 可指向内网绕过主机校验）</li>
 *   <li><b>钉死解析 IP</b>：连接阶段强制复用校验过的 IP，消除 HttpURLConnection 的第二次 DNS 查询，
 *       防 DNS rebinding（校验阶段解析返回公网 IP 过关、连接阶段重新解析被切到内网 IP）</li>
 *   <li>错误信息不回显内部状态/类型（避免被当端口扫描/IMDS 探测反馈）</li>
 * </ul>
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
    private final DnsResolver dnsResolver;

    public HeadBasedImageUrlValidator() {
        this(false);
    }

    /** 测试/内部 CDN 钩子：true 时跳过私网主机校验（HTTP 探活逻辑仍执行）。生产用无参（严格）。 */
    public HeadBasedImageUrlValidator(boolean allowPrivateHosts) {
        this(allowPrivateHosts, DnsResolver.DEFAULT);
    }

    /** 测试钩子：注入假 DNS 解析，验证连接目标被钉死在解析阶段返回的 IP（防 rebinding）。 */
    public HeadBasedImageUrlValidator(boolean allowPrivateHosts, DnsResolver dnsResolver) {
        this.allowPrivateHosts = allowPrivateHosts;
        this.dnsResolver = dnsResolver;
    }

    @Override
    public void validate(String url) {
        URL parsed;
        try {
            parsed = new URL(url);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址格式非法");
        }
        if (!ALLOWED_SCHEMES.contains(parsed.getProtocol().toLowerCase())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址仅支持 http/https");
        }

        String host = parsed.getHost();
        InetAddress[] addrs = resolve(host);
        if (!allowPrivateHosts) {
            for (InetAddress addr : addrs) {
                if (isPrivateOrInternal(addr)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "图片地址主机不允许指向内网");
                }
            }
        }
        // 钉死校验过的 IP —— 连接阶段复用它，杜绝 HttpURLConnection 再次 DNS 解析（防 rebinding）。
        InetAddress pinned = addrs[0];

        HttpURLConnection conn = null;
        try {
            conn = openConnection(parsed, host, pinned);
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

    /**
     * 用钉死的 IP 建立连接，而非让 HttpURLConnection 重新解析域名。
     * <p>
     * HTTPS 注意：连接目标为 pinned IP 时，TLS ClientHello 的 SNI 扩展会发 IP 而非域名。
     * 少数基于 SNI 选证的虚拟主机 CDN 可能因此探活失败（可用性问题，非安全 —— 前端 {@code <img>}
     * 渲染时浏览器仍会独立校验证书）。证书匹配通过 {@link OriginalHostHostnameVerifier} 委托
     * JDK 默认校验器比对原域名（而非连接的 IP）。
     */
    private HttpURLConnection openConnection(URL parsed, String host, InetAddress pinned) {
        int port = parsed.getPort() == -1 ? parsed.getDefaultPort() : parsed.getPort();
        URL connectUrl;
        try {
            connectUrl = new URL(parsed.getProtocol(), pinned.getHostAddress(), port, parsed.getFile());
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址格式非法");
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) connectUrl.openConnection();
            conn.setRequestProperty("Host", host); // 保留原 Host header（虚拟主机路由）
            if (conn instanceof HttpsURLConnection https) {
                https.setHostnameVerifier(new OriginalHostHostnameVerifier(host));
            }
            return conn;
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址校验失败");
        }
    }

    private InetAddress[] resolve(String host) {
        try {
            InetAddress[] addrs = dnsResolver.resolve(host);
            if (addrs == null || addrs.length == 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "图片地址主机无法解析");
            }
            return addrs;
        } catch (UnknownHostException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址主机无法解析");
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

    /**
     * 连接目标为 pinned IP，默认 HostnameVerifier 校验的是该 IP（证书 SAN 不含 IP 必失败）。
     * 这里改为委托 JDK 默认校验器比对原域名：证书 SAN/CN 须匹配原始 host 才放行。
     */
    private static final class OriginalHostHostnameVerifier implements HostnameVerifier {
        private final String expectedHost;

        OriginalHostHostnameVerifier(String expectedHost) {
            this.expectedHost = expectedHost;
        }

        @Override
        public boolean verify(String connHost, SSLSession session) {
            return HttpsURLConnection.getDefaultHostnameVerifier().verify(expectedHost, session);
        }
    }
}
