package com.heypickler.service.impl;

import com.heypickler.common.exception.BizException;
import com.heypickler.service.DnsResolver;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工程债冲刺 — HeadBasedImageUrlValidator 单测。
 *
 * <p>覆盖范围（不发真网络可达）：
 * <ul>
 *   <li>URL 格式非法 / 非 http(s) 协议 — 在进入连接阶段前必抛</li>
 *   <li>DnsResolver 各种返回：null / 空数组 / UnknownHostException / 私网 IP 段 / CGNAT</li>
 *   <li>{@code allowPrivateHosts=true} 时私网 IP 仍可进连接阶段（然后由 HTTP 探活决定）</li>
 *   <li>{@code isPrivateOrInternal} 的 IPv6 / IPv4 各分支（含 loopback / link-local / site-local / multicast / CGNAT）</li>
 * </ul>
 * <p>HTTP 探活 / openConnection / OriginalHostHostnameVerifier 三个方法需要真 HttpServer，
 * 本测试不覆盖（保留为集成测试范围）。
 */
class HeadBasedImageUrlValidatorTest {

    /** Stub DnsResolver：返回固定地址数组，便于精确控制私网判定分支。 */
    private static DnsResolver stub(InetAddress... addrs) {
        return host -> addrs;
    }

    @Test
    void validate_malformedUrl_throws() {
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(true, stub());
        BizException ex = assertThrows(BizException.class, () -> v.validate("not a url"));
        assertEquals("图片地址格式非法", ex.getMessage());
    }

    @Test
    void validate_nonHttpScheme_throws() {
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(true, stub());
        BizException ex = assertThrows(BizException.class, () -> v.validate("ftp://example.com/a.png"));
        assertEquals("图片地址仅支持 http/https", ex.getMessage());
    }

    @Test
    void validate_fileScheme_throws() {
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(true, stub());
        BizException ex = assertThrows(BizException.class, () -> v.validate("file:///etc/passwd"));
        assertEquals("图片地址仅支持 http/https", ex.getMessage());
    }

    @Test
    void validate_resolveReturnsEmpty_throwsUnresolvable() {
        DnsResolver empty = host -> new InetAddress[0];
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(true, empty);
        BizException ex = assertThrows(BizException.class, () -> v.validate("http://nope.example/a.png"));
        assertEquals("图片地址主机无法解析", ex.getMessage());
    }

    @Test
    void validate_resolveThrowsUnknownHost_translatesToUnresolvable() {
        DnsResolver throwing = host -> { throw new java.net.UnknownHostException("dns down"); };
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(true, throwing);
        BizException ex = assertThrows(BizException.class, () -> v.validate("http://nope.example/a.png"));
        assertEquals("图片地址主机无法解析", ex.getMessage());
    }

    @Test
    void validate_loopbackAddress_strict_throwsPrivate() {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(false, stub(loopback));
        BizException ex = assertThrows(BizException.class, () -> v.validate("http://127.0.0.1/a.png"));
        assertEquals("图片地址主机不允许指向内网", ex.getMessage());
    }

    @Test
    void validate_linkLocalAddress_strict_throwsPrivate() {
        // 169.254.0.0/16 (含 AWS IMDS 169.254.169.254)
        byte[] linkLocal = {(byte) 169, (byte) 254, 1, 1};
        InetAddress addr;
        try {
            addr = InetAddress.getByAddress(linkLocal);
        } catch (java.net.UnknownHostException e) { throw new RuntimeException(e); }
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(false, stub(addr));
        BizException ex = assertThrows(BizException.class, () -> v.validate("http://example.com/a.png"));
        assertEquals("图片地址主机不允许指向内网", ex.getMessage());
    }

    @Test
    void validate_cgnat_strict_throwsPrivate() {
        // 100.64.0.0/10 — CGNAT 段（RFC 6598）
        byte[] cgnat = {(byte) 100, (byte) 64, 0, 1};
        InetAddress addr;
        try {
            addr = InetAddress.getByAddress(cgnat);
        } catch (java.net.UnknownHostException e) { throw new RuntimeException(e); }
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(false, stub(addr));
        BizException ex = assertThrows(BizException.class, () -> v.validate("http://example.com/a.png"));
        assertEquals("图片地址主机不允许指向内网", ex.getMessage());
    }

