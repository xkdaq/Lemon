<script setup lang="ts">
import { confirmModal } from '@/utils/modal'
import { ref } from 'vue'
import { onLoad, onReachBottom, onShow } from '@dcloudio/uni-app'
import { getList, removeWrong, type CollectedQuestion, type Kind, type Scope } from '@/api/collection'
import { unfavorite, qTypeLabel } from '@/api/practice'

/** 收藏集 / 错题集 某个范围下的题目列表：点题目从该题开始练习，可取消收藏 / 移出错题集 */
const kind = ref<Kind>('favorite')
const scope = ref<Scope>({})
const list = ref<CollectedQuestion[]>([])
const total = ref(0)
const loading = ref(false)
let pageNum = 1

onLoad((query) => {
  kind.value = (query?.kind as Kind) ?? 'favorite'
  const num = (v?: string) => (v ? Number(v) : undefined)
  scope.value = { moduleId: num(query?.moduleId), bankId: num(query?.bankId), courseId: num(query?.courseId), chapterId: num(query?.chapterId) }
  uni.setNavigationBarTitle({ title: decodeURIComponent(query?.title ?? (kind.value === 'favorite' ? '收藏' : '错题')) })
})

// 从做题页返回时刷新（错题可能已被移出）
onShow(() => reload())
onReachBottom(() => {
  if (list.value.length < total.value) load(pageNum + 1)
})

async function reload() {
  await load(1)
}

async function load(page: number) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await getList(kind.value, scope.value, page)
    list.value = page === 1 ? res.list : [...list.value, ...res.list]
    total.value = res.total
    pageNum = page
  } finally {
    loading.value = false
  }
}

function open(item: CollectedQuestion) {
  if (!item.available) {
    uni.showToast({ title: '该题已下架或删除，无法练习', icon: 'none' })
    return
  }
  const q = Object.entries(scope.value).filter(([, v]) => v).map(([k, v]) => `&${k}=${v}`).join('')
  uni.navigateTo({ url: `/pages/practice/index?kind=${kind.value}${q}&questionId=${item.questionId}` })
}

async function remove(item: CollectedQuestion) {
  const { confirm } = await confirmModal({
    title: kind.value === 'favorite' ? '取消收藏？' : '移出错题集？',
    content: item.stemText.slice(0, 40),
  })
  if (!confirm) return
  if (kind.value === 'favorite') {
    await unfavorite(item.questionId)
  } else {
    await removeWrong(item.questionId)
  }
  list.value = list.value.filter((i) => i.questionId !== item.questionId)
  total.value--
}

function startAll() {
  const q = Object.entries(scope.value).filter(([, v]) => v).map(([k, v]) => `&${k}=${v}`).join('')
  uni.navigateTo({ url: `/pages/practice/index?kind=${kind.value}${q}` })
}
</script>

<template>
  <view>
    <view class="head">
      <text class="muted">共 {{ total }} 题</text>
      <text v-if="total" class="start" @click="startAll">{{ kind === 'wrong' ? '全部重练 ›' : '全部练习 ›' }}</text>
    </view>

    <view v-for="item in list" :key="item.questionId" class="card item" :class="{ unavailable: !item.available }" @click="open(item)">
      <view class="meta">
        <text v-if="item.type" class="type">{{ qTypeLabel[item.type] }}</text>
        <text class="muted where">{{ item.bankName }} · {{ item.chapterName }}</text>
      </view>
      <view class="stem">{{ item.stemText }}</view>
      <view class="foot">
        <text v-if="kind === 'wrong'" class="muted">
          错 {{ item.wrongCount }} 次<text v-if="item.lastWrongAnswer && item.lastWrongAnswer.length < 10"> · 上次答 {{ item.lastWrongAnswer === '0' ? '没掌握' : item.lastWrongAnswer }}</text>
          <text v-if="item.rightStreak"> · 已连对 {{ item.rightStreak }} 次</text>
        </text>
        <text v-else class="muted">{{ item.time }} 收藏</text>
        <text v-if="!item.available" class="tag-off">已下架</text>
        <text class="remove" @click.stop="remove(item)">{{ kind === 'favorite' ? '取消收藏' : '移出' }}</text>
      </view>
    </view>

    <view v-if="!loading && !list.length" class="empty muted">这里还没有题目</view>
    <view v-else-if="list.length && list.length >= total" class="end muted">没有更多了</view>
  </view>
</template>

<style lang="scss" scoped>
.head {
  display: flex;
  justify-content: space-between;
  padding: 24rpx 32rpx 0;
}
.start {
  color: $lemon-primary;
  font-size: 28rpx;
}
.item.unavailable {
  opacity: 0.6;
}
.meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 12rpx;
}
.type {
  font-size: 22rpx;
  color: $lemon-primary;
  background: $lemon-primary-light;
  padding: 2rpx 12rpx;
  border-radius: 6rpx;
  flex-shrink: 0;
}
.where {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.stem {
  font-size: 30rpx;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.foot {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 16rpx;
}
.tag-off {
  font-size: 22rpx;
  color: $lemon-danger;
}
.remove {
  margin-left: auto;
  font-size: 24rpx;
  color: $lemon-muted;
  padding: 4rpx 0 4rpx 16rpx;
}
.empty,
.end {
  text-align: center;
  padding: 48rpx 0;
}
.empty {
  padding: 120rpx 0;
}
</style>
