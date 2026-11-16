<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getBanks, getCategories } from '@/api/content'
import AccessBanner from '@/components/AccessBanner.vue'
import { BankType, type BankCategory, type QuestionBank } from '@/api/types'
import { usePageTheme } from '@/utils/settings'

/** 小程序：页面配色（深浅色 / 后台配色），见 utils/settings.ts */
const themeStyle = usePageTheme()

const allBanks = ref<QuestionBank[]>([])
const categories = ref<BankCategory[]>([])
const loaded = ref(false)
/** 当前分类；0 表示“其他”（未分类） */
const active = ref<number>(0)

/** 只展示有题库的分类；未分类题库在有分类时归入“其他”，没有分类时直接平铺 */
const tabs = computed(() => {
  const list = categories.value
    .filter((c) => allBanks.value.some((b) => b.categoryId === c.id))
    .map((c) => ({ id: c.id, name: c.name }))
  if (list.length && allBanks.value.some((b) => !b.categoryId)) list.push({ id: 0, name: '其他' })
  return list
})
const banks = computed(() =>
  tabs.value.length ? allBanks.value.filter((b) => (b.categoryId ?? 0) === active.value) : allBanks.value,
)

let storeKey = ''

const moduleIdRef = ref<number>()
onLoad(async (query) => {
  const moduleId = Number(query?.moduleId)
  moduleIdRef.value = moduleId
  storeKey = `bank-category-${moduleId}`
  uni.setNavigationBarTitle({ title: decodeURIComponent(query?.name ?? '题库') })
  ;[allBanks.value, categories.value] = await Promise.all([getBanks(moduleId), getCategories(moduleId)])
  // 回到上次看的分类
  const saved = uni.getStorageSync(storeKey)
  const last = saved === '' ? NaN : Number(saved)
  active.value = tabs.value.some((t) => t.id === last) ? last : tabs.value[0]?.id ?? 0
  loaded.value = true
})

function switchTab(id: number) {
  active.value = id
  uni.setStorageSync(storeKey, id)
}

/** 名称开头的【27版】【真题】等拆成小标签，卡片标题更短 */
function splitName(name: string): { tag: string; title: string } {
  const m = /^【([^】]{1,8})】\s*(.+)$/.exec(name)
  return m ? { tag: m[1], title: m[2] } : { tag: '', title: name }
}

function openBank(b: QuestionBank) {
  uni.navigateTo({ url: `/pages/bank/detail?bankId=${b.id}` })
}
</script>

<template>
  <!-- #ifdef MP-WEIXIN -->
  <page-meta :page-style="themeStyle" />
  <!-- #endif -->
  <view>
    <AccessBanner :module-id="moduleIdRef" />
    <scroll-view v-if="tabs.length" scroll-x class="tabs" :show-scrollbar="false">
      <view
        v-for="t in tabs"
        :key="t.id"
        class="chip"
        :class="{ 'chip-on': t.id === active }"
        @click="switchTab(t.id)"
      >
        {{ t.name }}
      </view>
    </scroll-view>
    <!-- 左右两列卡片 -->
    <view v-if="banks.length" class="grid">
      <view v-for="b in banks" :key="b.id" class="tile" @click="openBank(b)">
        <text v-if="splitName(b.name).tag" class="tile-tag">{{ splitName(b.name).tag }}</text>
        <view class="tile-name">{{ splitName(b.name).title }}</view>
        <view class="tile-meta">{{ b.type === BankType.PAPER ? '模拟卷' : '章节练习' }} · {{ b.questionCount }} 题</view>
        <view v-if="b.intro" class="tile-intro">{{ b.intro }}</view>
      </view>
    </view>
    <view v-if="loaded && !banks.length" class="empty">暂无题库，敬请期待</view>
  </view>
</template>

<style lang="scss" scoped>
.tabs {
  position: sticky;
  top: 0;
  z-index: 10;
  white-space: nowrap;
  padding: 24rpx 32rpx 8rpx;
  background: $lemon-bg;
  box-sizing: border-box;
}
.chip {
  display: inline-block;
  margin-right: 12rpx;
  padding: 6rpx 28rpx;
  border-radius: 99rpx;
  border: 1px solid $lemon-line;
  background: $lemon-surface;
  color: $lemon-muted;
  font-size: 26rpx;
}
.chip-on {
  background: $lemon-primary-light;
  border-color: $lemon-primary;
  color: $lemon-primary;
  font-weight: 600;
}
/* 两列：小程序对 grid 支持不稳，用 flex 换行 */
.grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  padding: 12rpx 32rpx 48rpx;
}
.tile {
  width: calc(50% - 10rpx);
  margin-top: 20rpx;
  padding: 24rpx 24rpx 22rpx;
  box-sizing: border-box;
  background: $lemon-surface;
  border: 1px solid $lemon-line;
  border-radius: $lemon-radius;
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
}
.tile:active {
  opacity: 0.85;
}
.tile-tag {
  align-self: flex-start;
  margin-bottom: 10rpx;
  padding: 2rpx 14rpx;
  border-radius: 99rpx;
  background: $lemon-primary-light;
  color: var(--accent);
  font-size: 22rpx;
  line-height: 1.6;
}
.tile-name {
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}
.tile-meta {
  margin-top: auto;
  padding-top: 12rpx;
  color: var(--muted);
  font-size: 24rpx;
}
.tile-intro {
  margin-top: 4rpx;
  color: var(--muted);
  font-size: 22rpx;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
