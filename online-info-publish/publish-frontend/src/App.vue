<template>
  <div id="app" class="app-shell">
    <header v-if="route.path !== '/'" class="topbar">
      <router-link class="brand" to="/home">网上信息发布</router-link>
      <router-link v-if="route.path !== '/'" class="ghost-btn back-nav" to="/">← 返回导航</router-link>

      <nav class="subsystem-links">
        <a :href="accountSysUrl" target="_blank" rel="noopener noreferrer">账户系统</a>
        <a :href="tradeClientUrl" target="_blank" rel="noopener noreferrer">交易客户端</a>
        <a :href="tradeMgmtUrl" target="_blank" rel="noopener noreferrer">交易管理</a>
      </nav>

      <div class="actions">
        <span v-if="userStore.globalUserId" class="role-pill">{{ roleLabel }}</span>
        <template v-if="!userStore.globalUserId">
          <a class="ghost-btn" :href="tradeClientLoginUrl">登录</a>
        </template>
        <button v-else class="ghost-btn" type="button" @click="handleLogout">退出</button>
      </div>
    </header>

    <main class="page-shell">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from './stores/user'

const accountSysUrl = 'http://localhost:5173'
const tradeClientUrl = 'http://localhost:8090'
const tradeMgmtUrl = 'http://localhost:8081'
const sharedPortalCookie = 'shared_investor_session'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
let sharedPortalTimer = null

const tradeClientLoginUrl = computed(() => {
  const targetPath = route.fullPath || '/'
  const returnUrl = new URL(targetPath, window.location.origin).toString()
  return `${tradeClientUrl}/?returnUrl=${encodeURIComponent(returnUrl)}`
})

const roleLabel = computed(() => {
  if (userStore.isPremiumVip) return 'PREMIUM_VIP'
  if (userStore.isStandard) return 'STANDARD'
  return 'GUEST'
})

const applyPortalLogin = () => {
  const params = new URLSearchParams(window.location.search)
  const queryFundAccNo = params.get('fundAccNo') || ''
  const queryRole = params.get('role') || 'STANDARD'
  const sharedSession = readSharedPortalSession()
  const fundAccNo = queryFundAccNo || sharedSession?.fundAccountNo || ''
  if (!fundAccNo) return

  userStore.setGlobalUserId(fundAccNo)
  userStore.setRole(queryRole || sharedSession?.role || 'STANDARD')
  userStore.setToken('portal-session')

  params.delete('loggedIn')
  params.delete('fundAccNo')
  params.delete('secAccNo')
  params.delete('role')
  params.delete('investorName')
  const query = params.toString()
  window.history.replaceState({}, '', `${window.location.pathname}${query ? `?${query}` : ''}`)

}

const handleLogout = () => {
  userStore.logout()
  clearSharedPortalSession()
  router.push('/')
}

onMounted(() => {
  applyPortalLogin()
  sharedPortalTimer = window.setInterval(syncFromSharedPortalSession, 1200)
})

onBeforeUnmount(() => {
  if (sharedPortalTimer) {
    window.clearInterval(sharedPortalTimer)
    sharedPortalTimer = null
  }
})

const readSharedPortalSession = () => {
  const cookies = document.cookie ? document.cookie.split('; ') : []
  const entry = cookies.find((item) => item.startsWith(`${sharedPortalCookie}=`))
  if (!entry) return null
  try {
    return JSON.parse(decodeURIComponent(entry.substring(sharedPortalCookie.length + 1)))
  } catch {
    return null
  }
}

const clearSharedPortalSession = () => {
  document.cookie = `${sharedPortalCookie}=; path=/; max-age=0; SameSite=Lax`
}

const syncFromSharedPortalSession = () => {
  const sharedSession = readSharedPortalSession()
  if (!sharedSession?.fundAccountNo) {
    if (userStore.globalUserId) {
      userStore.logout()
    }
    return
  }

  if (userStore.globalUserId !== sharedSession.fundAccountNo || userStore.role !== (sharedSession.role || 'STANDARD')) {
    userStore.setGlobalUserId(sharedSession.fundAccountNo)
    userStore.setRole(sharedSession.role || 'STANDARD')
    userStore.setToken('portal-session')
  }
}

window.addEventListener('storage', (event) => {
  if (event.key === 'publish_token' && !event.newValue) {
    userStore.logout()
  }
})
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;600;700&family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@400;500;600&display=swap');

.app-shell {
  min-height: 100vh;
  background: #F8F9FA;
  color: #1b1c1c;
  font-family: 'Inter', sans-serif;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid #E8E8E8;
  background: #ffffff;
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  font-size: 1.25rem;
  font-weight: 700;
  color: #b7000c;
  font-family: 'IBM Plex Sans', sans-serif;
  text-decoration: none;
}

.subsystem-links {
  display: flex;
  gap: 12px;
}

.subsystem-links a {
  color: #666666;
  text-decoration: none;
  font-size: 0.95rem;
  font-weight: 500;
  transition: color 0.2s;
}

.subsystem-links a:hover {
  color: #b7000c;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-pill,
.primary-btn,
.ghost-btn {
  border-radius: 4px;
  padding: 8px 12px;
  border: 1px solid transparent;
  font-size: 0.92rem;
  cursor: pointer;
}

.role-pill {
  background: #ffdad5;
  border-color: #ffb4aa;
  color: #410001;
  font-weight: 600;
}

.primary-btn {
  background: #b7000c;
  color: #ffffff;
  font-weight: 700;
}

.ghost-btn {
  background: #fbf9f8;
  color: #1b1c1c;
  border-color: #E8E8E8;
}

.ghost-btn:hover {
  background: #f6f3f2;
}

.page-shell {
  padding: 24px;
}
</style>
