<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { activate, type AccessInfo } from '@/api/access'
import { clearAccessCache, loadAccess } from '@/utils/access'
import { isLoggedIn } from '@/utils/auth'

/**
 * 我的模块：各模块的激活状态；激活码输入默认收起，点「输入激活码」展开。
 * 从「需要激活」进来（带 moduleId）时直接展开。同一模块没到期再激活会顺延。
 */
const info = ref<AccessInfo | null>(null)
const code = ref('')
const loading = ref(false)
/** 从「需要激活」进来时高亮的模块 */
const focusModule = ref(0)
const showCode = ref(false)

onLoad((query) => {
  focusModule.value = Number(query?.moduleId) || 0
  showCode.value = focusModule.value > 0
})

onShow(async () => {
  if (!isLoggedIn()) {
    uni.redirectTo({ url: '/pages/login/index' })
    return
  }
  info.value = await loadAccess(true).catch(() => null)
})

const day = (t: string | null) => (t ? t.slice(0, 10) : '')

async function submit() {
  const c = code.value.trim()
  if (!c) return uni.showToast({ title: '请输入激活码', icon: 'none' })
  loading.value = true
  try {
    const r = await activate(c)
    code.value = ''
    clearAccessCache()
    info.value = await loadAccess(true).catch(() => info.value)
    uni.showModal({
      title: '激活成功',
      content: `「${r.moduleName}」${r.extended ? '已顺延' : '已激活'}，有效期至 ${day(r.expireAt)}，模块下的所有题库都可以刷了。`,
      showCancel: false,
      confirmText: '去刷题',
      success: () => {
        if (getCurrentPages().length > 1) uni.navigateBack()
        else uni.switchTab({ url: '/pages/index/index' })
      },
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="page">
    <view v-if="info" class="card list-card">
      <view v-for="m in info.modules" :key="m.moduleId" class="list-row" :class="{ focus: m.moduleId === focusModule && !m.active }">
        <view class="grow">{{ m.name }}</view>
        <text v-if="m.free" class="state on">免费</text>
        <text v-else-if="m.active" class="state on">已激活 · 至 {{ day(m.expireAt) }}</text>
        <text v-else-if="m.expireAt" class="state">已过期（{{ day(m.expireAt) }}）</text>
        <text v-else class="state">未激活</text>
      </view>
    </view>
    <view v-if="info && info.trialQuestions > 0" class="muted foot">
      未激活的模块可以浏览全部题库，每个章节免费试做前 {{ info.trialQuestions }} 题。
    </view>

    <view v-if="showCode" class="card">
      <view class="title">输入激活码</view>
      <view class="muted sub">一个激活码对应一个模块，激活后该模块下的所有题库都可以刷。</view>
      <view class="field">
        <input v-model="code" maxlength="40" placeholder="如 ABCD-EFGH-JKMN" class="code-input" @confirm="submit" />
      </view>
      <button class="submit" :loading="loading" :disabled="loading" @click="submit">激活</button>
    </view>

    <view v-else class="code-link" @click="showCode = true">输入激活码 ›</view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding-bottom: 48rpx;
}
.title {
  font-size: 32rpx;
  font-weight: 600;
}
.sub {
  margin: 8rpx 0 28rpx;
  font-size: 24rpx;
}
.field {
  background: $lemon-bg;
  border: 1px solid $lemon-line;
  border-radius: 16rpx;
  padding: 24rpx 28rpx;
}
.code-input {
  font-size: 32rpx;
  letter-spacing: 2rpx;
}
.submit {
  margin-top: 32rpx;
  background: $lemon-primary;
  color: #fff;
  border-radius: 999rpx;
}
.grow {
  flex: 1;
}
.state {
  font-size: 26rpx;
  color: $lemon-muted;
}
.state.on {
  color: $lemon-primary;
}
.focus {
  background: var(--accent-soft);
}
.code-link {
  padding: 32rpx;
  font-size: 26rpx;
  color: $lemon-primary;
}
.foot {
  padding: 8rpx 32rpx;
  font-size: 24rpx;
}
</style>
