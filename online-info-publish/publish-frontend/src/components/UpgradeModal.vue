<template>
  <!-- 遮罩层，visible为true时页面背景模糊 -->
  <div v-if="visible" class="mask" @click.self="handleMaskClick">
    <!-- 第一层提示弹窗 -->
    <div v-if="showTipModal" class="tip-modal">
      <div class="lock-icon">🔒</div>
      <div class="tag">专业会员</div>
      <h2>专业版功能</h2>
      <p class="desc">此K线统计数据仅限VIP用户查看。升级您的订阅以解锁高级盘口深度和历史分析。</p>
      <button class="upgrade-btn" @click="openPayModal">立即升级 →</button>
      <div class="back-dash" @click="emit('close')">返回仪表盘</div>
    </div>

    <!-- 第二层支付升级弹窗 -->
    <div v-if="showPayModal" class="pay-modal">
      <div class="modal-header">
        <span>股票交易系统</span>
        <span class="close-x" @click="closePayModal">×</span>
      </div>
      <div class="tip-card">
        <div class="tip-title">高级访问权限</div>
        <div class="tip-text">解锁实时 Level 2 行情数据、高级图表功能以及优先订单执行。</div>
      </div>
      <div class="form-item">
        <label>手机号码</label>
        <input
          v-model="phone"
          type="text"
          placeholder="请输入您的手机号"
          maxlength="11"
        />
      </div>
      <div class="form-item code-row">
        <label>验证码</label>
        <div class="code-wrap">
          <input
            v-model="code"
            type="text"
            placeholder="6位验证码"
            maxlength="6"
          />
          <button class="get-code-btn">获取验证码</button>
        </div>
      </div>
      <div class="form-item price-row">
        <label>总计费用</label>
        <span class="price">¥99.00 / 月</span>
      </div>
      <button class="submit-pay" @click="handleUpgrade">确认支付并升级</button>
      <p class="service-tip">点击升级即表示同意我们的<span class="red-text">服务条款</span>。</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useUserStore } from '../stores/user'
import { upgradeToVip } from '../api/market'

// 接收父组件传入的显隐控制
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})
// 向父组件抛出关闭事件
const emit = defineEmits(['close'])

// 弹窗内部状态
const showTipModal = ref(true)
const showPayModal = ref(false)
const phone = ref('')
const code = ref('')
const userStore = useUserStore()

// 打开支付弹窗，关闭提示弹窗
const openPayModal = () => {
  showTipModal.value = false
  showPayModal.value = true
}
// 关闭支付弹窗，回到提示弹窗
const closePayModal = () => {
  showPayModal.value = false
  showTipModal.value = true
  // 清空输入框
  phone.value = ''
  code.value = ''
}
// 点击遮罩空白处关闭整个弹窗
const handleMaskClick = () => {
  emit('close')
}

// 手机号+验证码校验逻辑
const handleUpgrade = async () => {
  // 校验11位纯数字手机号
  const phoneReg = /^\d{11}$/
  if (!phoneReg.test(phone.value)) {
    alert('请输入11位有效手机号码')
    return
  }
  // 校验6位纯数字验证码
  const codeReg = /^\d{6}$/
  if (!codeReg.test(code.value)) {
    alert('请输入6位数字验证码')
    return
  }

  try {
    // 调用升级VIP接口
    await upgradeToVip()
    // 更新全局用户状态为VIP
    userStore.setRole('PREMIUM_VIP')
    alert('升级VIP成功!已解锁全部高级K线指标')
    // 关闭所有弹窗，通知父组件关闭弹窗
    emit('close')
    // 重置内部状态
    showTipModal.value = true
    showPayModal.value = false
    phone.value = ''
    code.value = ''
  } catch (err) {
    alert('升级失败，请稍后重试')
  }
}
</script>

<style scoped>
/* 遮罩层，页面整体模糊 */
.mask {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(5px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}

/* 第一层提示弹窗样式 */
.tip-modal {
  width: 420px;
  background: #fff;
  border-radius: 12px;
  padding: 40px 30px;
  text-align: center;
}
.lock-icon {
  width: 64px;
  height: 64px;
  line-height: 64px;
  font-size: 32px;
  background: #f3f4f6;
  border-radius: 50%;
  margin: 0 auto 12px;
  color: #c8102e;
}
.tag {
  font-size: 13px;
  color: #666;
  background: #eee;
  padding: 3px 10px;
  border-radius: 12px;
  display: inline-block;
  margin-bottom: 16px;
}
.tip-modal h2 {
  font-size: 24px;
  margin: 0 0 10px;
  color: #111;
}
.desc {
  color: #666;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 28px;
}
.upgrade-btn {
  width: 100%;
  height: 48px;
  background: #c8102e;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  cursor: pointer;
  margin-bottom: 20px;
}
.back-dash {
  font-size: 14px;
  color: #333;
  cursor: pointer;
}

/* 第二层支付弹窗样式 */
.pay-modal {
  width: 460px;
  background: #fff;
  border-radius: 12px;
  padding: 24px 30px 32px;
}
.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 18px;
  font-weight: 500;
  margin-bottom: 20px;
}
.close-x {
  font-size: 22px;
  cursor: pointer;
  color: #666;
}
.tip-card {
  background: #1a1a1a;
  color: #fff;
  padding: 14px 16px;
  border-radius: 8px;
  margin-bottom: 24px;
}
.tip-title {
  color: #ffd042;
  font-size: 14px;
  margin-bottom: 6px;
}
.tip-text {
  font-size: 13px;
  line-height: 1.5;
  color: #ddd;
}
.form-item {
  margin-bottom: 18px;
}
.form-item label {
  display: block;
  font-size: 14px;
  color: #333;
  margin-bottom: 8px;
}
.form-item input {
  width: 100%;
  height: 44px;
  border: 1px solid #ddd;
  border-radius: 6px;
  padding: 0 14px;
  font-size: 15px;
  box-sizing: border-box;
}
.code-row .code-wrap {
  display: flex;
  gap: 10px;
}
.code-row input {
  flex: 1;
}
.get-code-btn {
  width: 110px;
  height: 44px;
  background: #fff;
  color: #c8102e;
  border: 1px solid #c8102e;
  border-radius: 6px;
  cursor: pointer;
}
.price-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f7f7f7;
  padding: 0 14px;
  border-radius: 6px;
  height: 44px;
  box-sizing: border-box;
}
.price {
  font-size: 18px;
  color: #c8102e;
  font-weight: bold;
}
.submit-pay {
  width: 100%;
  height: 50px;
  background: #c8102e;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  cursor: pointer;
  margin-top: 8px;
  margin-bottom: 16px;
}
.service-tip {
  text-align: center;
  font-size: 13px;
  color: #666;
  margin: 0;
}
.red-text {
  color: #c8102e;
  /* 不可点击，无hover样式 */
  pointer-events: none;
}
</style>