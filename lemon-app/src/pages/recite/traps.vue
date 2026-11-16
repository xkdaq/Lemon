<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  SUBJECTS,
  getReciteBook,
  getReciteItems,
  listTrapMarks,
  setLabel,
  setTrapMark,
  trapKey,
  type ReciteBook,
  type ReciteItem,
} from '@/api/recite'
import { isLoggedIn } from '@/utils/auth'
import { subjectColor } from './shared'
import { usePageTheme } from '@/utils/settings'

/** 小程序：页面配色（深浅色 / 后台配色），见 utils/settings.ts */
const themeStyle = usePageTheme()

/** 典型错误说法：先想错在哪，再点开看；“已掌握”记在账号里（需要登录） */
const bookId = ref(0)
const book = ref<ReciteBook>()
const all = ref<ReciteItem[]>([])
const loaded = ref(false)
const subject = ref('')
const setNo = ref(0)
const selfTest = ref(true)
const hideDone = ref(false)
const opened = ref<Record<string, boolean>>({})
const marks = ref<Record<string, boolean>>({})

onLoad(async (q) => {
  bookId.value = Number(q?.bookId)
  book.value = await getReciteBook(bookId.value)
  all.value = await getReciteItems(bookId.value)
  loaded.value = true
  if (isLoggedIn()) {
    const keys = await listTrapMarks(bookId.value).catch(() => [] as string[])
    marks.value = Object.fromEntries(keys.map((k) => [k, true]))
  }
})

const subjects = computed(() => SUBJECTS.filter((s) => all.value.some((i) => i.subject === s && i.traps.length)))

const scoped = computed(() =>
  all.value
    .filter((i) => (!subject.value || i.subject === subject.value) && (!setNo.value || i.setNo === setNo.value))
    .flatMap((i) => i.traps.map((t, idx) => ({ key: trapKey(i, idx), item: i, idx, t }))),
)
const doneCount = computed(() => scoped.value.filter((x) => marks.value[x.key]).length)
const list = computed(() => (hideDone.value ? scoped.value.filter((x) => !marks.value[x.key]) : scoped.value))

function toggleOpen(key: string) {
  if (selfTest.value) opened.value = { ...opened.value, [key]: !opened.value[key] }
}

async function toggleMark(x: { key: string; item: ReciteItem; idx: number }) {
  if (!isLoggedIn()) {
    uni.showToast({ title: '登录后可以记录已掌握', icon: 'none' })
    setTimeout(() => uni.navigateTo({ url: '/pages/login/index' }), 500)
    return
  }
  const next = !marks.value[x.key]
  marks.value = { ...marks.value, [x.key]: next }
  try {
    await setTrapMark(bookId.value, x.item.setNo, x.item.no, x.idx, next)
  } catch {
    marks.value = { ...marks.value, [x.key]: !next }
  }
}

function openSeq(i: ReciteItem) {
  uni.navigateTo({ url: `/pages/recite/seq?bookId=${bookId.value}&setNo=${i.setNo}&no=${i.no}` })
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
    </scroll-view>
    <view class="bar">
      <view class="chip toggle" :class="{ on: selfTest }" @click="selfTest = !selfTest; opened = {}">自测模式</view>
      <view class="chip toggle" :class="{ on: hideDone }" @click="hideDone = !hideDone">隐藏已掌握</view>
    </view>
    <view class="section-label">已掌握 {{ doneCount }} / {{ scoped.length }}<text v-if="selfTest">　先想错在哪，再点卡片看原因</text></view>

    <view v-for="x in list" :key="x.key" class="card" @click="toggleOpen(x.key)">
      <view class="wrong">✗ {{ x.t.wrong }}</view>
      <view v-if="!selfTest || opened[x.key]" class="why reason">✓ {{ x.t.why }}</view>
      <view class="foot">
        <view class="src" @click.stop="openSeq(x.item)">
          <text class="tag subj" :style="{ background: subjectColor(x.item.subject) }">{{ x.item.subject || '未分类' }}</text>
          {{ setLabel(book, x.item.setNo) }} 第{{ x.item.no }}题 ›
        </view>
        <view class="chip mark" :class="{ on: marks[x.key] }" @click.stop="toggleMark(x)">
          {{ marks[x.key] ? '已掌握' : '标记掌握' }}
        </view>
      </view>
    </view>

    <view v-if="loaded && !list.length" class="empty muted">{{ hideDone ? '都掌握了' : '没有错误说法' }}</view>
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
.wrong {
  font-size: 28rpx;
  line-height: 1.6;
}
.reason {
  margin-top: 10rpx;
  font-size: 27rpx;
  line-height: 1.6;
}
.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 12rpx;
}
.foot .src {
  margin-top: 0;
}
.mark {
  flex: none;
  margin-right: 0;
  font-size: 24rpx;
  padding: 6rpx 18rpx;
}
.mark.on {
  background: var(--g2);
  border-color: var(--g2);
}
</style>
