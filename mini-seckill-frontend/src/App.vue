<script setup>
/**
 * 前端主组件，包含两个视图和它们共用的状态：商品大厅、我的订单。
 *
 * 阅读顺序：状态声明 -> 派生状态 -> refresh -> switchUser -> buy -> 下方 template。
 * script setup 是 Vue 的组件语法；顶层变量/函数可直接在模板中使用，无需写 return。
 * ref 创建响应式状态：脚本里通过 .value 读写，模板通常自动解包，不写 .value。
 * computed 创建随依赖变化而重算的派生值，避免手动维护多份彼此可能不一致的数据。
 */
import { computed, onMounted, ref } from "vue";
import { createOrder, getOrders, getProducts } from "./api/client.js";
import ProductArt from "./components/ProductArt.vue";

// 商品的交易数据来自 API；此处只提供与名称匹配的展示分类，不参与库存判断。
// product?.name 使用可选链：订单已加载但商品查询失败时，不会因为缺少商品对象而报错。
// 当前后端没有 category/image 字段，所以这是临时的展示映射，未知商品使用 box 兜底。
function presentation(product) {
  const name = product?.name || "";
  if (name.includes("耳机"))
    return {
      kind: "headphones",
      category: "数码好物",
      subtitle: "沉浸聆听，让生活安静一点",
      tone: "blue",
    };
  if (name.includes("键盘"))
    return {
      kind: "keyboard",
      category: "数码好物",
      subtitle: "每一次敲击，都有好手感",
      tone: "mint",
    };
  if (name.includes("杯"))
    return {
      kind: "cup",
      category: "生活精选",
      subtitle: "随身一杯，装下日常的小美好",
      tone: "sage",
    };
  if (name.includes("灯"))
    return {
      kind: "lamp",
      category: "生活精选",
      subtitle: "一束暖光，点亮专注时刻",
      tone: "sand",
    };
  return {
    kind: "box",
    category: "生活精选",
    subtitle: "限量精选，发现日常好物",
    tone: "peach",
  };
}

// hash 是 URL 中 # 后面的部分，不会作为 HTTP 路径发送给 Java 服务。
// 打开 /#orders 时先显示订单页，其余 hash 按商品页处理。
const page = ref(location.hash === "#orders" ? "orders" : "products");
// 下面两个数组保存服务端事实；搜索/分类只生成派生列表，不删除原始商品。
const products = ref([]);
const orders = ref([]);
// userId 是已确认身份；userDraft 是输入框草稿，点击“切换用户”并校验后才会生效。
const userId = ref("1001");
const userDraft = ref("1001");
const userError = ref("");
const search = ref("");
const category = ref("全部商品");
// refreshing 表示正在读商品与订单；buyingId 非 null 表示某件商品正在提交与刷新结果。
// 二者分开：页面可显示具体哪个按钮“正在提交”，同时统一阻止用户切换身份或重复点击。
const refreshing = ref(false);
const buyingId = ref(null);
// loaded 区分“首屏还没完成请求”和“已经完成请求但确实没有数据”，防止初始空数组闪出空态。
const loaded = ref(false);
// 商品错误和订单错误独立保存，某个接口失败时仍可显示另一个接口已得到的数据。
const productsError = ref("");
const ordersError = ref("");
// notice 仅表示本次下单反馈，结构为 { type: 'success'|'error', message: 文本 }。
const notice = ref(null);
const categories = ["全部商品", "数码好物", "生活精选"];
// computed 会追踪访问到的 ref；任一依赖变化后，模板中的 disabled 状态自动更新。
const busy = computed(() => refreshing.value || buyingId.value !== null);
// 将当前用户订单转换为商品 ID 集合，Set.has 快速判断某个商品是否已买过。
// 这只是 UI 辅助；多开浏览器或绕过按钮后，仍需要后端唯一约束保证限购。
const purchasedIds = computed(
  () => new Set(orders.value.map((order) => order.productId)),
);
// 先按商品名称匹配，再按分类匹配；trim 去两端空格，toLowerCase 让英文搜索忽略大小写。
// 此处纯客户端过滤，不会额外请求后端，也不修改 products 的库存或排序。
const visibleProducts = computed(() =>
  products.value.filter((product) => {
    return (
      product.name.toLowerCase().includes(search.value.trim().toLowerCase()) &&
      (category.value === "全部商品" ||
        presentation(product).category === category.value)
    );
  }),
);
// 后端以“分”传金额；这里只做人民币格式化，例如 9900 -> ¥99.00，不计算订单成交价。
const money = (cents) =>
  new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    minimumFractionDigits: 2,
  }).format(cents / 100);
