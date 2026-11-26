/**
 * 统一 HTTP 入口：设置超时、解析错误响应，并保留 HTTP 状态供业务层判断。
 * 不自动重试 POST：服务端可能已经完成下单，自动重试会让用户误判结果。
 */
export class ApiError extends Error {
  // Error 自带 message/stack；补上 status 供 App.vue 区分 409 冲突、5xx 故障和网络不确定状态。
  // 0 是本地约定，不是真实 HTTP 状态码，表示没有获得可用的 HTTP 状态判断。
  constructor(message, status = 0) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

/**
 * 发起一次 API 请求，成功时返回解析后的 JSON，失败时抛出统一的 ApiError。
 * path 为不含 /api 的业务路径；options 可传 method 等 fetch 配置。
 * 使用相对地址 /api，浏览器只访问前端同源地址，再由 Vite/Nginx 转发到 Java。
 */
export async function request(path, options = {}) {
  // AbortController 的 signal 传给 fetch，10 秒到期时通知浏览器中止等待。
  // 中止浏览器请求不等于撤销后端事务，服务端可能已经成功创建了订单。
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`/api${path}`, {
      // 展开调用者配置，随后统一使用本次超时控制器的 signal。
      ...options,
      signal: controller.signal,
    });
    // 代理故障可能返回 HTML，不能直接 response.json() 掩盖真正的 HTTP 错误。
    // 响应体是流，读取一次后不能再直接 response.json()；所以统一读文本后自行 JSON.parse。
    const text = await response.text();
    let data;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = null;
    }
    // fetch 收到 404/409/500 时本身不会 reject，必须显式检查 ok（状态为 200～299）。
    if (!response.ok) {
      throw new ApiError(
        // 优先沿用 Java ApiExceptionHandler 返回的 message；HTML 代理错误页则退回状态提示。
        data?.message || `服务暂不可用（HTTP ${response.status}）`,
        response.status,
      );
    }
    // 当前三个接口都应返回 JSON；null/空响应/非 JSON 不视为有效成功结果。
    // 这里不做完整字段 schema 校验；后续扩展接口时可以增加独立的数据校验层。
    if (data === null) throw new ApiError("接口未返回有效数据，请稍后刷新");
    return data;
  } catch (error) {
    // 保留前面已构造的业务错误，否则会把“售罄 409”错误地改写成“无法连接”。
    if (error instanceof ApiError) throw error;
    if (error.name === "AbortError")
      throw new ApiError("请求超时，请查询订单确认结果");
    throw new ApiError("无法连接服务，请检查后端是否启动");
  } finally {
    // 成功或失败都撤销定时器，避免请求早已结束后还触发无意义的 abort。
    clearTimeout(timeout);
  }
}

// URL 参数先编码；页面只传入经过正整数校验的演示用户 ID。
// GET：读取商品列表，返回 [{ id, name, priceInCents, stock }, ...]。
export const getProducts = () => request("/products");
// GET：读取指定演示用户的订单，返回 [{ id, userId, productId, status, createdAt }, ...]。
export const getOrders = (userId) =>
  request(`/users/${encodeURIComponent(userId)}/orders`);
// POST：路径参数是商品，查询参数是用户；不发送 JSON body，与当前 Controller 的签名对应。
// 成功返回一个订单对象；HTTP 409 通常表示已购买或售罄，不会触发自动重试。
export const createOrder = (productId, userId) =>
  request(
    `/products/${encodeURIComponent(productId)}/seckill?userId=${encodeURIComponent(userId)}`,
    { method: "POST" },
  );
