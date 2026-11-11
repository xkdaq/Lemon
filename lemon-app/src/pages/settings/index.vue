<script setup lang="ts">
import { ref } from 'vue'
import { isLoggedIn } from '@/utils/auth'
import { FONT_SCALES, getFontScale, getTheme, setFontScale, setTheme, type ThemeMode } from '@/utils/settings'

/** 设置：主题（仅 H5）、做题字号、清除做题记录 */
const THEMES: { value: ThemeMode; label: string }[] = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
]

const theme = ref<ThemeMode>(getTheme())
const fontScale = ref(getFontScale())

function pickTheme(v: ThemeMode) {
  theme.value = v
  setTheme(v)
}

function pickFont(v: number) {
  fontScale.value = v
  setFontScale(v)
}

function openRecords() {
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  uni.navigateTo({ url: '/pages/settings/records' })
}
</script>

<template>
  <view class="page">
    <!-- #ifdef H5 -->
    <view class="section-label">外观</view>
    <view class="card">
      <view class="row-title">主题</view>
      <view class="segs">
        <text v-for="t in THEMES" :key="t.value" class="seg" :class="{ on: theme === t.value }" @click="pickTheme(t.value)">
          {{ t.label }}
        </text>
      </view>
    </view>
    <!-- #endif -->

    <view class="section-label">做题</view>
    <view class="card">
      <view class="row-title">做题字号</view>
      <view class="segs">
        <text v-for="f in FONT_SCALES" :key="f.value" class="seg" :class="{ on: fontScale === f.value }" @click="pickFont(f.value)">
          {{ f.label }}
        </text>
      </view>
      <view class="preview" :style="{ '--fs': fontScale }">
        <view class="preview-stem">马克思主义最鲜明的政治立场是</view>
        <view class="preview-option"><text class="key">A</text>致力于实现以劳动人民为主体的最广大人民的根本利益</view>
      </view>
    </view>

    <view class="section-label">数据</view>
    <view class="card list-card">
      <view class="list-row" @click="openRecords">
        <view class="grow">
          <view>清除做题记录</view>
          <view class="muted">按题库清除做题进度，可选连同错题一起清除</view>
        </view>
        <text class="row-arrow">›</text>
      </view>
    </view>
    <view class="muted foot">主题和字号只保存在这台设备上。</view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding-bottom: 48rpx;
}
.row-title {
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 20rpx;
}
.segs {
  display: flex;
  padding: 6rpx;
  border-radius: 999rpx;
  background: $lemon-bg;
  border: 1px solid $lemon-line;
}
.seg {
  flex: 1;
  text-align: center;
  padding: 14rpx 0;
  border-radius: 999rpx;
  font-size: 28rpx;
  color: $lemon-muted;
}
.seg.on {
  background: $lemon-primary;
  color: #fff;
}
.preview {
  margin-top: 24rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  background: $lemon-bg;
}
.preview-stem {
  font-size: calc(32rpx * var(--fs, 1));
  line-height: 1.7;
  margin-bottom: 16rpx;
}
.preview-option {
  display: flex;
  gap: 16rpx;
  font-size: calc(30rpx * var(--fs, 1));
  line-height: 1.5;
}
.key {
  flex-shrink: 0;
  color: $lemon-primary;
  font-weight: 600;
}
.grow {
  flex: 1;
}
.grow .muted {
  font-size: 24rpx;
  margin-top: 4rpx;
}
.foot {
  padding: 8rpx 40rpx;
}
</style>