// 接口返回 LocalDateTime 字符串；仅移除 T 和小数秒，不做浏览器时区换算。
const date = (value) => (value ? value.replace("T", " ").slice(0, 19) : "—");
// 订单只有 productId，借助已加载商品查询名称；匹配不到时模板显示“商品 #ID”。
const findProduct = (id) => products.value.find((product) => product.id === id);

function navigate(target) {
  // 同步更新界面与地址栏：hash 会留下浏览器历史记录，支持返回上一视图。
  page.value = target;
  location.hash = target;
}
// 支持浏览器前进/后退；两个页面共用库存和订单状态，无需再次初始化整个应用。
window.addEventListener("hashchange", () => {
  page.value = location.hash === "#orders" ? "orders" : "products";
});

/**
 * 库存和订单分别加载、分别报错：商品接口失败不能伪装成“无商品”。
 * 忙碌时禁止切换用户，避免旧用户的响应写进新用户页面。
 * 只有读操作可手动重试；下单请求的超时结果必须先查订单确认。
 */
async function refresh() {
  // 防止连续点击“刷新”产生重叠请求；下单后的 refresh 仍可在 buyingId 非空时执行。
  if (refreshing.value) return;
  refreshing.value = true;
  // 两个独立 GET 并发执行，allSettled 等全部结束，即使其中一个失败也会保留另一个结果。
  // 与 Promise.all 不同，它不会因首个 rejected 直接抛出；结果顺序与传入顺序一致。
  const results = await Promise.allSettled([
    getProducts(),
    getOrders(userId.value),
  ]);
  if (results[0].status === "fulfilled") {
    // 成功时替换整个快照并清除旧错误；失败时保留旧数据，同时明确显示“未更新”。
    products.value = results[0].value;
    productsError.value = "";
  } else productsError.value = results[0].reason.message;
  if (results[1].status === "fulfilled") {
    orders.value = results[1].value;
    ordersError.value = "";
  } else ordersError.value = results[1].reason.message;
  loaded.value = true;
  // 本方法只访问 allSettled 的完成结果；各 API 失败已转换为上面的错误提示。
  refreshing.value = false;
}

/** 切换身份时清空上个用户订单，随后读取新用户订单，避免短暂显示他人的购买记录。 */
async function switchUser() {
  if (busy.value) return;
  // 用户 ID 使用字符串保存，防止 JS 数字精度丢失；本演示将长度限制到安全整数范围。
  const value = userDraft.value.trim();
  // 首位不能为 0，后续最多 14 位数字；这里只校验格式，不代表用户已认证或真实存在。
  if (!/^[1-9]\d{0,14}$/.test(value)) {
    userError.value = "请输入 1～15 位的正整数用户 ID";
    return;
  }
  userError.value = "";
  if (value === userId.value) return; // 未改变身份时不额外发请求。
  userId.value = value;
  orders.value = [];
  notice.value = null;
  await refresh();
}

/**
 * 一次下单：本地交互检查 -> POST -> 展示已确认结果 -> GET 刷新库存和订单。
 * async/await 表达异步依赖，不会让浏览器主线程一直阻塞等待网络。
 * 后端是同步下单，所以这里没有轮询消息队列或“排队中”的状态协议。
 */
async function buy(product) {
  // 与模板 disabled 条件保持对应；订单查询失败时先恢复订单状态，防止误导用户重复参与。
  if (
    busy.value ||
    product.stock <= 0 ||
    purchasedIds.value.has(product.id) ||
    ordersError.value
  )
    return;
  // 在第一次 await 前设置，后续点击进入 buy 时立即看见 busy=true。
  buyingId.value = product.id;
  notice.value = null;
  try {
    const order = await createOrder(product.id, userId.value);
    // 后端响应成功才显示创建成功；先保留结果，避免随后的查询失败覆盖已确认的事实。
    orders.value = [
      order,
      // 按订单 ID 去重后前插，确保界面不出现同一订单两次。
      ...orders.value.filter((item) => item.id !== order.id),
    ];
    notice.value = {
      type: "success",
      message: `订单 #${order.id} 创建成功！可在“我的订单”中查看。`,
    };
  } catch (error) {
    // status=0 代表未拿到可判断的 HTTP 结果；5xx 代表服务端/代理故障。
    // 这两类情况都不能证明事务未提交，因此提示查订单，不能自行恢复库存或自动重试 POST。
    notice.value = {
      type: "error",
      message:
        error.status === 0 || error.status >= 500
          ? `${error.message}。结果可能尚未确认，请刷新“我的订单”后再操作。`
          : error.message,
    };
  } finally {
    // 无论成功、售罄还是重复购买，都重新读取服务端库存，避免在前端自行减库存。
    await refresh();
    // 等刷新也结束后才解除忙碌；否则用户可能在旧库存/旧订单尚未更新时再次操作。
    buyingId.value = null;
  }
}

