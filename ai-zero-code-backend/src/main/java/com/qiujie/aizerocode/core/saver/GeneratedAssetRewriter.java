package com.qiujie.aizerocode.core.saver;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites generated image references so they work from preview and deployed sub-paths:
 * - external image URLs are proxied through the same-origin /api/image/proxy endpoint
 * - leading-slash assets are made relative so they resolve under /{appId}/ or /{deployKey}/
 */
public final class GeneratedAssetRewriter {

    private static final Pattern IMAGE_ATTRIBUTE = Pattern.compile(
            "(?i)(\\b(?:src|href)\\s*=\\s*[\\\"'])((?:https?://|/)[^\\\"']+)([\\\"'])");
    private static final Pattern CSS_URL = Pattern.compile(
            "(?i)(url\\(\\s*[\\\"']?)((?:https?://|/)[^\\\"')]+)([\\\"']?\\s*\\))");
    private static final Pattern JS_STRING_IMAGE = Pattern.compile(
            "(?i)([\\\"'`])(https?://[^\\\"'`]+?\\.(?:png|jpe?g|gif|webp|svg)(?:\\?[^\\\"'`]*)?)(\\1)");

    private static final String PROXY_PREFIX = "/api/image/proxy?url=";

    private GeneratedAssetRewriter() {
    }

    public static String rewrite(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String rewritten = rewriteMatches(content, IMAGE_ATTRIBUTE, true);
        rewritten = rewriteMatches(rewritten, CSS_URL, false);
        return rewriteMatches(rewritten, JS_STRING_IMAGE, true);
    }

    private static String rewriteMatches(String content, Pattern pattern, boolean attribute) {
        Matcher matcher = pattern.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group(2);
            String replacement = rewriteUrl(url);
            if (replacement == null) {
                continue;
            }
            String prefix = matcher.group(1);
            String suffix = matcher.group(3);
            matcher.appendReplacement(result, Matcher.quoteReplacement(prefix + replacement + suffix));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * @return rewritten URL, or null if the URL should be left untouched
     */
    private static String rewriteUrl(String url) {
        if (isAlreadyProxy(url)) {
            return null;
        }
        if (isExternal(url)) {
            return looksLikeImage(url) ? proxyUrl(url) : null;
        }
        // leading-slash local asset: make relative for sub-path deployment
        if (url.startsWith("/") && !url.startsWith("//") && looksLikeImage(url)) {
            return url.substring(1);
        }
        return null;
    }

    private static boolean isExternal(String url) {
        return url.regionMatches(true, 0, "http://", 0, 7)
                || url.regionMatches(true, 0, "https://", 0, 8);
    }

    private static boolean isAlreadyProxy(String url) {
        return url.startsWith("/api/image/proxy");
    }

    /**
     * 仅改写「看起来像图片」的 URL，避免把字体、样式表等错误代理导致破图/破样式。
     */
    private static boolean looksLikeImage(String url) {
        String path = url.toLowerCase(Locale.ROOT);
        if (path.indexOf('?') > 0) {
            path = path.substring(0, path.indexOf('?'));
        }
        if (path.matches(".*\\.(png|jpe?g|gif|webp|svg|ico)$")) {
            return true;
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return false;
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            return lowerHost.contains("picsum.photos")
                    || lowerHost.contains("pexels.com")
                    || lowerHost.contains("undraw.co");
        } catch (Exception e) {
            return false;
        }
    }

    private static String proxyUrl(String url) {
        return PROXY_PREFIX + URLEncoder.encode(url, StandardCharsets.UTF_8);
    }
}