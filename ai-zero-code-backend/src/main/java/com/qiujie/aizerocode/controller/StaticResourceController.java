package com.qiujie.aizerocode.controller;

import com.qiujie.aizerocode.exception.ErrorCode;
import com.qiujie.aizerocode.exception.ThrowUtils;
import com.qiujie.aizerocode.model.entity.App;
import com.qiujie.aizerocode.model.enums.CodeGenTypeEnum;
import com.qiujie.aizerocode.service.AppService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;

import static com.qiujie.aizerocode.constant.AppConstant.CODE_SAVE_PATH;

@RestController
@RequestMapping("/static")
public class StaticResourceController {


    @Autowired
    private AppService appService;

    /**
     * 应用预览
     *
     * @param appId
     * @param request
     * @return
     */
    @GetMapping("/{appId}/**")
    public ResponseEntity<Resource> serveStaticResource(@PathVariable Long appId, HttpServletRequest request) {
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + appId).length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = File.separator + "index.html";
            }
            // 构建文件路径
            String filePath = CODE_SAVE_PATH + File.separator + app.getCodeGenType() + File.separator + appId;
            // Vue 工程模式的运行入口在构建产物 dist/ 下，统一从应用根路径解析入口
            if (CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType())
                    && !resourcePath.startsWith(File.separator + "dist")) {
                filePath += File.separator + "dist";
            }
            filePath += resourcePath;
            File file = new File(filePath);
            // 检查文件是否存在
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) return "image/jpeg";
        if (filePath.endsWith(".gif")) return "image/gif";
        if (filePath.endsWith(".svg")) return "image/svg+xml";
        if (filePath.endsWith(".webp")) return "image/webp";
        if (filePath.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}
