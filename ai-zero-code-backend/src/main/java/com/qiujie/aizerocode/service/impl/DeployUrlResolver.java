package com.qiujie.aizerocode.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 部署访问 URL 解析器。
 *
 * <p>URL 格式为 {@code {scheme}://{deployHost}/{deployKey}}：
 * <ul>
 *   <li>{@code deployHost} 从配置项 {@code app.deploy-host} 注入（本地 localhost，生产为部署域名）；</li>
 *   <li>{@code scheme} 优先取配置项 {@code app.deploy-scheme}（生产环境显式配置 https，避免依赖网关转发头），
 *       其次取 {@code X-Forwarded-Proto} 请求头（gateway-nginx 转发 https 时使用），
 *       最后按 Host 端口推断：443 → https，其他端口 → http。</li>
 * </ul>
 */
@Component
@Slf4j
public class DeployUrlResolver {

    private final String deployHost;

    private final String deployScheme;

    public DeployUrlResolver(@Value("${app.deploy-host}") String deployHost,
                             @Value("${app.deploy-scheme:}") String deployScheme) {
        this.deployHost = deployHost;
        this.deployScheme = deployScheme == null ? "" : deployScheme.trim();
    }

    public String resolve(HttpServletRequest request, String deployKey) {
        String scheme = resolveScheme(request);
        return String.format("%s://%s/%s", scheme, deployHost, deployKey);
    }

    private String resolveScheme(HttpServletRequest request) {
        if (!deployScheme.isEmpty()) {
            return deployScheme;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return forwardedProto.split(",")[0].trim();
        }
        String host = request.getHeader("Host");
        if (host != null && host.endsWith(":443")) {
            return "https";
        }
        return "http";
    }
}
