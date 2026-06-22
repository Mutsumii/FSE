<template>
  <div class="portal">
    <section class="portal-hero">
      <p class="eyebrow">STOCK TRADING SYSTEM</p>
      <h1>股票交易系统 · 导航门户</h1>
      <p class="subtitle">选择要进入的子系统</p>
    </section>

    <section class="portal-grid">
      <router-link class="sys-card" to="/home">
        <span class="sys-icon">📈</span>
        <h2>网上信息发布</h2>
        <p>实时行情、K线图表、主力动向（本系统首页）</p>
        <span class="enter">进入首页 →</span>
      </router-link>

      <a class="sys-card" :href="accountSysUrl">
        <span class="sys-icon">👤</span>
        <h2>账户管理系统</h2>
        <p>工作人员后台、资金与证券账户业务办理</p>
        <span class="enter">前往登录 →</span>
      </a>

      <a class="sys-card" :href="tradeClientUrl">
        <span class="sys-icon">💹</span>
        <h2>交易客户端</h2>
        <p>投资者交易终端、委托下单、持仓查询</p>
        <span class="enter">前往登录 →</span>
      </a>

      <a class="sys-card" :href="tradeMgmtUrl">
        <span class="sys-icon">🛡️</span>
        <h2>交易管理系统</h2>
        <p>管理员后台、人工审核、风控与黑名单</p>
        <span class="enter">前往登录 →</span>
      </a>

      <a class="sys-card" :href="centralTradingUrl">
        <span class="sys-icon">🏛️</span>
        <h2>中央交易系统</h2>
        <p>中央撮合引擎、盘口监控、委托与成交运行面板</p>
        <span class="enter">进入系统 →</span>
      </a>
    </section>
  </div>
</template>

<script setup>
const accountSysUrl = 'http://localhost:5173/login'
const tradeClientUrl = 'http://localhost:8090/'
const tradeMgmtUrl = 'http://localhost:8081/'
const centralTradingUrl = 'http://localhost:8082/'
</script>

<style scoped>
.portal { max-width: 1080px; margin: 0 auto; }
.portal-hero { text-align: center; padding: 28px 0 36px; }
.eyebrow { color: #005aa6; font-weight: 600; letter-spacing: .12em; font-size: .8rem; margin: 0 0 8px; }
.portal-hero h1 { font-family: 'IBM Plex Sans', sans-serif; color: #1b1c1c; font-size: 2rem; margin: 0 0 8px; }
.portal-hero .subtitle { color: #666; margin: 0; }
.portal-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 20px; }
.sys-card {
  display: flex; flex-direction: column; gap: 8px;
  background: #fff; border: 1px solid #E8E8E8; border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
  padding: 24px; text-decoration: none; color: #1b1c1c;
  transition: transform .15s, box-shadow .15s, border-color .15s;
}
.sys-card:hover { transform: translateY(-3px); box-shadow: 0 6px 16px rgba(183,0,12,0.12); border-color: #b7000c; }
.sys-icon { font-size: 2rem; }
.sys-card h2 { font-family: 'IBM Plex Sans', sans-serif; font-size: 1.2rem; margin: 4px 0 0; color: #b7000c; }
.sys-card p { color: #666; font-size: .92rem; margin: 0; line-height: 1.5; flex: 1; }
.enter { color: #b7000c; font-weight: 700; font-size: .92rem; margin-top: 8px; }
</style>
