<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { clearRecords, getRecords, type BankRecord } from '@/api/mine'

/** 清除做题记录：列出做过题的题库，选择后清除 */
const list = ref<BankRecord[]>([])
const loaded = ref(false)
const target = ref<BankRecord | null>(null)
const busy = ref(false)

async function load() {
  list.value = await getRecords()
  loaded.value = true
}
onShow(load)

const groups = computed(() => {
  const map = new Map<string, BankRecord[]>()
  for (const r of list.value) {
    const k = r.moduleName ?? '其他'
    map.set(k, [...(map.get(k) ?? []), r])
  }
  return [...map.entries()]
})

async function doClear(includeWrong: boolean) {
  const r = target.value
  if (!r || busy.value) return
  busy.value = true
  try {
    await clearRecords(r.bankId, includeWrong)
    target.value = null
    uni.showToast({ title: '已清除', icon: 'success' })
    await load()
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="muted intro">清除后，该题库的做题进度、正确率和未完成的练习会重置，收藏不受影响。清除后无法恢复。</view>

    <template v-for="[name, items] in groups" :key="name">
      <view class="section-label">{{ name }}</view>
      <view class="card list-card">
        <view v-for="r in items" :key="r.bankId" class="list-row" @click="target = r">
          <view class="grow">
            <view>{{ r.bankName }}</view>
            <view class="muted">做过 {{ r.doneCount }} 题<text v-if="r.wrongCount"> · 错题 {{ r.wrongCount }} 题</text></view>
          </view>
          <text class="clear-btn">清除</text>
        </view>
      </view>
    </template>
    <view v-if="loaded && !list.length" class="empty">还没有做题记录</view>

    <view v-if="target" class="sheet-mask" @click="target = null">
      <view class="sheet" @click.stop>
        <view class="sheet-title">清除做题记录</view>
        <view class="muted sheet-sub">{{ target.bankName }}</view>
        <view class="sheet-option" @click="doClear(false)">
          <view class="option-title">只清除做题记录</view>
          <view class="muted">重置做题进度和正确率，错题集保留</view>
        </view>
        <view class="sheet-option danger" @click="doClear(true)">
          <view class="option-title">连同错题一起清除</view>
          <view class="muted">同时移除该题库的 {{ target.wrongCount }} 道错题</view>
        </view>
        <view class="sheet-cancel" @click="target = null">取消</view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding-bottom: 48rpx;
}
.intro {
  padding: 24rpx 40rpx 0;
  line-height: 1.6;
}
.grow {
  flex: 1;
}
.grow .muted {
  font-size: 24rpx;
  margin-top: 4rpx;
}
.clear-btn {
  font-size: 24rpx;
  padding: 6rpx 22rpx;
  border-radius: 999rpx;
  color: $lemon-danger;
  background: $lemon-danger-soft;
}
.sheet-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.sheet {
  width: 100%;
  max-width: 750px;
  box-sizing: border-box;
  background: $lemon-bg;
  border-radius: 32rpx 32rpx 0 0;
  padding: 36rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
}
.sheet-title {
  font-size: 34rpx;
  font-weight: 700;
  text-align: center;
}
.sheet-sub {
  text-align: center;
  margin: 8rpx 0 28rpx;
}
.sheet-option {
  background: $lemon-surface;
  border: 1px solid $lemon-line;
  border-radius: $lemon-radius;
  padding: 28rpx 32rpx;
  margin-bottom: 20rpx;
}
.sheet-option.danger .option-title {
  color: $lemon-danger;
}
.option-title {
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 6rpx;
}
.sheet-cancel {
  margin-top: 8rpx;
  padding: 24rpx 0;
  text-align: center;
  color: $lemon-muted;
  font-size: 30rpx;
}
</style>
