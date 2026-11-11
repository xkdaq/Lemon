<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getBanks, getCategories } from '@/api/content'
import AccessBanner from '@/components/AccessBanner.vue'
import { BankType, type BankCategory, type QuestionBank } from '@/api/types'

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

function openBank(b: QuestionBank) {
  uni.navigateTo({ url: `/pages/bank/detail?bankId=${b.id}` })
}
</script>

<template>
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
    <view v-if="banks.length" class="card list-card">
      <view v-for="b in banks" :key="b.id" class="list-row" @click="openBank(b)">
        <view class="bank-main">
          <view class="bank-name">{{ b.name }}</view>
          <view class="muted">
            {{ b.type === BankType.PAPER ? '模拟卷' : '章节练习' }} · 共 {{ b.questionCount }} 题<text v-if="b.intro"> · {{ b.intro }}</text>
          </view>
        </view>
        <text class="row-arrow">›</text>
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
.bank-main {
  flex: 1;
  min-width: 0;
}
.bank-name {
  font-size: 32rpx;
  font-weight: 600;
}
</style>
