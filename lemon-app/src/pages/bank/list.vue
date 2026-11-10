<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getBanks, getCategories } from '@/api/content'
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

onLoad(async (query) => {
  const moduleId = Number(query?.moduleId)
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
    <scroll-view v-if="tabs.length" scroll-x class="tabs" :show-scrollbar="false">
      <view
        v-for="t in tabs"
        :key="t.id"
        class="tab"
        :class="{ 'tab-active': t.id === active }"
        @click="switchTab(t.id)"
      >
        {{ t.name }}
      </view>
    </scroll-view>
    <view v-for="b in banks" :key="b.id" class="card bank" @click="openBank(b)">
      <view class="bank-head">
        <text class="bank-name">{{ b.name }}</text>
        <text class="tag" :class="b.type === BankType.PAPER ? 'tag-paper' : ''">
          {{ b.type === BankType.PAPER ? '模拟卷' : '章节练习' }}
        </text>
      </view>
      <view v-if="b.intro" class="muted intro">{{ b.intro }}</view>
      <view class="muted">共 {{ b.questionCount }} 题</view>
    </view>
    <view v-if="loaded && !banks.length" class="empty muted">暂无题库，敬请期待</view>
  </view>
</template>

<style lang="scss" scoped>
.tabs {
  position: sticky;
  top: 0;
  z-index: 10;
  white-space: nowrap;
  padding: 20rpx 24rpx 12rpx;
  background: $lemon-bg;
  box-sizing: border-box;
}
.tab {
  display: inline-block;
  margin-right: 16rpx;
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  color: #555;
  background: #fff;
}
.tab-active {
  color: $lemon-text;
  background: $lemon-primary;
  font-weight: 600;
}
.bank-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.bank-name {
  font-size: 32rpx;
  font-weight: 600;
}
.tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: #e8f3ff;
  color: #3a7bd5;
}
.tag-paper {
  background: #fff1e8;
  color: #e37318;
}
.intro {
  margin-bottom: 8rpx;
}
.empty {
  text-align: center;
  padding: 120rpx 0;
}
</style>
