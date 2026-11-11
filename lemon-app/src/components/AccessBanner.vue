<script setup lang="ts">
import { ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import type { ModuleAccess } from '@/api/access'
import { goActivate, loadAccess } from '@/utils/access'

/** 模块没激活时的提示条：可以试做每章前几题，引导去激活 */
const props = defineProps<{ moduleId?: number }>()
const info = ref<ModuleAccess | null>(null)
const trial = ref(0)

async function refresh() {
  if (!props.moduleId) return
  const a = await loadAccess().catch(() => null)
  info.value = a?.modules.find((m) => m.moduleId === props.moduleId) ?? null
  trial.value = a?.trialQuestions ?? 0
}

watch(() => props.moduleId, refresh, { immediate: true })
onShow(refresh)
</script>

<template>
  <view v-if="info && !info.active" class="access-banner" @click="goActivate(info.moduleId)">
    <view class="text">
      <view class="title">「{{ info.name }}」未激活</view>
      <view class="sub">{{ trial > 0 ? `每个章节可以免费试做前 ${trial} 题，激活后可刷全部题目` : '激活后才能刷题' }}</view>
    </view>
    <text class="btn">去激活</text>
  </view>
</template>

<style lang="scss" scoped>
.access-banner {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin: 24rpx 32rpx 0;
  padding: 24rpx 28rpx;
  border-radius: 20rpx;
  background: var(--accent-soft);
  border: 1px solid $lemon-primary;
}
.text {
  flex: 1;
}
.title {
  font-size: 28rpx;
  font-weight: 600;
  color: $lemon-text;
}
.sub {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: $lemon-muted;
}
.btn {
  flex-shrink: 0;
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: $lemon-primary;
  color: #fff;
  font-size: 24rpx;
}
</style>