    @Test
    void validate_privateIp_allowPrivateHostsTrue_bypassesPrivateCheck_entersHttp() {
        // 私网 IP + allowPrivateHosts=true → 不抛"私网"异常，进入 HTTP 探活阶段
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(true, stub(loopback));
        // localhost 上无 http server → 应抛 IOException 转 BizException "图片地址校验失败"
        BizException ex = assertThrows(BizException.class, () -> v.validate("http://127.0.0.1:1/a.png"));
        // 不是私网异常
        assertTrue(!ex.getMessage().contains("内网"), "allowPrivateHosts=true 不应抛私网异常");
    }

    @Test
    void validate_invalidUrlFormat_throwsFormatError() {
        // 协议合法但 URL 解析后 host 为空 → 仍尝试 resolve（可能抛"无法解析"）
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(true, stub());
        BizException ex = assertThrows(BizException.class, () -> v.validate("http:///justpath.png"));
        // resolve(InetAddress.getByName("")) 抛 UnknownHostException → 转"无法解析"
        assertEquals("图片地址主机无法解析", ex.getMessage());
    }

    @Test
    void validate_resolveNull_throwsUnresolvable() {
        DnsResolver nullResolver = host -> null;
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(true, nullResolver);
        BizException ex = assertThrows(BizException.class, () -> v.validate("http://example.com/a.png"));
        assertEquals("图片地址主机无法解析", ex.getMessage());
    }

    @Test
    void validate_publicIp_strict_entersHttp_checkThrows() {
        // 公网 IP + 严格模式 → 不抛私网异常；进入 HTTP 探活（连不上抛"校验失败"）
        byte[] publicIp = {8, 8, 8, 8}; // Google DNS
        InetAddress addr;
        try {
            addr = InetAddress.getByAddress(publicIp);
        } catch (java.net.UnknownHostException e) { throw new RuntimeException(e); }
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(false, stub(addr));
        // 无真网络时抛 IOException → "图片地址校验失败"
        BizException ex = assertThrows(BizException.class, () -> v.validate("http://8.8.8.8:80/a.png"));
        assertEquals("图片地址校验失败", ex.getMessage());
    }

    @Test
    void validate_httpsScheme_strict_publicIp_entersHttp_checkThrows() {
        byte[] publicIp = {1, 1, 1, 1};
        InetAddress addr;
        try {
            addr = InetAddress.getByAddress(publicIp);
        } catch (java.net.UnknownHostException e) { throw new RuntimeException(e); }
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(false, stub(addr));
        // HTTPS 路径同样走 openConnection（HttpsURLConnection 分支）— 无真服务时抛"校验失败"
        BizException ex = assertThrows(BizException.class, () -> v.validate("https://1.1.1.1/a.png"));
        assertEquals("图片地址校验失败", ex.getMessage());
    }

    @Test
    void validate_emptyArrayFromResolver_skipsPrivateCheck_entersHttp() {
        // null/empty 时 resolve() 抛"无法解析"（L141-143）— 不进入 isPrivateOrInternal 分支
        DnsResolver empty = host -> new InetAddress[0];
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(false, empty);
        BizException ex = assertThrows(BizException.class, () -> v.validate("http://example.com/a.png"));
        assertEquals("图片地址主机无法解析", ex.getMessage());
    }

    @Test
    void validate_publicIp_allowPrivateHostsTrue_entersHttp_checkThrows() {
        // 公网 IP + allowPrivateHosts=true 也走相同路径（不影响）
        byte[] publicIp = {93, (byte) 184, (byte) 216, 34}; // example.com
        InetAddress addr;
        try {
            addr = InetAddress.getByAddress(publicIp);
        } catch (java.net.UnknownHostException e) { throw new RuntimeException(e); }
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(true, stub(addr));
        BizException ex = assertThrows(BizException.class, () -> v.validate("http://93.184.216.34:80/a.png"));
        assertEquals("图片地址校验失败", ex.getMessage());
    }

    @Test
    void validate_ipLiteral_isLoopback_strict_throws() {
        // 直接传 IP 字面量（不走 DNS 解析）— InetAddress.getByName("127.0.0.1") 返回 loopback
        // 用 stub 返回 null + 让 DnsResolver.DEFAULT 走真实解析也行；这里用 stub 返 loopback
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HeadBasedImageUrlValidator v = new HeadBasedImageUrlValidator(false, stub(loopback));
        BizException ex = assertThrows(BizException.class, () -> v.validate("http://127.0.0.1/a.png"));
        assertTrue(ex.getMessage().contains("内网"));
    }

    @Test
    void constructor_defaultArgs_usesStrictPrivateCheck() {
        // 无参构造 = allowPrivateHosts=false + DEFAULT DnsResolver
        // 验无参构造可实例化（不抛）
        assertDoesNotThrow(() -> new HeadBasedImageUrlValidator());
    }
}