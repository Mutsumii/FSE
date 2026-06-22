<template>
  <div class="stock-detail">
    <router-link to="/home" class="back-link">← 返回行情列表</router-link>
    <!-- 顶部信息卡：所有角色（含 GUEST）均可见 -->
    <header class="quote-header card">
      <div class="header-main">
        <div class="title">
          <span class="stock-name">{{ quote?.stockName || '—' }}</span>
          <span class="stock-code">{{ stockCode }}</span>
          <span v-if="quote?.status === 1" class="suspended-badge">停牌</span>
        </div>
        <div class="price-line">
          <span class="last-price" :class="changeClass">{{ displayPrice }}</span>
          <span class="change-amount" :class="changeClass">{{ changeAmount }}</span>
          <span class="change-rate" :class="changeClass">{{ rateDisplay }}</span>
        </div>
      </div>
      <div class="header-metrics">
        <div class="metric">
          <span class="m-label">昨收</span>
          <span class="m-value">{{ fmtPrice(quote?.yesterdayClose) }}</span>
        </div>
        <div class="metric">
          <span class="m-label">状态</span>
          <span class="m-value">{{ quote?.status === 1 ? '停牌' : '正常' }}</span>
        </div>
      </div>
    </header>

    <!-- 行情拉取失败提示 -->
    <div v-if="errorMsg" class="error-bar">{{ errorMsg }}</div>

    <!-- GUEST：仅价格，引导登录解锁 -->
    <div v-if="isGuest" class="guest-hint card">
      登录并绑定证券账户后，可查看买一/卖一盘口、主力动向与 K 线图表。
    </div>

    <!-- STANDARD+：左侧 K 线图 + 右侧盘口/主力面板 -->
    <div v-else class="detail-body">
      <section class="chart-area card" :class="{ blurred: showUpgrade }">
        <KLineChart
          :klineData="klineData"
          :period="period"
          @change-period="onPeriodChange"
          @need-upgrade="onNeedUpgrade"
        />
      </section>

      <aside class="side-panels">
        <!-- 盘口面板 -->
        <div class="panel card orderbook-panel">
          <h3 class="panel-title">盘口</h3>
          <div class="ob-row ask">
            <div class="ob-bar ask-bar" :style="{ width: askBarWidth }"></div>
            <span class="label">卖一</span>
            <span class="ask-price">{{ fmtPrice(quote?.askPrice) }}</span>
            <span class="ask-volume">{{ quote?.askVolume ?? '—' }}</span>
          </div>
          <div class="ob-row bid">
            <div class="ob-bar bid-bar" :style="{ width: bidBarWidth }"></div>
            <span class="label">买一</span>
            <span class="bid-price">{{ fmtPrice(quote?.bidPrice) }}</span>
            <span class="bid-volume">{{ quote?.bidVolume ?? '—' }}</span>
          </div>
        </div>

        <!-- 主力动向面板 -->
        <div class="panel card trader-panel">
          <h3 class="panel-title">主力动向</h3>
          <div class="trader-row top-buyer">
            <span class="label">大买家</span>
            <span class="account">{{ quote?.topBuyer?.account || '—' }}</span>
            <span class="qty">{{ quote?.topBuyer?.qty ?? '—' }}</span>
          </div>
          <div class="trader-row top-seller">
            <span class="label">大卖家</span>
            <span class="account">{{ quote?.topSeller?.account || '—' }}</span>
            <span class="qty">{{ quote?.topSeller?.qty ?? '—' }}</span>
          </div>
        </div>
      </aside>
    </div>

    <!-- VIP 升级弹窗（STANDARD 点击非 1D 周期时由 KLineChart 触发 need-upgrade） -->
    <UpgradeModal :visible="showUpgrade" @close="showUpgrade = false" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getQuote, getKLine } from '../api/market'
import KLineChart from '../components/KLineChart.vue'
import UpgradeModal from '../components/UpgradeModal.vue'

const route = useRoute()
const userStore = useUserStore()

// 路由 /stock/:code → 取股票代码
const stockCode = route.params.code

const quote = ref(null)          // QuoteDTO：实时行情 + 盘口 + 主力
const klineData = ref([])        // KLineDTO[]：传给 KLineChart
const period = ref('1D')         // 当前 K 线周期，默认日线
const showUpgrade = ref(false)   // 是否显示 VIP 升级弹窗
const errorMsg = ref('')         // 行情拉取失败提示
let timer = null                 // 5 秒轮询定时器句柄

// 直接读取 role（GUEST/STANDARD/PREMIUM_VIP），不依赖 F1 尚未实现的 getter
const isGuest = computed(() => userStore.role === 'GUEST')

// 涨跌着色：changeRate 形如 "+1.20%" / "-0.80%"
const changeClass = computed(() => {
  const r = quote.value?.changeRate
  if (!r) return ''
  const s = String(r).trim()
  if (s.startsWith('-')) return 'down'
  if (s.startsWith('+')) return 'up'
  return 'flat'
})

const displayPrice = computed(() => fmtPrice(quote.value?.lastPrice))

// 涨跌额 = 最新价 − 昨收
const changeAmount = computed(() => {
  const lp = quote.value?.lastPrice
  const yc = quote.value?.yesterdayClose
  if (lp == null || yc == null) return ''
  const d = Number(lp) - Number(yc)
  return (d >= 0 ? '+' : '') + d.toFixed(2)
})

const rateDisplay = computed(() => {
  const r = quote.value?.changeRate
  return r ? `(${r})` : ''
})

