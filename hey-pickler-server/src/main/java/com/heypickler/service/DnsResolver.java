package com.heypickler.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * DNS 解析抽象。抽出后便于在 {@link com.heypickler.service.impl.HeadBasedImageUrlValidator}
 * 中钉死解析结果（防 DNS rebinding），并便于单测注入假解析验证连接目标。
 */
@FunctionalInterface
public interface DnsResolver {

    DnsResolver DEFAULT = host -> InetAddress.getAllByName(host);

    InetAddress[] resolve(String host) throws UnknownHostException;
}
