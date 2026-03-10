# 前端部署指南

为了将重构后的 Vue 3 前端部署到生产环境，推荐采用 **前后端合并部署** 的方案，即将前端构建后的静态文件放入 Spring Boot 的 `static` 目录中。

## 方案一：集成到 Spring Boot 部署（推荐）

这是最简单的方式，只需要将前端编译生成的 `dist` 目录内容拷贝到后端的静态资源目录。

### 自动化脚本
你可以运行根目录下的 `deploy_frontend.bat` 自动化完成此过程：
1. **构建前端**：进入 `frontend/` 目录运行 `npm run build`（已配置 `base: '/monitor/'`）。
2. **准备目录**：在后端 `src/main/resources/static/` 下创建 `monitor` 文件夹。
3. **拷贝新版**：将 `frontend/dist/` 下的所有文件拷贝到 `static/monitor/` 目录中。

**此操作不会影响你原有的 `command.html`, `dashboard.html` 等调试文件。**

### 访问路径
部署完成后，启动 Spring Boot 后端，访问：
`http://localhost:8080/monitor/index.html`

---

## 方案二：独立 Nginx 部署（前后端分离）

如果你希望前端通过 Nginx 独立运行，请按照以下步骤操作：

1. **构建前端**：
   在 `frontend/` 目录下执行：
   ```bash
   npm run build
   ```
2. **配置 Nginx**：
   将 `dist/` 目录拷贝到 Nginx 的 `html` 目录，并配置反向代理以处理 API 请求。
   示例配置：
   ```nginx
   server {
       listen       80;
       server_name  localhost;

       location / {
           root   /usr/share/nginx/html;
           index  index.html;
           try_files $uri $uri/ /index.html;
       }

       # 代理 API 请求到后端
       location /api/ {
           proxy_pass http://localhost:8080;
       }

       # 代理 WebSocket 请求
       location /ws/ {
           proxy_pass http://localhost:8080;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
       }
   }
   ```

---

## 注意事项
- **API 基础路径**：当前 Vue 应用在开发环境下通过 `vite.config.js` 的 `proxy` 代理请求。在生产环境下，如果是非 Nginx 方案，前端代码会直接请求当前域名的相对路径（如 `/api/...`），这与 Spring Boot 合并部署完美兼容。
- **404 问题**：由于使用了 `history` 路由模式，必须确保服务器（Nginx 或 Spring Boot）在找不到资源时指向 `index.html`。Spring Boot 默认已处理静态资源，但如果遇到刷新 404，请确保后台有对应的 Controller 转发。
