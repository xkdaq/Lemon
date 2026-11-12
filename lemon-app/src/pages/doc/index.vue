<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getDocument, type DocKey } from '@/api/site'

/** 用户协议 / 隐私政策 */
const doc = ref<{ title: string; content: string; updatedAt: string } | null>(null)

onLoad(async (query) => {
  doc.value = await getDocument((query?.key as DocKey) || 'agreement')
  uni.setNavigationBarTitle({ title: doc.value.title })
})
</script>

<template>
  <view v-if="doc" class="page">
    <view class="title">{{ doc.title }}</view>
    <view class="muted date">更新日期：{{ doc.updatedAt }}</view>
    <view class="content">{{ doc.content }}</view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding: 32rpx 40rpx 64rpx;
}
.title {
  font-size: 40rpx;
  font-weight: 700;
}
.date {
  margin: 8rpx 0 32rpx;
  font-size: 24rpx;
}
.content {
  font-size: 28rpx;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
