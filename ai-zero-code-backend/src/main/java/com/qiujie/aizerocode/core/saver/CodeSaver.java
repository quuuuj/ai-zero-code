package com.qiujie.aizerocode.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.qiujie.aizerocode.exception.BusinessException;
import com.qiujie.aizerocode.exception.ErrorCode;
import com.qiujie.aizerocode.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static com.qiujie.aizerocode.constant.AppConstant.CODE_SAVE_PATH;

@Slf4j
public abstract class CodeSaver<T> {




    /**
     * 保存代码
     *
     * @param result 生成的代码
     * @param appId  应用 id
     * @return
     */
    public final File save(T result, Long appId) {
        // 1. 验证输入
        validateInput(result);
        // 2. 构建存放目录
        String dirPath = createFileDir(appId);
        // 3. 保存代码到文件中
        saveToFile(dirPath, result);
        // 4. 返回存放目录
        return new File(dirPath);
    }

    /**
     * 验证生成的代码内容
     *
     * @param result
     */
    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码内容不能为空");
        }
    }


    /**
     * 创建代码文件存放目录
     *
     * @param appId 应用 id
     * @return
     */
    private String createFileDir(Long appId) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "appId不能为空");
        }
        String type = getCodeGenType().getValue();
        String dirPath = CODE_SAVE_PATH + File.separator + type + File.separator + appId;
        FileUtil.mkdir(dirPath);
        log.info("代码文件存放目录为：{}", dirPath);
        return dirPath;
    }


    /**
     * 获取代码生成类型
     *
     * @return
     */
    protected abstract CodeGenTypeEnum getCodeGenType();


    /**
     * 保存代码到文件中
     *
     * @param dirPath
     * @param result
     */
    protected abstract void saveToFile(String dirPath, T result);


    /**
     * 写入文件
     *
     * @param dirPath
     * @param fileName
     * @param content
     */
    protected final void writeToFile(String dirPath, String fileName, String content) {
        // AI 响应不一定包含所有代码块，跳过空内容避免 NPE
        if (content == null || content.isEmpty()) {
            return;
        }
        String filePath = dirPath + File.separator + fileName;
        FileUtil.writeString(GeneratedAssetRewriter.rewrite(content), filePath, StandardCharsets.UTF_8);
    }
}
