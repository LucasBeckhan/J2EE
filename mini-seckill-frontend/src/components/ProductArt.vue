<script setup>
// 本地图形不依赖第三方图片服务，离线开发也可正常展示。
// 图案只是装饰，商品名称、价格和库存始终取自后端数据。
// defineProps 是 script setup 编译宏：父组件用 :kind="..." 传入插图类型。
// kind 缺省为 box；组件没有自己的库存、订单或网络请求，是可复用的展示组件。
defineProps({ kind: { type: String, default: "box" } });
</script>

<template>
  <!-- viewBox 定义 320×230 的内部坐标系，实际显示大小由 CSS 容器决定。
       aria-hidden 表示装饰图不重复朗读；真正的商品名称在父组件标题中提供。 -->
  <svg viewBox="0 0 320 230" aria-hidden="true" class="product-art">
    <ellipse
      cx="160"
      cy="198"
      rx="81"
      ry="10"
      fill="currentColor"
      opacity=".08"
    />
    <!-- SVG g 将耳机的路径和矩形分组，统一应用描边样式。
         下方 v-if / v-else-if / v-else 组成一个分支链，每次只显示一种插图。 -->
    <g
      v-if="kind === 'headphones'"
      fill="none"
      stroke="#253341"
      stroke-width="17"
      stroke-linecap="round"
    >
      <path d="M99 125V94a61 61 0 0 1 122 0v31" />
      <path d="M110 61a61 61 0 0 1 100 0" stroke="#536879" stroke-width="10" />
      <rect
        x="84"
        y="111"
        width="34"
        height="66"
        rx="15"
        fill="#253341"
        stroke-width="5"
      />
      <rect
        x="202"
        y="111"
        width="34"
        height="66"
        rx="15"
        fill="#253341"
        stroke-width="5"
      />
      <path d="M111 125v36M209 125v36" stroke="#788d9e" stroke-width="4" />
    </g>
    <g
      v-else-if="kind === 'keyboard'"
      transform="translate(44 72) rotate(-7 115 50)"
    >
      <rect width="232" height="106" rx="13" fill="#46545e" />
      <rect x="5" y="4" width="222" height="94" rx="10" fill="#d6dedc" />
      <g fill="#f8faf9">
        <!-- 30 个键帽按十列三行分布：取余得到列号，整除取整得到行号，避免重复写 30 个矩形。 -->
        <rect
          v-for="i in 30"
          :key="i"
          :x="12 + ((i - 1) % 10) * 21"
          :y="12 + Math.floor((i - 1) / 10) * 20"
          width="17"
          height="16"
          rx="3"
        />
        <rect x="55" y="73" width="122" height="16" rx="3" />
        <rect x="12" y="73" width="36" height="16" rx="3" />
        <rect x="184" y="73" width="36" height="16" rx="3" />
      </g>
      <rect x="12" y="12" width="17" height="16" rx="3" fill="#ed714d" />
    </g>
    <g v-else-if="kind === 'cup'">
      <path
        d="M119 69h82l-8 115a13 13 0 0 1-13 12h-40a13 13 0 0 1-13-12z"
        fill="#789e8f"
      />
      <rect x="115" y="52" width="90" height="23" rx="9" fill="#294d40" />
      <rect x="129" y="44" width="62" height="11" rx="5" fill="#456e5c" />
      <path
        d="m136 87 5 83"
        stroke="#b2c9bc"
        stroke-width="5"
        stroke-linecap="round"
      />
      <text
        x="160"
        y="135"
        text-anchor="middle"
        fill="#e6ede7"
        font-size="12"
        letter-spacing="3"
      >
        DAILY
      </text>
    </g>
    <g v-else-if="kind === 'lamp'">
      <ellipse cx="159" cy="187" rx="51" ry="10" fill="#ca9d54" />
      <path d="M159 180V104" stroke="#9c783e" stroke-width="8" />
      <path d="M140 43h39l29 66h-98z" fill="#e9bd72" />
      <ellipse cx="159" cy="109" rx="49" ry="8" fill="#f8dfaa" />
      <path d="m146 50-15 49" stroke="#f7d89e" stroke-width="4" />
    </g>
    <g v-else>
      <path d="m160 51 67 36v76l-67 35-67-35V87z" fill="#e4a783" />
      <path d="m93 87 67 35 67-35-67-36z" fill="#f4c6a2" />
      <path d="M160 122v76" stroke="#c98967" stroke-width="2" />
      <path d="m128 68 67 36v30l-20 11v-31l-67-36z" fill="#ef754d" />
    </g>
  </svg>
</template>
