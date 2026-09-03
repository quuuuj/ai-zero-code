package com.qiujie.aizerocode.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.qiujie.aizerocode.exception.ErrorCode;
import com.qiujie.aizerocode.exception.ThrowUtils;
import com.qiujie.aizerocode.manager.OssManager;
import com.qiujie.aizerocode.service.ScreenshotService;
import com.qiujie.aizerocode.utils.WebScreenshotUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {


    @Autowired
    private OssManager ossManager;

    @Autowired
    private WebScreenshotUtil webScreenshotUtil;

    @Override
    public String takeAndUploadScreenshot(String webUrl) {
        // 1. 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "截图网址不能为空");
        // 2. 截图
        String localImgPath = webScreenshotUtil.takeScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localImgPath), ErrorCode.OPERATION_ERROR, "截图失败");
        File img = new File(localImgPath);
        // 3. 构建存放key（存放目录）
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = RandomUtil.randomString(10) + "_compress.jpg";
        String key = String.format("screenshot/%s/%s", dateStr, filename);
        try {
            // 4. 上传
            String url = ossManager.upload(key, img);
            ThrowUtils.throwIf(StrUtil.isBlank(url), ErrorCode.OPERATION_ERROR, "截图上传失败");
            return url;
        } finally {
            // 5. 删除本地文件
            FileUtil.del(localImgPath);
        }
    }
}
