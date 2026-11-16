<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getReciteBook, getReciteItems, setLabel, typeLabel, type ReciteBook, type ReciteItem } from '@/api/recite'
import { goPractice } from '@/utils/nav'
import { subjectColor, useHideAnswer } from './shared'
import { usePageTheme } from '@/utils/settings'

/** 小程序：页面配色（深浅色 / 后台配色），见 utils/settings.ts */
const themeStyle = usePageTheme()

/** 按套背：按试卷题号顺序，一题一题过考点 */
const bookId = ref(0)
const book = ref<ReciteBook>()
const all = ref<ReciteItem[]>([])
const setNo = ref(1)
const loaded = ref(false)
const expanded = ref<Record<number, boolean>>({})
/** 从考点 / 辨析 / 错误说法点“第几题”进来时，定位并短暂高亮这道题 */
const focusNo = ref(0)
const { hide, toggleHide, hidden, reveal } = useHideAnswer()

onLoad(async (q) => {
  bookId.value = Number(q?.bookId)
  book.value = await getReciteBook(bookId.value)
  all.value = await getReciteItems(bookId.value)
  const first = book.value?.sets[0]?.no ?? all.value[0]?.setNo ?? 1
  setNo.value = Number(q?.setNo) || first
  updateTitle()
  loaded.value = true
  const target = Number(q?.no)
  if (target && items.value.some((i) => i.no === target)) {
    focusNo.value = target
    curNo.value = target
    await nextTick()
    // 等列表渲染完再滚动
    setTimeout(() => jump(target), 300)
    setTimeout(() => (focusNo.value = 0), 2500)
  }
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
  curNo.value = 0
  stripTarget.value = ''
  updateTitle()
  uni.pageScrollTo({ scrollTop: 0, duration: 0 })
}

/** 吸顶题号条的高度（px），跳转时让开它，题目不被挡住 */
const STRIP_H = 42
/** H5 顶部标题栏高度：吸顶要从它下面开始（小程序为 0） */
const windowTop = uni.getWindowInfo?.().windowTop ?? 0
/** 当前选中的题号（点过 / 跳过来的），题号条里高亮 */
const curNo = ref(0)
const stripTarget = ref('')

/**
 * 滚到第 no 题，让开吸顶题号条；不加动画，长列表跳转更快。
 * 节点位置是相对页面内容区（H5 在标题栏下方、小程序在导航栏下方）量的，所以不用再减标题栏高度。
 * 不用 pageScrollTo 的 selector + offsetTop：H5 下 offsetTop 不生效，题目会被题号条挡住
 */
function jump(no: number) {
  curNo.value = no
  stripTarget.value = `qs-${no}`
  uni
    .createSelectorQuery()
    .select(`#q-${no}`)
    .boundingClientRect()
    .selectViewport()
    .scrollOffset()
    .exec((res) => {
      const rect = res?.[0] as UniApp.NodeInfo | null
      const vp = res?.[1] as UniApp.NodeInfo | null
      if (!rect || rect.top == null) return
      const top = rect.top + (vp?.scrollTop ?? 0) - STRIP_H - 8
      uni.pageScrollTo({ scrollTop: Math.max(0, top), duration: 0 })
    })
}

const rightText = (i: ReciteItem) =>
  (i.answer ?? '')
    .split('')
    .map((k) => `${k}. ${i.options.find((o) => o.key === k)?.content ?? ''}`)
    .join('\n')
</script>

<template>
  <!-- #ifdef MP-WEIXIN -->
  <page-meta :page-style="themeStyle" />
  <!-- #endif -->
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

    <!-- 题号条：一行横向滑动，吸顶，往下看题时也能随时跳 -->
    <view class="strip-wrap" :style="{ top: `${windowTop}px` }">
      <scroll-view scroll-x class="strip" :scroll-into-view="stripTarget" scroll-with-animation :show-scrollbar="false">
        <view
          v-for="i in items"
          :id="`qs-${i.no}`"
          :key="i.no"
          class="qn"
          :class="{ on: curNo === i.no }"
          @click="jump(i.no)"
        >
          {{ i.no }}
        </view>
      </scroll-view>
    </view>

    <view v-for="i in items" :id="`q-${i.no}`" :key="i.id" class="card item" :class="{ focus: focusNo === i.no }">
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
.strip-wrap {
  position: sticky;
  z-index: 10;
  margin-top: 12rpx;
  padding: 12rpx 0;
  background: var(--bg);
  border-bottom: 1px solid var(--line);
}
.strip {
  white-space: nowrap;
  padding: 0 32rpx;
  box-sizing: border-box;
}
.qn {
  display: inline-block;
  min-width: 56rpx;
  height: 56rpx;
  line-height: 56rpx;
  margin-right: 10rpx;
  padding: 0 6rpx;
  text-align: center;
  border-radius: 12rpx;
  border: 1px solid var(--line);
  background: var(--surface);
  font-size: 24rpx;
  box-sizing: border-box;
}
.qn.on {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
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
.item.focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-soft);
}
.item {
  transition: border-color 0.3s, box-shadow 0.3s;
}
.pager {
  display: flex;
  justify-content: space-between;
  padding: 20rpx 32rpx 0;
}
</style>
