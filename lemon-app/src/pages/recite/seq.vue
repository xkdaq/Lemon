<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getReciteBook, getReciteItems, setLabel, typeLabel, type ReciteBook, type ReciteItem } from '@/api/recite'
import { goPractice } from '@/utils/nav'
import { subjectColor, useHideAnswer } from './shared'

/** 按套背：按试卷题号顺序，一题一题过考点 */
const bookId = ref(0)
const book = ref<ReciteBook>()
const all = ref<ReciteItem[]>([])
const setNo = ref(1)
const loaded = ref(false)
const expanded = ref<Record<number, boolean>>({})
const { hide, toggleHide, hidden, reveal } = useHideAnswer()

onLoad(async (q) => {
  bookId.value = Number(q?.bookId)
  book.value = await getReciteBook(bookId.value)
  all.value = await getReciteItems(bookId.value)
  const first = book.value?.sets[0]?.no ?? all.value[0]?.setNo ?? 1
  setNo.value = Number(q?.setNo) || first
  updateTitle()
  loaded.value = true
})

const sets = computed(() => book.value?.sets ?? [])
const items = computed(() => all.value.filter((i) => i.setNo === setNo.value))
const curSet = computed(() => sets.value.find((s) => s.no === setNo.value))
const setIdx = computed(() => sets.value.findIndex((s) => s.no === setNo.value))

function updateTitle() {
  uni.setNavigationBarTitle({ title: `按套背 · ${setLabel(book.value, setNo.value)}` })
}

function chooseSet(no: number) {
  if (no === setNo.value) return
  setNo.value = no
  expanded.value = {}
  updateTitle()
  uni.pageScrollTo({ scrollTop: 0, duration: 0 })
}

function jump(no: number) {
  uni.pageScrollTo({ selector: `#q-${no}`, duration: 200, offsetTop: -8 })
}

const rightText = (i: ReciteItem) =>
  (i.answer ?? '')
    .split('')
    .map((k) => `${k}. ${i.options.find((o) => o.key === k)?.content ?? ''}`)
    .join('\n')
</script>

<template>
  <view class="page">
    <scroll-view scroll-x class="chips">
      <view v-for="s in sets" :key="s.no" class="chip" :class="{ on: s.no === setNo }" @click="chooseSet(s.no)">
        {{ s.name }}
      </view>
    </scroll-view>

    <view class="bar">
      <view class="chip toggle" :class="{ on: hide }" @click="toggleHide">遮住答案</view>
      <view v-if="curSet?.chapterId" class="chip" @click="goPractice(curSet!.chapterId!, 'practice')">去刷这套</view>
    </view>

    <view class="qnav">
      <view v-for="i in items" :key="i.no" class="qn" @click="jump(i.no)">{{ i.no }}</view>
    </view>

    <view v-for="i in items" :id="`q-${i.no}`" :key="i.id" class="card item">
      <view class="head">
        <text class="no">{{ i.no }}</text>
        <text class="tag subj" :style="{ background: subjectColor(i.subject) }">{{ i.subject || '未分类' }}</text>
        <text class="tag">{{ typeLabel(i.type) }}</text>
        <text v-if="i.qtype" class="tag">{{ i.qtype }}</text>
      </view>
      <view v-if="i.topic" class="topic muted">{{ i.topic }}</view>
      <view class="stem" :class="{ clamp: !expanded[i.id] }" @click="expanded = { ...expanded, [i.id]: !expanded[i.id] }">
        {{ i.stem }}
      </view>

      <view class="kv">
        正确选项：<text class="ans pre" :class="{ hide: hidden(`r${i.id}`) }" @click="reveal(`r${i.id}`)">{{ rightText(i) }}</text>
      </view>
      <view v-if="i.core" class="kv core">
        📌 <text class="ans" :class="{ hide: hidden(`c${i.id}`) }" @click="reveal(`c${i.id}`)">{{ i.core }}</text>
      </view>
      <view v-for="(c, ci) in i.cards" :key="ci" class="kv point">
        · {{ c.q }}　<text class="ans" :class="{ hide: hidden(`p${i.id}-${ci}`) }" @click="reveal(`p${i.id}-${ci}`)">{{ c.a }}</text>
      </view>
      <view v-if="i.mnemonic" class="mn">💡 {{ i.mnemonic }}</view>
      <view v-if="i.tip" class="tip">🎯 {{ i.tip }}</view>

      <view v-if="i.traps.length" class="box">
        <view class="box-title">错误说法</view>
        <view v-for="(t, ti) in i.traps" :key="ti" class="trap">
          <view class="wrong">✗ {{ t.wrong }}</view>
          <view class="why">✓ <text class="ans why" :class="{ hide: hidden(`t${i.id}-${ti}`) }" @click="reveal(`t${i.id}-${ti}`)">{{ t.why }}</text></view>
        </view>
      </view>

      <view v-for="(d, di) in i.dists" :key="di" class="box">
        <view class="box-title">{{ d.title }}</view>
        <view v-for="(r, ri) in d.rows" :key="ri" class="drow">
          <text class="dk">{{ r[0] }}</text>
          <text class="dv ans" :class="{ hide: hidden(`d${i.id}-${di}-${ri}`) }" @click="reveal(`d${i.id}-${di}-${ri}`)">{{ r.slice(1).join('；') }}</text>
        </view>
      </view>
    </view>

    <view v-if="loaded && !items.length" class="empty muted">这一套还没有内容</view>

    <view v-if="items.length" class="pager">
      <view v-if="setIdx > 0" class="btn" @click="chooseSet(sets[setIdx - 1].no)">上一套</view>
      <view v-else />
      <view v-if="setIdx >= 0 && setIdx < sets.length - 1" class="btn btn-primary" @click="chooseSet(sets[setIdx + 1].no)">
        下一套
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import './recite.scss';

.page {
  padding-bottom: 60rpx;
}
.bar {
  display: flex;
  padding: 16rpx 32rpx 0;
}
.qnav {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  padding: 16rpx 32rpx 0;
}
.qn {
  width: 60rpx;
  height: 52rpx;
  line-height: 52rpx;
  text-align: center;
  border-radius: 10rpx;
  border: 1px solid var(--line);
  background: var(--surface);
  font-size: 24rpx;
}
.head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  row-gap: 8rpx;
}
.no {
  min-width: 48rpx;
  height: 48rpx;
  line-height: 48rpx;
  margin-right: 14rpx;
  padding: 0 8rpx;
  text-align: center;
  border-radius: 10rpx;
  background: var(--text);
  color: var(--bg);
  font-weight: 700;
  font-size: 26rpx;
}
.topic {
  margin-top: 8rpx;
  font-size: 24rpx;
}
.stem {
  margin-top: 12rpx;
  color: var(--muted);
  font-size: 26rpx;
  line-height: 1.6;
}
.stem.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pre {
  white-space: pre-wrap;
}
.core {
  font-weight: 600;
}
.point {
  font-size: 27rpx;
}
.trap + .trap {
  margin-top: 10rpx;
}
.pager {
  display: flex;
  justify-content: space-between;
  padding: 20rpx 32rpx 0;
}
</style>
