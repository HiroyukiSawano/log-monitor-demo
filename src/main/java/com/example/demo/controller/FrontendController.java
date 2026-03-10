package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 前端单页应用 (SPA) 路由回退控制器
 * 用于将 /monitor/ 下的路径转发给 index.html，交由 Vue Router 处理
 */
@Controller
public class FrontendController {

    /**
     * 明确匹配 Vue Router 中定义的页面路径，转发到 index.html
     * 避免使用宽泛通配符拦截未来可能的 /monitor/api 等后端接口
     */
    @RequestMapping(value = {
            "/monitor",
            "/monitor/",
            "/monitor/command",
            "/monitor/debug",
            "/monitor/logs"
    })
    public String forwardToMonitor() {
        return "forward:/monitor/index.html";
    }
}