// 盘口挂单量条形长度（按买/卖一中的较大量归一化）
const maxVol = computed(() => {
  const a = Number(quote.value?.askVolume) || 0
  const b = Number(quote.value?.bidVolume) || 0
  return Math.max(a, b, 1)
})
const askBarWidth = computed(
  () => `${Math.min(100, ((Number(quote.value?.askVolume) || 0) / maxVol.value) * 100)}%`
)
const bidBarWidth = computed(
  () => `${Math.min(100, ((Number(quote.value?.bidVolume) || 0) / maxVol.value) * 100)}%`
)

function fmtPrice(v) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 拉取实时行情（盘口/主力字段是否返回由后端按角色屏蔽）
async function fetchQuote() {
  try {
    quote.value = await getQuote(stockCode)
    errorMsg.value = ''
  } catch (e) {
    errorMsg.value = '行情获取失败，正在重试…'
  }
}

// 拉取 K 线数据
async function fetchKline(p = period.value) {
  try {
    klineData.value = await getKLine(stockCode, p)
  } catch (e) {
    // K 线获取失败不致命，保留上一次数据
  }
}

// KLineChart 切换周期 → 重新拉取对应周期数据
function onPeriodChange(p) {
  period.value = p
  fetchKline(p)
}

// KLineChart 检测到越权（非 VIP 点击非 1D 周期）→ 弹升级窗
function onNeedUpgrade() {
  showUpgrade.value = true
}

onMounted(() => {
  fetchQuote()                       // 立即拉一次，避免首屏空白
  if (!isGuest.value) fetchKline('1D')
  timer = setInterval(fetchQuote, 5000)  // 每 5 秒轮询
})

onUnmounted(() => {
  // 清理定时器，防止内存泄漏 / 页面切走后仍在请求
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
/* ===== 主题色：浅色卡片风格（参照样例），集中此处便于团队统一替换 ===== */
.back-link {
  display: inline-block;
  margin-bottom: 12px;
  color: #666;
  text-decoration: none;
  font-size: 0.95rem;
}
.back-link:hover { color: #b7000c; }

.stock-detail {
  --bg: #f5f6fa;
  --card-bg: #ffffff;
  --border: #eef0f3;
  --text: #1f2329;
  --text-dim: #8a8f99;
  --up: #f5384e;            /* 涨/买盘：红（A 股习惯） */
  --down: #16b384;          /* 跌/卖盘：绿 */
  --up-bg: rgba(245, 56, 78, 0.10);
  --down-bg: rgba(22, 179, 132, 0.10);

  background: var(--bg);
  color: var(--text);
  min-height: 100%;
  padding: 16px;
  box-sizing: border-box;
}

.card {
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
}

/* ===== 顶部信息卡 ===== */
.quote-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  padding: 16px 20px;
}
.title {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.stock-name { font-size: 22px; font-weight: 700; }
.stock-code { font-size: 13px; color: var(--text-dim); }
.suspended-badge {
  margin-left: 4px;
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 4px;
  background: #fff3e0;
  color: #fa8c16;
}
.price-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: 6px;
}
.last-price { font-size: 30px; font-weight: 700; line-height: 1; }
.change-amount, .change-rate { font-size: 15px; font-weight: 500; }

.header-metrics {
  display: flex;
  gap: 28px;
}
.metric { display: flex; flex-direction: column; gap: 4px; min-width: 64px; }
.m-label { font-size: 12px; color: var(--text-dim); }
.m-value { font-size: 15px; font-weight: 600; font-variant-numeric: tabular-nums; }

/* 涨跌通用色 */
.up { color: var(--up); }
.down { color: var(--down); }
.flat { color: var(--text-dim); }

.error-bar {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--up-bg);
  color: var(--up);
  font-size: 14px;
}

.guest-hint {
  margin-top: 16px;
  padding: 32px;
  text-align: center;
  color: var(--text-dim);
}

/* ===== 主体：左图 + 右面板 ===== */
.detail-body {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  margin-top: 16px;
}
@media (max-width: 900px) {
  .detail-body { grid-template-columns: 1fr; }
}

.chart-area {
  min-height: 420px;
  transition: filter 0.2s ease;
}
.chart-area.blurred {
  filter: blur(5px);
  pointer-events: none;
}

.side-panels { display: flex; flex-direction: column; gap: 16px; }
.panel { padding: 14px 16px; }
.panel-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

/* ===== 盘口（买一/卖一 + 挂单量条） ===== */
.ob-row {
  position: relative;
  display: grid;
  grid-template-columns: 44px 1fr auto;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 8px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.ob-bar {
  position: absolute;
  top: 4px;
  bottom: 4px;
  right: 0;
  border-radius: 3px;
  z-index: 0;
}
.ask-bar { background: var(--down-bg); }
.bid-bar { background: var(--up-bg); }
.ob-row > .label,
.ob-row > span { position: relative; z-index: 1; }
.ob-row .label { color: var(--text-dim); }
.ob-row .ask-price,
.ob-row .bid-price { text-align: right; font-weight: 600; }
.ob-row .ask-price { color: var(--down); }
.ob-row .bid-price { color: var(--up); }
.ob-row .ask-volume,
.ob-row .bid-volume { text-align: right; min-width: 56px; color: var(--text); }

/* ===== 主力动向 ===== */
.trader-row {
  display: grid;
  grid-template-columns: 64px 1fr auto;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 8px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.trader-row .label { color: var(--text-dim); }
.trader-row.top-buyer .account { color: var(--up); }
.trader-row.top-seller .account { color: var(--down); }
.trader-row .qty { text-align: right; min-width: 56px; }
</style>