// 组件首次挂载到 DOM 后加载数据；切换两个视图不会重新挂载整个 App。
onMounted(refresh);
</script>

<template>
  <!-- 模板速读：{{ }} 插值显示文本；:属性 绑定表达式；@事件 绑定事件处理函数。
       v-if/v-else 按条件创建视图；v-for 渲染列表；ref="catalog" 暴露滚动目标 DOM。 -->
  <div class="site-shell">
    <div class="top-strip">
      <div class="container strip-inner">
        <span>好物不必等 · 每一份库存都值得期待</span><span>本地体验版</span>
      </div>
    </div>
    <!-- 顶部导航通过 hash 切换页面，刷新和前进/后退仍可定位我的订单。 -->
    <header class="site-header">
      <div class="container header-inner">
        <a href="#products" class="brand" @click="navigate('products')"
          ><span class="brand-mark" aria-hidden="true">ϟ</span
          ><span
            >即刻<span class="brand-light">秒杀</span
            ><small>MINI FLASH SALE</small></span
          ></a
        >
        <nav aria-label="主导航">
          <a
            href="#products"
            :class="{ active: page === 'products' }"
            :aria-current="page === 'products' ? 'page' : undefined"
            @click="navigate('products')"
            >秒杀大厅</a
          >
          <a
            href="#orders"
            :class="{ active: page === 'orders' }"
            :aria-current="page === 'orders' ? 'page' : undefined"
            @click="navigate('orders')"
            >我的订单 <span class="count">{{ orders.length }}</span></a
          >
        </nav>
        <div class="account">
          <span class="avatar" aria-hidden="true">U</span
          ><span
            ><small>当前体验用户</small><strong>{{ userId }}</strong></span
          >
        </div>
      </div>
    </header>

    <main class="container main-content">
      <!-- Banner 只在商品页出现，纯展示，不决定活动有效期或库存。
           @click.prevent 阻止跳转 #catalog 覆盖路由 hash，改为滚动到商品区域。 -->
      <section
        v-if="page === 'products'"
        class="hero"
        aria-labelledby="hero-title"
      >
        <div class="hero-copy">
          <span class="eyebrow">SMALL QUANTITY. GREAT FINDS.</span>
          <h1 id="hero-title">
            心动好物，<br />即刻开抢<span class="accent-dot">。</span>
          </h1>
          <p>精选日常好物，限量库存。<br />把喜欢带回家，就在这一刻。</p>
          <a
            class="hero-link"
            href="#catalog"
            @click.prevent="
              $refs.catalog.scrollIntoView({ behavior: 'smooth' })
            "
            >探索本场商品 <span aria-hidden="true">↘</span></a
          >
        </div>
        <div class="hero-art" aria-hidden="true">
          <div class="hero-orbit"></div>
          <span class="orbit-caption">限量 · 精选</span>
          <div class="hero-product"><ProductArt kind="headphones" /></div>
          <span class="hero-word">FLASH<br />SALE</span
          ><span class="hero-seal">每人每件<br /><b>限购 1 件</b></span>
        </div>
      </section>

      <!-- 草稿 ID 与已确认 ID 分开保存，输入过程中不会影响正在展示的订单。
           v-model 双向绑定输入草稿；@submit.prevent 阻止表单原生提交导致页面刷新。
           aria-invalid/aria-describedby 将校验错误关联到输入框，方便辅助技术读取。 -->
      <section class="experience-bar" aria-label="体验用户设置">
        <div>
          <strong>以不同用户参与抢购</strong>
          <p>每个用户每件商品限购一次，订单按用户分别展示。</p>
        </div>
        <form class="user-form" @submit.prevent="switchUser">
          <label for="user-id">用户 ID</label
          ><input
            id="user-id"
            v-model="userDraft"
            inputmode="numeric"
            maxlength="15"
            :disabled="busy"
            :aria-invalid="!!userError"
            :aria-describedby="userError ? 'user-error' : undefined"
          /><button
            class="button button-outline"
            :disabled="busy"
            type="submit"
          >
            切换用户
          </button>
        </form>
        <p v-if="userError" id="user-error" class="field-error" role="alert">
          {{ userError }}
        </p>
      </section>

      <!-- 服务端已确认的下单结果与读接口错误分开显示，避免刷新失败覆盖成功。
           role=status 用于操作反馈，role=alert 用于错误；这里的提示不替代实际订单数据。 -->
      <div v-if="notice" class="notice" :class="notice.type" role="status">
        <span>{{ notice.message }}</span
        ><button type="button" aria-label="关闭提示" @click="notice = null">
          ×
        </button>
      </div>
      <div v-if="productsError || ordersError" class="error-panel" role="alert">
        <div>
          <strong>部分数据暂未更新</strong>
          <p v-if="productsError">商品：{{ productsError }}</p>
          <p v-if="ordersError">订单：{{ ordersError }}。请刷新后继续抢购。</p>
        </div>
        <button class="button button-outline" :disabled="busy" @click="refresh">
          重新连接
        </button>
      </div>

      <!-- 商品页：v-if 切换 DOM，数据状态仍由上方同一个组件保存。
           aria-busy 提示当前正在更新；它本身不会禁止点击，禁用由 :disabled 实现。 -->
      <section
        v-if="page === 'products'"
        id="catalog"
        ref="catalog"
        class="catalog"
        aria-labelledby="catalog-title"
        :aria-busy="refreshing"
      >
        <div class="section-header">
          <div>
            <span class="eyebrow orange-text">THE CURRENT EDIT</span>
            <h2 id="catalog-title">
              本场精选 <span class="live-label">限量开抢</span>
            </h2>
          </div>
          <button class="text-button" :disabled="busy" @click="refresh">
            {{ refreshing ? "更新中…" : "刷新库存" }}
            <span aria-hidden="true">↻</span>
          </button>
        </div>
        <!-- 分类和关键词只修改响应式筛选条件，visibleProducts 自动重算。 -->
        <div class="filter-bar">
          <div class="category-list" role="group" aria-label="商品分类">
            <button
              v-for="item in categories"
              :key="item"
              :class="{ selected: category === item }"
              :aria-pressed="category === item"
              @click="category = item"
            >
              {{ item }}
            </button>
          </div>
          <label class="search"
            ><span aria-hidden="true">⌕</span
            ><input
              v-model="search"
              type="search"
              aria-label="搜索商品名称"
              placeholder="搜索心动好物"
          /></label>
        </div>
        <!-- 三种互斥状态：首次加载、加载后无匹配数据、正常商品列表。
             请求失败时读 productsError 展示错误，不把连接故障误标为“卖完”。 -->
        <div v-if="!loaded" class="empty-state" role="status">
          <span class="spinner"></span>
          <h3>正在准备好物</h3>
          <p>正在获取商品与订单信息…</p>
        </div>
        <div v-else-if="!visibleProducts.length" class="empty-state">
          <span class="empty-symbol" aria-hidden="true">⌕</span>
          <h3>{{ productsError ? "商品暂时无法加载" : "没有找到对应商品" }}</h3>
          <p>
            {{
              productsError
                ? "请检查后端连接，然后点击重新连接。"
                : "试试其他关键词或分类。"
            }}
          </p>
        </div>
        <!-- 前端禁用仅用于改善交互；最终限购和库存约束始终由后端保证。 -->
        <div v-else class="product-grid">
          <!-- :key 使用稳定的商品主键，Vue 可以正确复用对应 DOM，不能用库存等易变值作 key。 -->
          <article
            v-for="product in visibleProducts"
            :key="product.id"
            class="product-card"
          >
            <div class="product-image" :class="presentation(product).tone">
              <span class="product-tag">{{
                presentation(product).category
              }}</span
              ><ProductArt :kind="presentation(product).kind" /><span
                class="sold-stamp"
                v-if="product.stock <= 0"
                >已售罄</span
              >
            </div>
            <div class="product-content">
              <span class="product-kicker">LIMITED SELECTION</span>
              <h3>{{ product.name }}</h3>
              <p class="product-subtitle">
                {{ presentation(product).subtitle }}
              </p>
              <div class="price-line">
                <strong>{{ money(product.priceInCents) }}</strong
                ><span>秒杀价</span>
              </div>
              <div class="stock-line">
                <span>{{ product.stock > 0 ? "限量库存" : "本场已抢完" }}</span
                ><strong>剩余 {{ product.stock }} 件</strong>
              </div>
              <!-- 同时检查忙碌、售罄、已购买和订单未知；按钮文案按正在提交/已买/售罄排序。 -->
              <button
                class="button button-buy"
                :disabled="
                  busy ||
                  product.stock <= 0 ||
                  purchasedIds.has(product.id) ||
                  !!ordersError
                "
                @click="buy(product)"
              >
                {{
                  buyingId === product.id
                    ? "正在提交…"
                    : purchasedIds.has(product.id)
                      ? "已抢购"
                      : product.stock <= 0
                        ? "已售罄"
                        : "立即抢购"
                }}<span
                  v-if="
                    !busy && product.stock > 0 && !purchasedIds.has(product.id)
                  "
                  aria-hidden="true"
                  >＋</span
                >
              </button>
            </div>
          </article>
        </div>
        <p class="inventory-note">
          库存以提交时的服务端结果为准 · 每人每件商品限购 1 件
        </p>
      </section>

      <!-- 订单页与上方商品页互斥。顺序由后端 Repository 的 CreatedAtDesc 决定。 -->
      <section
        v-else
        class="orders-section"
        aria-labelledby="orders-title"
        :aria-busy="refreshing"
      >
        <div class="section-header">
          <div>
            <span class="eyebrow orange-text">YOUR GOOD FINDS</span>
            <h1 id="orders-title">
              我的订单 <span class="order-total">{{ orders.length }} 笔</span>
            </h1>
            <p class="section-description">
              用户 {{ userId }} 的抢购记录，成功下单一目了然。
            </p>
          </div>
          <button class="text-button" :disabled="busy" @click="refresh">
            {{ refreshing ? "更新中…" : "刷新订单" }}
            <span aria-hidden="true">↻</span>
          </button>
        </div>
        <div v-if="!orders.length" class="empty-state">
          <span class="empty-symbol" aria-hidden="true">▤</span>
          <h3>
            {{
              !loaded
                ? "正在加载订单"
                : ordersError
                  ? "暂时无法确认订单"
                  : "第一件好物，等你来抢"
            }}
          </h3>
          <p>
            {{
              ordersError
                ? "请重新连接后查看真实订单状态。"
                : "抢购成功后，你的订单会出现在这里。"
            }}
          </p>
          <button class="button button-buy" @click="navigate('products')">
            逛逛秒杀大厅
          </button>
        </div>
        <!-- 订单数据来自订单接口；商品名称/插图借助 productId 在商品列表中关联。
             找不到商品时仍保留订单 ID 和状态，不能因商品查询失败隐藏已存在的订单。 -->
        <div v-else class="order-list">
          <article v-for="order in orders" :key="order.id" class="order-card">
            <div class="order-card-head">
              <span
                >订单编号 <b>#{{ order.id }}</b></span
              ><time>{{ date(order.createdAt) }}</time>
            </div>
            <div class="order-card-body">
              <div
                class="order-thumb"
                :class="presentation(findProduct(order.productId)).tone"
              >
                <ProductArt
                  :kind="presentation(findProduct(order.productId)).kind"
                />
              </div>
              <div class="order-info">
                <h3>
                  {{
                    findProduct(order.productId)?.name ||
                    `商品 #${order.productId}`
                  }}
                </h3>
                <p>商品编号 {{ order.productId }} · 数量 1 件</p>
                <p>用户 {{ order.userId }}</p>
              </div>
              <!-- CREATED 仅翻译为“订单已创建”，不能当作支付成功；未知状态保留原文便于定位。 -->
              <span class="order-status">{{
                order.status === "CREATED" ? "订单已创建" : order.status
              }}</span>
            </div>
          </article>
        </div>
        <p class="inventory-note">
          当前支持订单创建与查询，支付和取消功能将在后续版本加入。
        </p>
      </section>

      <section class="rules" aria-label="抢购规则">
        <div class="rule-item">
          <span class="rule-icon" aria-hidden="true">01</span>
          <div>
            <h3>限量精选</h3>
            <p>数量有限，售完即止</p>
          </div>
        </div>
        <div class="rule-item">
          <span class="rule-icon" aria-hidden="true">02</span>
          <div>
            <h3>公平参与</h3>
            <p>每人每件限购一次</p>
          </div>
        </div>
        <div class="rule-item">
          <span class="rule-icon" aria-hidden="true">03</span>
          <div>
            <h3>结果可查</h3>
            <p>订单创建成功后即可查询</p>
          </div>
        </div>
      </section>
      <details class="help">
        <summary>体验说明与抢购规则</summary>
        <p>
          本项目使用演示用户 ID
          切换身份，无需真实登录。商品价格与库存由后端返回；提交后以订单查询结果为准。当前使用内存数据库，后端重启会清空订单并恢复演示库存。页面没有支付、真实活动场次或倒计时。
        </p>
      </details>
    </main>
    <footer class="container site-footer">
      <span class="footer-brand">即刻秒杀 <span>MINI FLASH SALE</span></span
      ><span>少一点犹豫，多一点喜欢。</span
      ><span>Java 学习项目 · 本地体验</span>
    </footer>
  </div>
</template>
