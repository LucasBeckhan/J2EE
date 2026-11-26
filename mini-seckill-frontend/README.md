# 即刻秒杀 · 独立前端

学习入口：[从一次点击读懂整个项目](../mini-seckill/学习导读.md)，包含阅读顺序、完整调用链、事务与并发示例、前端状态说明和断点建议。

Vue 3 + Vite 实现的响应式秒杀页面，与 `../mini-seckill` 的 Spring Boot API 对接。前后端目录、依赖、启动进程和构建产物独立。浏览器通过 `/api` 调用后端，不把页面打进 Java JAR。

## 本地启动（两个终端）

需要完整 JDK 8、Maven，以及 Node.js 22.12+（推荐受支持的 LTS 版本）。

终端 1，在仓库根目录：

```bash
mvn -f mini-seckill/pom.xml spring-boot:run
```

终端 2，在本目录：

```bash
npm ci
npm run dev
```

打开 http://127.0.0.1:5173。Vite 把 `/api` 请求代理到 `http://127.0.0.1:8080`。若后端地址不同，复制 `.env.example` 为 `.env.local`，修改 `API_PROXY_TARGET`，然后重启 Vite。

若 Maven 报 `No compiler is provided`，请把 IDE 的 Maven Runner JRE 和终端的 JAVA_HOME 设置为**完整 JDK**。当前机器可使用：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_321.jdk/Contents/Home mvn -f mini-seckill/pom.xml spring-boot:run
```

## 功能与数据流

- 秒杀大厅：后端商品、真实价格和剩余库存；分类、关键词搜索、手动刷新。
- 演示身份：正整数用户 ID 切换；查询和购买都使用同一个已确认的 ID。
- 下单：禁用重复点击，接收后端售罄/限购提示；成功后刷新库存与订单。
- 我的订单：展示后端订单编号、商品、用户、创建时间及真实状态。
- 异常：商品/订单独立报错；网络超时不自动重试下单，提示先查订单。
- 不设置虚假倒计时、原价、销量或付款成功提示，因为现有后端未提供这些信息。

```text
浏览器 :5173 → Vite /api 代理 → Java :8080 → H2 内存数据库
生产浏览器 → Nginx 静态前端 + /api 反向代理 → Java → 数据库
```

## 代码阅读顺序

1. `src/api/client.js`：超时、错误解析、三个接口。
2. `src/App.vue`：页面状态、用户切换、下单流程和页面模板（含中文注释）。
3. `src/components/ProductArt.vue`：本地商品装饰插图，不依赖远程图片。
4. `src/style.css`：桌面/平板/手机布局与键盘焦点。
5. `vite.config.js`：开发与预览代理。

## 构建与验证

```bash
npm test
npm run build
npm run preview
```

`dist/` 是独立静态产物。`preview` 仅用于本地检查，端口为 4173；生产部署参考 `deploy/nginx.conf`，把 `dist/` 放入静态目录并配置 `/api` 代理。直接双击 `dist/index.html` 不能完成 API 联调。

## 边界

当前仍是学习用系统：用户 ID 不是登录鉴权；H2 重启后数据清空；没有支付、取消、定时活动、Redis 或消息队列。订单界面不会承诺未实现的功能。库存最后一件由数据库条件更新决定，前端禁用按钮只是交互优化。

## 搜索与设计参考

- [Vue 官方快速开始](https://vuejs.org/guide/quick-start.html)：Vue 单文件组件与 Vite 工程。
- [Vite 官方代理配置](https://vite.dev/config/server-options#server-proxy)：开发时代理 API。
- [京东秒杀](https://h5.m.jd.com/active/jmp/seckill/seckill.html)：参考限量商品与抢购入口的信息组织；本项目为独立设计，不使用其商标、图片或规则。
