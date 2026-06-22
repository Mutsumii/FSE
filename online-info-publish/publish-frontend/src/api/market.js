import axios from 'axios'
import { useUserStore } from '../stores/user'

const api = axios.create({
  baseURL: '/api/publish',
  timeout: 10000
})

const unwrapResponse = (payload) => {
  if (payload && typeof payload === 'object' && Object.prototype.hasOwnProperty.call(payload, 'code')) {
    if (payload.code !== 200 && payload.code !== 0) {
      throw new Error(payload.message || 'Request failed')
    }
    return payload.data ?? payload
  }

  return payload
}

api.interceptors.request.use((config) => {
  const userStore = useUserStore()

  if (userStore.globalUserId) {
    config.headers = {
      ...(config.headers || {}),
      'X-Fund-Acc-No': userStore.globalUserId
    }
  }

  return config
})

api.interceptors.response.use(
  (response) => unwrapResponse(response.data),
  (error) => {
    const message = error.response?.data?.message || error.message || 'Request failed'
    return Promise.reject(new Error(message))
  }
)

export function searchStock(keyword) {
  return api.get('/stock/search', { params: { keyword } })
}

export function getQuote(stockCode) {
  return api.get(`/market/quote/${stockCode}`)
}

export function getKLine(stockCode, period = '1D') {
  return api.get('/market/kline', { params: { stockCode, period } })
}

export function getUserMe() {
  return api.get('/user/me')
}

export function upgradeToVip() {
  return api.post('/user/upgrade')
}

export default api
