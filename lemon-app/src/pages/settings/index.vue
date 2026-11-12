<script setup lang="ts">
import { ref } from 'vue'
import { isLoggedIn } from '@/utils/auth'
import { openDocument, type DocKey } from '@/api/site'
import { FONT_SCALES, getAutoNext, getFontScale, getTheme, setAutoNext, setFontScale, setTheme, type ThemeMode } from '@/utils/settings'

/**
 * 设置：外观（主题，仅 H5）、做题（字号、答对自动下一题）、数据（清除做题记录）、关于（用户协议、隐私政策）。
 * 账号相关（昵称、密码）在「我的 → 个人信息」。
 */
const THEMES: { value: ThemeMode; label: string }[] = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
]

const theme = ref<ThemeMode>(getTheme())
const fontScale = ref(getFontScale())
const autoNext = ref(getAutoNext())

function pickTheme(v: ThemeMode) {
  theme.value = v
  setTheme(v)
}

function pickFont(v: number) {
  fontScale.value = v
  setFontScale(v)
}

function toggleAutoNext() {
  autoNext.value = !autoNext.value
  setAutoNext(autoNext.value)
}

function openDoc(key: DocKey) {
  openDocument(key)
}

function openRecords() {
  uni.navigateTo({ url: isLoggedIn() ? '/pages/settings/records' : '/pages/login/index' })
}
</script>

<template>
  <view class="page">
    <!-- #ifdef H5 -->
    <view class="section-label">外观</view>
    <view class="card list-card">
      <view class="list-row">
        <view class="grow label">主题</view>
        <view class="segs">
          <text v-for="t in THEMES" :key="t.value" class="seg" :class="{ on: theme === t.value }" @click="pickTheme(t.value)">
            {{ t.label }}
          </text>
        </view>
      </view>
    </view>
    <!-- #endif -->

    <view class="section-label">做题</view>
    <view class="card list-card">
      <view class="list-row">
        <view class="grow label">字号</view>
        <view class="segs">
          <text v-for="f in FONT_SCALES" :key="f.value" class="seg" :class="{ on: fontScale === f.value }" @click="pickFont(f.value)">
            {{ f.label }}
          </text>
        </view>
      </view>
      <view class="list-row" @click="toggleAutoNext">
        <view class="grow label">答对自动下一题</view>
        <view class="switch" :class="{ on: autoNext }"><view class="knob" /></view>
      </view>
    </view>

    <view class="section-label">数据</view>
    <view class="card list-card">
      <view class="list-row" @click="openRecords">
        <view class="grow label">清除做题记录</view>
        <text class="row-arrow">›</text>
      </view>
    </view>

    <view class="section-label">关于</view>
    <view class="card list-card">
      <view class="list-row" @click="openDoc('agreement')">
        <view class="grow label">用户协议</view>
        <text class="row-arrow">›</text>
      </view>
      <view class="list-row" @click="openDoc('privacy')">
        <view class="grow label">隐私政策</view>
        <text class="row-arrow">›</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding-bottom: 48rpx;
}
.grow {
  flex: 1;
  min-width: 0;
}
.label {
  font-size: 30rpx;
}

/* 紧凑分段选择器：放在行右侧 */
.segs {
  flex-shrink: 0;
  display: flex;
  padding: 4rpx;
  border-radius: 999rpx;
  background: $lemon-bg;
  border: 1px solid $lemon-line;
}
.seg {
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: $lemon-muted;
  white-space: nowrap;
}
.seg.on {
  background: $lemon-primary;
  color: #fff;
}


/* 开关 */
.switch {
  flex-shrink: 0;
  position: relative;
  width: 88rpx;
  height: 52rpx;
  border-radius: 999rpx;
  background: $lemon-line;
  transition: background 0.2s;
}
.switch.on {
  background: $lemon-primary;
}
.knob {
  position: absolute;
  top: 6rpx;
  left: 6rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.2);
  transition: transform 0.2s;
}
.switch.on .knob {
  transform: translateX(36rpx);
}

</style>
