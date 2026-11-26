import { createApp } from "vue";
import App from "./App.vue";
import "./style.css";

// 浏览器先加载 index.html，再通过其中的 module 脚本进入本文件。
// App.vue 是根组件，style.css 是全站样式；Vite 负责将单文件组件编译成浏览器可执行的代码。
// createApp 创建 Vue 应用，mount 将它挂到 index.html 中 id="app" 的空容器。
// Vue 只接管前端容器；所有库存与订单事实都由 Java API 返回。
createApp(App).mount("#app");
