<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getServiceInfo } from '@/api/site'

/** 联系客服：客服微信号（点击复制） */
const info = ref<{ wechat: string; note: string } | null>(null)

onLoad(async () => {
  info.value = await getServiceInfo()
})

function copy() {
  if (!info.value?.wechat) return
  uni.setClipboardData({ data: info.value.wechat, success: () => uni.showToast({ title: '微信号已复制', icon: 'none' }) })
}
</script>

<template>
  <view v-if="info" class="page">
    <view class="card">
      <view class="label muted">客服微信</view>
      <view v-if="info.wechat" class="wechat-row">
        <text class="wechat">{{ info.wechat }}</text>
        <text class="copy" @click="copy">复制</text>
      </view>
      <view v-else class="muted">暂未设置</view>
      <view v-if="info.note" class="note">{{ info.note }}</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.label {
  font-size: 26rpx;
}
.wechat-row {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}
.wechat {
  flex: 1;
  font-size: 44rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
}
.copy {
  padding: 12rpx 36rpx;
  border-radius: 999rpx;
  background: $lemon-primary;
  color: #fff;
  font-size: 28rpx;
}
.note {
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1px solid $lemon-line;
  font-size: 28rpx;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
