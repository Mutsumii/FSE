import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const STORAGE_KEY = {
  token: 'publish_token',
  role: 'publish_role',
  globalUserId: 'publish_global_user_id'
}

const safeStorage = {
  get(key) {
    try {
      return localStorage.getItem(key)
    } catch {
      return null
    }
  },
  set(key, value) {
    try {
      localStorage.setItem(key, value)
    } catch {
      // ignore storage failures in non-browser environments
    }
  },
  remove(key) {
    try {
      localStorage.removeItem(key)
    } catch {
      // ignore storage failures in non-browser environments
    }
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(safeStorage.get(STORAGE_KEY.token) || '')
  const role = ref(safeStorage.get(STORAGE_KEY.role) || 'GUEST')
  const globalUserId = ref(safeStorage.get(STORAGE_KEY.globalUserId) || '')

  const isGuest = computed(() => !token.value || role.value === 'GUEST')
  const isStandard = computed(() => role.value === 'STANDARD')
  const isPremiumVip = computed(() => role.value === 'PREMIUM_VIP')

  function setToken(value = '') {
    token.value = value
    if (value) {
      safeStorage.set(STORAGE_KEY.token, value)
    } else {
      safeStorage.remove(STORAGE_KEY.token)
    }
  }

  function setRole(value = 'GUEST') {
    role.value = value
    safeStorage.set(STORAGE_KEY.role, value)
  }

  function setGlobalUserId(value = '') {
    globalUserId.value = value
    if (value) {
      safeStorage.set(STORAGE_KEY.globalUserId, value)
    } else {
      safeStorage.remove(STORAGE_KEY.globalUserId)
    }
  }

  function logout() {
    setToken('')
    setRole('GUEST')
    setGlobalUserId('')
  }

  return {
    token,
    role,
    globalUserId,
    isGuest,
    isStandard,
    isPremiumVip,
    setToken,
    setRole,
    setGlobalUserId,
    logout
  }
})
