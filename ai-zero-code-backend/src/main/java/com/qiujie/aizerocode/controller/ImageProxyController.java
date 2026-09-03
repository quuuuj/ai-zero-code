package com.qiujie.aizerocode.controller;

import com.qiujie.aizerocode.exception.ErrorCode;
import com.qiujie.aizerocode.exception.ThrowUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Locale;

@RestController
@RequestMapping("/image")
public class ImageProxyController {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxy(@RequestParam String url) {
        try {
            URI uri = URI.create(url);
            validateUri(uri);
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            // 图床普遍 302 跳转到 CDN（如 picsum.photos → fastly），必须跟随重定向，否则所有占位图 404
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
            int status = connection.getResponseCode();
            // 跟随重定向后再次校验最终地址，防止被重定向到内网
            validateUri(connection.getURL().toURI());
            String contentType = connection.getContentType();
            if (status < 200 || status >= 300 || contentType == null
                    || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return ResponseEntity.notFound().build();
            }
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_IMAGE_BYTES) {
                return ResponseEntity.status(413).build();
            }
            byte[] image = readLimited(connection, MAX_IMAGE_BYTES);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(image);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private void validateUri(URI uri) throws IOException {
        String scheme = uri.getScheme();
        ThrowUtils.throwIf(!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme),
                ErrorCode.PARAMS_ERROR, "图片地址协议不支持");
        String host = uri.getHost();
        ThrowUtils.throwIf(host == null || host.isBlank(), ErrorCode.PARAMS_ERROR, "图片地址无效");
        InetAddress address = InetAddress.getByName(host);
        ThrowUtils.throwIf(address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress(),
                ErrorCode.PARAMS_ERROR, "图片地址不允许访问内网");
    }

    private byte[] readLimited(HttpURLConnection connection, int limit) throws IOException {
        try (var input = connection.getInputStream(); var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > limit) {
                    throw new IOException("image too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
