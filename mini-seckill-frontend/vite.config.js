import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

// 浏览器请求前端的 /api，Vite 再转发给 Java 服务，开发阶段无需放开后端 CORS。
// 生产环境由 deploy/nginx.conf 提供同样的反向代理，前后端仍然独立构建、独立部署。
export default defineConfig(({ mode }) => {
  // mode 为当前开发/构建模式；从工作目录读取 .env、.env.local 等匹配配置。
  // 第三个参数空字符串允许在“配置文件进程”中读取 API_PROXY_TARGET；不是把所有变量公开到网页。
  const env = loadEnv(mode, process.cwd(), "");
  const proxy = {
    "/api": {
      target: env.API_PROXY_TARGET || "http://127.0.0.1:8080",
      // 转发时将 Host 调整为目标服务器；保留原始 /api 路径，和 Java Controller 保持一致。
      changeOrigin: true,
    },
  };
  return {
    // 插件把 .vue 文件中的 script/template 编译成模块，并支持开发热更新。
    plugins: [vue()],
    // 只监听本机；strictPort 避免端口占用时悄悄改成 5174，让文档地址与实际地址一致。
    server: { host: "127.0.0.1", port: 5173, strictPort: true, proxy },
    // preview 用于本地查看 dist 构建产物，不是生产服务器；正式部署使用 Nginx 代理。
    preview: { host: "127.0.0.1", port: 4173, strictPort: true, proxy },
  };
});
