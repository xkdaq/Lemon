<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { SUBJECTS, getReciteBook, getReciteItems, setLabel, type ReciteBook, type ReciteItem } from '@/api/recite'
import { subjectColor, useHideAnswer } from './shared'
import { usePageTheme } from '@/utils/settings'

/** 小程序：页面配色（深浅色 / 后台配色），见 utils/settings.ts */
const themeStyle = usePageTheme()

/** 考点：按科目 → 考点归类通读，把同一知识点在不同套里的考法放在一起 */
const bookId = ref(0)
const book = ref<ReciteBook>()
const all = ref<ReciteItem[]>([])
const loaded = ref(false)
const setNo = ref(0)
const subject = ref('')
const kw = ref('')
const { hide, toggleHide, hidden, reveal } = useHideAnswer()

onLoad(async (q) => {
  bookId.value = Number(q?.bookId)
  book.value = await getReciteBook(bookId.value)
  all.value = await getReciteItems(bookId.value)
  loaded.value = true
})

const subjects = computed(() => SUBJECTS.filter((s) => all.value.some((i) => i.subject === s)))

const groups = computed(() => {
  const k = kw.value.trim()
  const list = all.value.filter(
    (i) =>
      (i.core || i.cards.length) &&
      (!setNo.value || i.setNo === setNo.value) &&
      (!subject.value || i.subject === subject.value) &&
      (!k || [i.topic, i.core, i.mnemonic, i.stem, ...i.cards.flatMap((c) => [c.q, c.a])].some((t) => t?.includes(k))),
  )
  const order = [...SUBJECTS, '未分类']
  const bySubj = new Map<string, Map<string, ReciteItem[]>>()
  for (const i of list) {
    const s = i.subject || '未分类'
    if (!bySubj.has(s)) bySubj.set(s, new Map())
    const t = i.topic || '其他'
    const m = bySubj.get(s)!
    if (!m.has(t)) m.set(t, [])
    m.get(t)!.push(i)
  }
  return order
    .filter((s) => bySubj.has(s))
    .map((s) => ({
      subject: s,
      topics: [...bySubj.get(s)!.entries()]
        .sort((a, b) => a[0].localeCompare(b[0], 'zh'))
        .map(([topic, items]) => ({ topic, items })),
    }))
})

function openSeq(i: ReciteItem) {
  uni.navigateTo({ url: `/pages/recite/seq?bookId=${bookId.value}&setNo=${i.setNo}` })
}
</script>

<template>
  <!-- #ifdef MP-WEIXIN -->
  <page-meta :page-style="themeStyle" />
  <!-- #endif -->
  <view class="page">
    <scroll-view scroll-x class="chips">
      <view class="chip" :class="{ on: !setNo }" @click="setNo = 0">全部套</view>
      <view v-for="s in book?.sets ?? []" :key="s.no" class="chip" :class="{ on: setNo === s.no }" @click="setNo = s.no">
        {{ s.name }}
      </view>
    </scroll-view>
    <scroll-view scroll-x class="chips">
      <view class="chip" :class="{ on: !subject }" @click="subject = ''">全部科目</view>
      <view v-for="s in subjects" :key="s" class="chip" :class="{ on: subject === s }" @click="subject = s">{{ s }}</view>
      <view class="chip toggle" :class="{ on: hide }" @click="toggleHide">遮住答案</view>
    </scroll-view>
    <input v-model="kw" class="search" placeholder="搜索关键词，如：剩余价值、遵义会议" confirm-type="search" />

    <view v-for="g in groups" :key="g.subject">
      <view class="section-label">
        <text class="tag subj" :style="{ background: subjectColor(g.subject) }">{{ g.subject }}</text>
      </view>
      <view class="card">
        <view v-for="t in g.topics" :key="t.topic" class="topic-block">
          <view class="topic">{{ t.topic }}</view>
          <view v-for="i in t.items" :key="i.id" class="pt">
            <view v-if="i.core" class="core">{{ i.core }}</view>
            <view v-for="(c, ci) in i.cards" :key="ci" class="kv">
              · {{ c.q }}　<text class="ans" :class="{ hide: hidden(`p${i.id}-${ci}`) }" @click="reveal(`p${i.id}-${ci}`)">{{ c.a }}</text>
            </view>
            <view v-if="i.mnemonic" class="mn">💡 {{ i.mnemonic }}</view>
            <view v-if="i.tip" class="tip">🎯 {{ i.tip }}</view>
            <view class="src" @click="openSeq(i)">
              <text v-if="i.qtype" class="tag">{{ i.qtype }}</text>{{ setLabel(book, i.setNo) }} 第{{ i.no }}题 ›
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="loaded && !groups.length" class="empty muted">没有匹配的考点</view>
  </view>
</template>

<style lang="scss" scoped>
@import './recite.scss';

.page {
  padding-bottom: 60rpx;
}
.topic-block + .topic-block {
  margin-top: 28rpx;
}
.topic {
  color: var(--muted);
  font-size: 26rpx;
  font-weight: 600;
}
.pt {
  padding: 16rpx 0;
  border-bottom: 1px solid var(--line);
}
.pt:last-child {
  border-bottom: 0;
}
.core {
  font-weight: 600;
  font-size: 28rpx;
  line-height: 1.6;
}
</style>
