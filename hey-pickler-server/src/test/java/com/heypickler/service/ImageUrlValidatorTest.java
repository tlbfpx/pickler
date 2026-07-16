package com.heypickler.service;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.service.impl.HeadBasedImageUrlValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ImageUrlValidatorTest {

    private HttpServer server;
    private HeadBasedImageUrlValidator validator;
    private ExecutorService executor;
    private final AtomicInteger hitCount = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        validator = new HeadBasedImageUrlValidator(true);
        hitCount.set(0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    private void mount(String path, HttpHandler handler) {
        server.createContext(path, handler);
        server.start();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    void validate_ok_whenHeadReturns200WithImageContentType() {
        mount("/ok.jpg", exchange -> {
            hitCount.incrementAndGet();
            byte[] body = "img".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });

        assertDoesNotThrow(() -> validator.validate(url("/ok.jpg")));
        assertEquals(1, hitCount.get(), "HEAD should hit the server exactly once");
    }

    @Test
    void validate_throws_whenHeadReturns404() {
        mount("/missing.jpg", exchange -> {
            hitCount.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.getResponseBody().close();
        });

        BizException ex = assertThrows(BizException.class,
            () -> validator.validate(url("/missing.jpg")));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("不可访问"), "Should mention unreachable");
    }

    @Test
    void validate_throws_whenContentTypeIsNotImage() {
        mount("/notimg.jpg", exchange -> {
            hitCount.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });

        BizException ex = assertThrows(BizException.class,
            () -> validator.validate(url("/notimg.jpg")));
        assertTrue(ex.getMessage().contains("非图片"), "Should mention non-image content");
    }

    @Test
    void validate_throws_whenHostUnreachable() {
        // 192.0.2.0/24 is TEST-NET-1 (RFC 5737) — guaranteed not to route
        BizException ex = assertThrows(BizException.class,
            () -> validator.validate("https://192.0.2.1/no-such-image.jpg"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("校验失败"), "Should mention validation failed");
    }

    // ---------- SSRF 防护（严格模式 allowPrivateHosts=false，不发网络请求）----------

    @Test
    void validate_rejectsLoopbackHost_whenSsrfGuardOn() {
        HeadBasedImageUrlValidator strict = new HeadBasedImageUrlValidator();
        BizException ex = assertThrows(BizException.class,
            () -> strict.validate("http://127.0.0.1:8080/x.jpg"));
        assertTrue(ex.getMessage().contains("内网"), "loopback 应被拒");
    }

    @Test
    void validate_rejectsPrivateRange_whenSsrfGuardOn() {
        HeadBasedImageUrlValidator strict = new HeadBasedImageUrlValidator();
        BizException ex = assertThrows(BizException.class,
            () -> strict.validate("http://10.0.0.5/x.jpg"));
        assertTrue(ex.getMessage().contains("内网"), "10/8 私网应被拒");
    }

    @Test
    void validate_rejectsLinkLocal_whenSsrfGuardOn() {
        // AWS IMDS 169.254.169.254 必须拒（防元数据服务探测）
        HeadBasedImageUrlValidator strict = new HeadBasedImageUrlValidator();
        assertThrows(BizException.class,
            () -> strict.validate("http://169.254.169.254/latest/meta-data/x.jpg"));
    }

    @Test
    void validate_rejectsNonHttpScheme() {
        HeadBasedImageUrlValidator strict = new HeadBasedImageUrlValidator();
        BizException ex = assertThrows(BizException.class,
            () -> strict.validate("file:///etc/passwd"));
        assertTrue(ex.getMessage().contains("http/https"), "伪协议应被拒");
    }
}
