<template>
  <div class="portal">
    <section class="hero-card">
      <div>
        <p class="eyebrow">信息发布 / 市场数据</p>
        <h1>实时行情、指数与个股详情<br>一站式浏览</h1>
        <p class="subtitle">支持代码与拼音检索，默认展示大盘指数与5秒轮询行情。</p>
      </div>

      <div class="search-panel">
        <label class="search-label" for="stockSearch">股票搜索</label>
        <div class="search-row">
          <input
            id="stockSearch"
            v-model="keyword"
            type="text"
            placeholder="输入股票代码 / 拼音首字母"
            @keyup.enter="handleSearch"
          />
          <button type="button" class="primary-btn" @click="handleSearch">搜索</button>
        </div>
        <p class="hint">例如：600519、茅台、GZMT</p>

        <ul v-if="searchResults.length" class="search-result-list">
          <li
            v-for="item in searchResults"
            :key="item.stock_code || item.stockCode"
            class="search-result-item"
            @click="goToStock(item.stock_code || item.stockCode)"
          >
            <strong>{{ item.stock_code || item.stockCode }}</strong>
            <span>{{ item.stock_name || item.stockName }}</span>
          </li>
        </ul>
        <p v-else-if="!isSearching" class="empty-tip">暂无搜索结果，请输入股票代码或简称。</p>
      </div>
    </section>

    <section class="dashboard-grid">
      <article class="panel-card">
        <div class="panel-heading">
          <h2>大盘指数</h2>
          <span>Mock 数据</span>
        </div>
        <div class="index-grid">
          <div v-for="index in indices" :key="index.name" class="index-card">
            <p>{{ index.name }}</p>
            <strong>{{ index.value }}</strong>
            <small :class="index.trend >= 0 ? 'up' : 'down'">{{ formatRate(index.trend) }}</small>
          </div>
        </div>
      </article>

      <article class="panel-card">
        <div class="panel-heading">
          <h2>实时行情</h2>
          <span>每 5 秒刷新</span>
        </div>

        <div v-if="isLoadingQuotes" class="loading-text">正在同步行情...</div>

        <div v-else class="table-scroll">
        <table class="quote-table">
          <thead>
            <tr>
              <th>代码</th>
              <th>名称</th>
              <th>最新价</th>
              <th>涨跌幅</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in quotes"
              :key="item.stock_code"
              class="quote-row"
              @click="goToStock(item.stock_code)"
            >
              <td>{{ item.stock_code }}</td>
              <td>{{ item.stock_name }}</td>
              <td>{{ formatPrice(item.last_price) }}</td>
              <td :class="item.change_rate >= 0 ? 'up' : 'down'">{{ formatRate(item.change_rate) }}</td>
            </tr>
          </tbody>
        </table>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getQuote, searchStock } from '../api/market'

const router = useRouter()

const keyword = ref('')
const searchResults = ref([])
const quotes = ref([])
const stockCodes = ref([])  // 缓存股票列表，首次加载后不再重新拉
const isSearching = ref(false)
const isLoadingQuotes = ref(false)

const indices = ref([
  { name: '上证指数', value: '3,132.54', trend: 0.14 },
  { name: '深证成指', value: '10,887.20', trend: -0.36 }
])

const defaultQuotes = [
  { stock_code: '600519', stock_name: '贵州茅台', last_price: 1680.5, change_rate: 1.2 },
  { stock_code: '000001', stock_name: '平安银行', last_price: 12.35, change_rate: -0.23 },
  { stock_code: '000858', stock_name: '五粮液', last_price: 166.9, change_rate: 0.45 },
  { stock_code: '300750', stock_name: '宁德时代', last_price: 438.4, change_rate: -0.17 },
  { stock_code: '600036', stock_name: '招商银行', last_price: 38.50, change_rate: 0.0 },
  { stock_code: '601318', stock_name: '中国平安', last_price: 45.60, change_rate: 0.0 }
]

const formatPrice = (value) => Number(value || 0).toFixed(2)

const formatRate = (value) => {
  const numeric = Number(value || 0)
  return `${numeric >= 0 ? '+' : ''}${numeric.toFixed(2)}%`
}

const parseChangeRate = (val) => {
  if (val == null) return 0
  // API 返回 "+1.20%" 格式，去掉 % 再转数字
  const s = String(val).replace('%', '')
  return Number(s) || 0
}

const normalizeQuote = (item, fallbackCode) => {
  const code = item.stock_code || item.stockCode || fallbackCode

  return {
    stock_code: String(code),
    stock_name: item.stock_name || item.stockName || '—',
    last_price: Number(item.last_price ?? item.lastPrice ?? 0),
    change_rate: parseChangeRate(item.change_rate ?? item.changeRate)
  }
}

