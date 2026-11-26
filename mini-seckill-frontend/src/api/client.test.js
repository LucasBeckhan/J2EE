import test from "node:test";
import assert from "node:assert/strict";
import { request, createOrder, ApiError } from "./client.js";

/**
 * 执行 npm test 后由 Node 内置测试运行器加载本文件，不会打入 main.js 的网页依赖。
 * t.mock.method 临时替换全局 fetch，模拟网络响应，因此这些测试不需要启动 Java。
 * 每个测试上下文结束后恢复 mock；它们验证请求契约与错误映射，不等同于真实数据库并发测试。
 */
test("下单调用现有 Java 路径，使用 POST 且不自动重试", async (t) => {
  // 计数防止写请求在失败或成功后被重复发送；断言路径和方法保证与 Java Controller 对齐。
  let calls = 0;
  t.mock.method(globalThis, "fetch", async (url, options) => {
    calls++;
    assert.equal(url, "/api/products/2/seckill?userId=1001");
    assert.equal(options.method, "POST");
    return new Response(JSON.stringify({ id: 3 }), { status: 200 });
  });
  // deepEqual 比较 JSON 对象内容；Response 模拟的是浏览器 HTTP 响应，不是真正的订单落库。
  assert.deepEqual(await createOrder(2, "1001"), { id: 3 });
  assert.equal(calls, 1);
});

// assert.rejects 验证 Promise 以预期错误拒绝，避免 409 被当成普通成功 JSON 返回。
test("409 业务错误保留状态及后端中文提示", async (t) => {
  t.mock.method(
    globalThis,
    "fetch",
    async () =>
      new Response(JSON.stringify({ message: "该用户已经购买过此商品" }), {
        status: 409,
      }),
  );
  await assert.rejects(
    request("/products"),
    (error) =>
      error instanceof ApiError &&
      error.status === 409 &&
      error.message === "该用户已经购买过此商品",
  );
});

// 真实代理可能返回 HTML 502 页面，覆盖这个分支可防止 JSON 解析异常掩盖状态码。
test("代理返回 HTML 错误页时仍展示 HTTP 状态", async (t) => {
  t.mock.method(
    globalThis,
    "fetch",
    async () => new Response("<html>Bad gateway</html>", { status: 502 }),
  );
  await assert.rejects(
    request("/products"),
    (error) => error.status === 502 && error.message.includes("502"),
  );
});

// 没收到 HTTP 响应时只能表示结果未知，不能从本地 TypeError 推断订单一定没创建。
test("网络故障不能冒充下单失败或成功，不触发自动重试", async (t) => {
  let calls = 0;
  t.mock.method(globalThis, "fetch", async () => {
    calls++;
    throw new TypeError("offline");
  });
  await assert.rejects(
    createOrder(1, "1001"),
    (error) => error.status === 0 && error.message.includes("无法连接"),
  );
  assert.equal(calls, 1);
});

// 这里直接模拟 AbortError，验证错误转换；没有实际等待十秒，也没有证明后端事务被取消。
test("请求中止会提示查询订单确认结果", async (t) => {
  t.mock.method(globalThis, "fetch", async () => {
    throw new DOMException("timeout", "AbortError");
  });
  await assert.rejects(createOrder(1, "1001"), (error) =>
    error.message.includes("查询订单确认"),
  );
});
