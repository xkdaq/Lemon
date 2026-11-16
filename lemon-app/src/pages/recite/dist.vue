<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { SUBJECTS, getReciteBook, getReciteItems, setLabel, type ReciteBook, type ReciteItem } from '@/api/recite'
import { subjectColor, useHideAnswer } from './shared'

/** 易混辨析：解析里需要区分的概念、会议、文章、时间，整理成对照表 */
const bookId = ref(0)
const book = ref<ReciteBook>()
const all = ref<ReciteItem[]>([])
const loaded = ref(false)
const subject = ref('')
const kw = ref('')
const { hide, toggleHide, hidden, reveal } = useHideAnswer()

onLoad(async (q) => {
  bookId.value = Number(q?.bookId)
  book.value = await getReciteBook(bookId.value)
  all.value = await getReciteItems(bookId.value)
  loaded.value = true
})

const subjects = computed(() => SUBJECTS.filter((s) => all.value.some((i) => i.subject === s && i.dists.length)))

const list = computed(() => {
  const k = kw.value.trim()
  return all.value
    .filter((i) => !subject.value || i.subject === subject.value)
    .flatMap((i) => i.dists.map((d, di) => ({ key: `${i.id}-${di}`, item: i, d })))
    .filter((x) => !k || x.d.title.includes(k) || x.d.rows.some((r) => r.some((c) => c.includes(k))))
})

function openSeq(i: ReciteItem) {
  uni.navigateTo({ url: `/pages/recite/seq?bookId=${bookId.value}&setNo=${i.setNo}` })
}
</script>

<template>
  <view class="page">
    <scroll-view scroll-x class="chips">
      <view class="chip" :class="{ on: !subject }" @click="subject = ''">全部科目</view>
      <view v-for="s in subjects" :key="s" class="chip" :class="{ on: subject === s }" @click="subject = s">{{ s }}</view>
      <view class="chip toggle" :class="{ on: hide }" @click="toggleHide">遮住答案</view>
    </scroll-view>
    <input v-model="kw" class="search" placeholder="搜索，如：参与制、整风" confirm-type="search" />
    <view class="section-label">共 {{ list.length }} 张对照表</view>

    <view v-for="x in list" :key="x.key" class="card">
      <view class="title">{{ x.d.title }}</view>
      <view v-for="(r, ri) in x.d.rows" :key="ri" class="drow">
        <text class="dk">{{ r[0] }}</text>
        <text class="dv ans" :class="{ hide: hidden(`${x.key}-${ri}`) }" @click="reveal(`${x.key}-${ri}`)">{{ r.slice(1).join('；') }}</text>
      </view>
      <view class="src" @click="openSeq(x.item)">
        <text class="tag subj" :style="{ background: subjectColor(x.item.subject) }">{{ x.item.subject || '未分类' }}</text>
        {{ setLabel(book, x.item.setNo) }} 第{{ x.item.no }}题 ›
      </view>
    </view>

    <view v-if="loaded && !list.length" class="empty muted">没有匹配的辨析</view>
  </view>
</template>

<style lang="scss" scoped>
@import './recite.scss';

.page {
  padding-bottom: 60rpx;
}
.title {
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}
.drow {
  font-size: 27rpx;
  line-height: 1.6;
}
</style>