const loadQuotes = async () => {
  // 仅首次加载显示loading，后续静默刷新
  if (!quotes.value.length) {
    isLoadingQuotes.value = true
  }

  try {
    // 仅首次拉取全量股票列表
    if (!stockCodes.value.length) {
      const allStocks = await searchStock('')
      const stockList = Array.isArray(allStocks) ? allStocks : allStocks?.data || []
      stockCodes.value = stockList.map(s => ({ code: s.stockCode || s.stock_code, name: s.stockName || s.stock_name || '—' }))
    }

    if (!stockCodes.value.length) {
      quotes.value = defaultQuotes
      return
    }

    // 并行拉取所有行情
    const results = await Promise.allSettled(
      stockCodes.value.map(item => getQuote(item.code).then(q => normalizeQuote(q, item.code)).catch(() => ({
        stock_code: item.code,
        stock_name: item.name,
        last_price: 0,
        change_rate: 0
      })))
    )
    quotes.value = results.map(r => r.status === 'fulfilled' ? r.value : { stock_code: '', stock_name: '—', last_price: 0, change_rate: 0 })
  } finally {
    isLoadingQuotes.value = false
  }
}

const handleSearch = async () => {
  const value = keyword.value.trim()

  if (!value) {
    searchResults.value = []
    return
  }

  isSearching.value = true

  try {
    const response = await searchStock(value)
    const data = Array.isArray(response) ? response : response?.data || []
    searchResults.value = data
  } catch {
    searchResults.value = defaultQuotes.filter((item) =>
      item.stock_code.includes(value) || item.stock_name.includes(value)
    )
  } finally {
    isSearching.value = false
  }
}

const goToStock = (code) => {
  if (!code) return
  router.push({ name: 'StockDetail', params: { code } })
}

let timer = null

onMounted(() => {
  loadQuotes()
  timer = window.setInterval(loadQuotes, 5000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.portal {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.hero-card,
.panel-card {
  background: #FFFFFF;
  border: 1px solid #E8E8E8;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.hero-card {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 18px;
  align-items: start;
}

.eyebrow {
  font-size: 0.82rem;
  color: #005aa6;
  font-weight: 600;
}

h1 {
  margin: 6px 0 10px;
  color: #1b1c1c;
  font-family: 'IBM Plex Sans', sans-serif;
  font-size: 2rem;
  line-height: 1.2;
}

.subtitle {
  color: #666666;
  max-width: 560px;
}

.search-panel {
  background: #f6f3f2;
  border: 1px solid #E8E8E8;
  border-radius: 8px;
  padding: 14px;
}

.search-label {
  display: block;
  color: #1b1c1c;
  font-weight: 500;
  margin-bottom: 8px;
}

.search-row {
  display: flex;
  gap: 8px;
}

.search-row input {
  flex: 1;
  border-radius: 4px;
  border: 1px solid #E8E8E8;
  padding: 10px 12px;
  background: #ffffff;
  color: #1b1c1c;
}

.primary-btn {
  border-radius: 4px;
  background: #b7000c;
  color: #ffffff;
  border: 0;
  padding: 10px 14px;
  font-weight: 600;
  cursor: pointer;
}

.hint,
.empty-tip,
.loading-text {
  color: #999999;
  font-size: 0.92rem;
  margin-top: 8px;
}

.search-result-list {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.search-result-item {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid #E8E8E8;
  border-radius: 4px;
  padding: 10px;
  cursor: pointer;
  background: #ffffff;
  color: #1b1c1c;
}

.search-result-item:hover {
  background: #f6f3f2;
}

.search-result-item strong {
  font-family: 'JetBrains Mono', monospace;
  color: #005aa6;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 24px;
}

.panel-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-heading h2 {
  margin: 0;
  color: #1b1c1c;
  font-size: 1.2rem;
  font-family: 'IBM Plex Sans', sans-serif;
}

.panel-heading span {
  color: #666666;
  font-size: 0.9rem;
}

.index-grid {
  display: grid;
  gap: 12px;
}

.index-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border: 1px solid #E8E8E8;
  border-radius: 8px;
  padding: 12px;
  background: #fbf9f8;
}

.index-card p {
  color: #666666;
  font-size: 0.95rem;
}

.index-card strong {
  color: #1b1c1c;
  font-family: 'JetBrains Mono', monospace;
  font-size: 1.4rem;
}

.table-scroll {
  max-height: 420px;
  overflow-y: auto;
}

.quote-table {
  width: 100%;
  border-collapse: collapse;
}

.quote-table th,
.quote-table td {
  text-align: left;
  padding: 10px 8px;
  border-bottom: 1px solid #E8E8E8;
}

.quote-table th {
  color: #666666;
  font-weight: 600;
  background: #f6f3f2;
  padding: 12px 8px;
}

.quote-table td {
  font-family: 'JetBrains Mono', monospace;
  color: #1b1c1c;
}

.quote-row {
  cursor: pointer;
  transition: background-color 0.2s;
}

.quote-row:hover {
  background: rgba(0, 90, 166, 0.05); /* tertiary/5 */
}

.up { color: #E60012 !important; }
.down { color: #00A650 !important; }

@media (max-width: 980px) {
  .hero-card,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
