package com.qiujie.aizerocode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.qiujie.aizerocode.model.dto.app.AppAddRequest;
import com.qiujie.aizerocode.model.dto.app.AppDeployRequest;
import com.qiujie.aizerocode.model.dto.app.AppQueryRequest;
import com.qiujie.aizerocode.model.entity.App;
import com.qiujie.aizerocode.model.entity.User;
import com.qiujie.aizerocode.model.vo.AppVO;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author qiujie
 */
public interface AppService extends IService<App> {


    /**
     * 创建应用
     *
     * @param appAddRequest
     * @param request
     * @return
     */
    Long createApp(AppAddRequest appAddRequest, HttpServletRequest request);

    /**
     * 获取应用视图
     *
     * @param app
     * @return
     */
    AppVO getAppVO(App app);


    /**
     * 获取查询包装类
     *
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);


    /**
     * 获取应用视图列表
     *
     * @param appList
     * @return
     */
    List<AppVO> getAppVOList(List<App> appList);


    /**
     * 通过对话生成代码
     *
     * @param appId
     * @param userMessage
     * @param lginUser
     * @return
     */
    Flux<String> chatToCodegen(Long appId, String userMessage, User lginUser);


    /**
     * 部署应用，将生成的代码文件复制到部署目录并更新数据库
     *
     * @param appDeployRequest 部署请求（包含 appId）
     * @param loginUser        当前登录用户
     * @param request          当前请求，用于解析部署域名与协议
     */
    String deployApp(AppDeployRequest appDeployRequest, User loginUser, HttpServletRequest request);

}
