package com.qiujie.aizerocode.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qiujie.aizerocode.exception.BusinessException;
import com.qiujie.aizerocode.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static com.qiujie.aizerocode.constant.AppConstant.SCREENSHOT_SAVE_PATH;

@Component
@Slf4j
public class WebScreenshotUtil {

    private static final int DEFAULT_WIDTH = 1600;
    private static final int DEFAULT_HEIGHT = 900;

    /**
     * 远程截图服务基础地址（生产环境配置为 http://ai-zero-code-screenshot:3000，即 browserless/chromium）。
     * 本地为空时走本机 Selenium 截图（WebDriverManager 自动管理 chromedriver）。
     */
    private final String screenshotBaseUrl;

    /**
     * 本地 Selenium 驱动，懒加载：仅在未配置远程截图服务时初始化
     */
    private volatile WebDriver webDriver;

    /**
     * 必须显式指定 HTTP/1.1：JDK HttpClient 默认 HTTP/2，向明文 http:// 服务发送 "Upgrade: h2c"
     * 升级头，browserless/chromium（v2）对 h2c 请求处理异常，解析不了请求体，直接返回
     * 400 "Couldn't parse JSON body"，导致部署截图失败（线上已复现）。
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WebScreenshotUtil(@Value("${app.screenshot.base-url:}") String screenshotBaseUrl) {
        this.screenshotBaseUrl = screenshotBaseUrl == null ? "" : screenshotBaseUrl.trim();
    }

    @PreDestroy
    public void destroy() {
        if (webDriver != null) {
            webDriver.quit();
        }
    }


    /**
     * 获取网页截图，并保存到本地
     *
     * @param webUrl
     * @return 压缩后的本地图片路径，失败返回 null
     */
    public String takeScreenshot(String webUrl) {
        // 非空校验
        if (StrUtil.isBlank(webUrl)) {
            log.error("webUrl不能为空");
        }
        try {
            // 创建目录
            String dirPath = SCREENSHOT_SAVE_PATH + File.separator + RandomUtil.randomString(10);
            FileUtil.mkdir(dirPath);
            // 图片后缀
            String imgSuffix = ".png";
            String imgPath = dirPath + File.separator + RandomUtil.randomString(10) + imgSuffix;
            // 截图（远程服务优先，本地 Selenium 兜底）
            byte[] screenshotBytes = screenshotBaseUrl.isEmpty()
                    ? captureLocally(webUrl)
                    : captureRemotely(webUrl);
            // 保存图片
            saveScreenshot(screenshotBytes, imgPath);
            // 压缩图片
            String compressedImgSuffix = "_compress.jpg";
            String compressedImgPath = dirPath + File.separator + RandomUtil.randomString(10) + compressedImgSuffix;
            compressImage(imgPath, compressedImgPath);
            // 删除原始图片
            FileUtil.del(imgPath);
            return compressedImgPath;
        } catch (Exception e) {
            log.error("获取网页截图失败：{}", e.getMessage(), e);
            return null;
        }
    }


    /**
     * 本地 Selenium 截图（懒初始化共享 WebDriver）
     */
    private byte[] captureLocally(String webUrl) {
        WebDriver driver = getOrInitDriver();
        // 访问网页
        driver.get(webUrl);
        // 等待页面加载完成
        waitForPageLoad(driver);
        // 截图
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES); // 也可以直接返回文件
    }


    /**
     * 远程 browserless/chromium 截图服务截图
     */
    private byte[] captureRemotely(String webUrl) throws Exception {
        String body = JSONUtil.createObj()
                .set("url", webUrl)
                .set("viewport", JSONUtil.createObj().set("width", DEFAULT_WIDTH).set("height", DEFAULT_HEIGHT))
                .set("options", JSONUtil.createObj().set("type", "png"))
                .toString();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(screenshotBaseUrl + "/screenshot"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            // 记录响应体便于诊断（browserless 的错误信息在 body 中，如 "Couldn't parse JSON body"）
            String respBody = response.body() == null ? "" : new String(response.body(), StandardCharsets.UTF_8);
            log.error("截图服务返回异常状态码：{}，响应：{}", response.statusCode(), respBody);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "截图服务返回异常状态码：" + response.statusCode());
        }
        return response.body();
    }


    /**
     * 懒初始化本机 Chrome 驱动
     */
    private synchronized WebDriver getOrInitDriver() {
        if (webDriver == null) {
            webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }
        return webDriver;
    }


    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            // 使用预装的 chromedriver（容器镜像已内置 /usr/local/bin/chromedriver），
            // 跳过 WebDriverManager 运行时解析，避免服务器访问不到 Google 下载源时挂起。
            // 本地开发未设置 CHROMEDRIVER_PATH 时，仍由 WebDriverManager 自动管理。
            String chromedriverPath = System.getenv("CHROMEDRIVER_PATH");
            if (StrUtil.isNotBlank(chromedriverPath)) {
                System.setProperty("webdriver.chrome.driver", chromedriverPath);
            } else {
                WebDriverManager.chromedriver().setup();
            }
            // 配置 Chrome 选项
            ChromeOptions options = new ChromeOptions();
            // 无头模式
            options.addArguments("--headless");
            // 禁用GPU（在某些环境下避免问题）
            options.addArguments("--disable-gpu");
            // 禁用沙盒模式（Docker环境需要）
            options.addArguments("--no-sandbox");
            // 禁用开发者shm使用
            options.addArguments("--disable-dev-shm-usage");
            // 设置窗口大小
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            // 禁用扩展
            options.addArguments("--disable-extensions");
            // 设置用户代理
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            // 创建驱动
            WebDriver driver = new ChromeDriver(options);
            // 设置页面加载超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "初始化 Chrome 浏览器失败");
        }
    }


    /**
     * 保存截图
     *
     * @param bytes
     * @param filePath
     */
    private static void saveScreenshot(byte[] bytes, String filePath) {
        try {
            FileUtil.writeBytes(bytes, filePath);
            log.info("截图保存成功：{}", filePath);
        } catch (Exception e) {
            log.error("保存截图失败：{}", filePath, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存截图失败");
        }
    }


    /**
     * 压缩图片
     *
     * @param sourcePath
     * @param targetPath
     */
    private static void compressImage(String sourcePath, String targetPath) {
        final float quality = 0.3f;
        try {
            ImgUtil.compress(FileUtil.file(sourcePath), FileUtil.file(targetPath), quality);
            log.info("图片压缩成功：{} -> {}", sourcePath, targetPath);
        } catch (IORuntimeException e) {
            log.error("图片压缩失败：{} -> {}", sourcePath, targetPath, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片压缩失败");
        }
    }


    /**
     * 等待页面加载完成
     *
     * @param driver
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            // 创建 WebDriverWait 对象
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // 等待 document.readyState 为 complete
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState")
                    .equals("complete"));
            // 额外等待一段时间，确保页面完全加载
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (InterruptedException e) {
            log.error("等待页面加载失败", e);
        }
    }
}
