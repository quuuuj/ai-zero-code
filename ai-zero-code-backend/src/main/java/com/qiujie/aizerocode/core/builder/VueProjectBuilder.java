package com.qiujie.aizerocode.core.builder;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qiujie.aizerocode.core.saver.GeneratedAssetRewriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class VueProjectBuilder {

    /**
     * 远程构建服务基础地址（生产环境配置为 http://ai-zero-code-builder:3000，
     * 本地为空时直接在宿主机执行 npm 命令）
     */
    private final String builderBaseUrl;

    /**
     * 显式指定 HTTP/1.1：JDK HttpClient 默认 HTTP/2 会向明文 http:// 服务发送 "Upgrade: h2c"
     * 升级头，个别服务（如 browserless v2）处理异常。统一用 HTTP/1.1 最稳妥。
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public VueProjectBuilder(@Value("${app.builder.base-url:}") String builderBaseUrl) {
        this.builderBaseUrl = builderBaseUrl == null ? "" : builderBaseUrl.trim();
    }


    /**
     * 异步构建 Vue 项目
     *
     * @param path 项目路径
     */
    public void buildProjectAsync(String path) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
                    try {
                        buildProject(path);
                    } catch (Exception e) {
                        log.error("异步构建Vue 项目失败：{}", e.getMessage());
                    }
                });
    }


    /**
     * 构建 Vue 项目
     *
     * @param path 项目路径
     * @return 是否构建成功
     */
    public boolean buildProject(String path) {
        File projectDir = new File(path);
        if (!projectDir.exists()) {
            log.error("项目目录不存在： {}", path);
            return false;
        }
        // 检查是否有package.json文件
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("项目目录下不存在 package.json 文件： {}", path);
            return false;
        }
        // 执行npm install & npm run build（生产走远程构建服务，本地直接执行 npm 命令）
        if (!builderBaseUrl.isEmpty()) {
            if (!buildRemotely(projectDir)) {
                log.error("远程构建失败： {}", path);
                return false;
            }
        } else {
            // 执行npm install
            if (!executeNpmInstall(projectDir)) {
                log.error("npm install 执行失败： {}", path);
                return false;
            }
            // 执行npm run build
            if (!executeNpmBuild(projectDir)) {
                log.error("npm run build 执行失败： {}", path);
                return false;
            }
        }
        // 检查是否有dist目录
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists() || !distDir.isDirectory()) {
            log.error("项目目录下不存在 dist 目录： {}", path);
            return false;
        }
        // 构建产物中仍可能包含外部图片引用（JS 模板字符串、CSS url 等），统一改写为同源代理地址，
        // 保证部署到子路径后图片可用
        rewriteDistImages(distDir);
        log.info("项目构建成功： {}", path);
        return true;

    }

    /**
     * 递归改写 dist 下 HTML/CSS/JS 文件中的外部图片引用
     */
    private void rewriteDistImages(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                rewriteDistImages(file);
            } else if (file.isFile() && isRewritable(file.getName())) {
                try {
                    String content = FileUtil.readString(file, StandardCharsets.UTF_8);
                    String rewritten = GeneratedAssetRewriter.rewrite(content);
                    if (!rewritten.equals(content)) {
                        FileUtil.writeString(rewritten, file, StandardCharsets.UTF_8);
                        log.info("已改写 dist 资源引用：{}", file.getName());
                    }
                } catch (Exception e) {
                    log.error("改写 dist 资源失败: {}", file.getName(), e);
                }
            }
        }
    }

    private boolean isRewritable(String name) {
        return name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".js");
    }


    /**
     * 调用远程构建服务（独立 Node 容器，共享 tmp/code 卷）执行 npm install & npm run build
     *
     * @param projectDir 项目目录（后端与构建服务使用相同的绝对路径）
     * @return 是否构建成功（dist 已生成）
     */
    private boolean buildRemotely(File projectDir) {
        try {
            String body = JSONUtil.createObj().set("path", projectDir.getAbsolutePath()).toString();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(builderBaseUrl + "/build"))
                    .timeout(Duration.ofMinutes(10)) // 覆盖 install 5min + build 3min
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.error("远程构建服务返回异常状态码： {}", response.statusCode());
                return false;
            }
            JSONObject result = JSONUtil.parseObj(response.body());
            boolean success = result.getBool("success", false);
            if (!success) {
                log.error("远程构建失败，构建日志：\n{}", result.getStr("logTail"));
            }
            return success;
        } catch (Exception e) {
            log.error("远程构建请求异常： {}", e.getMessage());
            return false;
        }
    }

    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300); // 5分钟超时
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180); // 3分钟超时
    }


    /**
     * 构建 npm 命令（win下，直接执行npm命令会保报错）
     *
     * @param baseCommand 基础命令
     * @return 构建后的命令
     */
    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }


    /**
     * 判断当前操作系统是否为 Windows
     *
     * @return true 表示当前操作系统为 Windows，false 表示当前操作系统为 Linux 或 MacOS
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }


    /**
     * 执行命令
     *
     * @param workingDir     工作目录
     * @param command        命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令：： {}", workingDir.getAbsolutePath(), command);
            Process process = RuntimeUtil.exec(
                    null,
                    workingDir,
                    command.split("\\s+") // 按空格将命令分割为数组
            );
            // 等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return false;
            }
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功： {}", command);
                return true;
            } else {
                log.error("命令执行失败，退出码： {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            log.error("执行命令失败： {}, 错误信息： {}", command, e.getMessage());
            return false;
        }
    }

}

