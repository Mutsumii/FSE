import { createRouter, createWebHistory } from 'vue-router'

const Navigation = () => import('../views/Navigation.vue')
const Portal = () => import('../views/Portal.vue')
const StockDetail = () => import('../views/StockDetail.vue')

const routes = [
  { path: '/', name: 'Navigation', component: Navigation },
  { path: '/home', name: 'Portal', component: Portal },
  { path: '/stock/:code', name: 'StockDetail', component: StockDetail, props: true }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
