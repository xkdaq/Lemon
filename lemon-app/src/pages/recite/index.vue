<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import { clearReciteCache, listReciteBooks, type ReciteBook } from '@/api/recite'
import { usePageTheme } from '@/utils/settings'

/** 小程序：页面配色（深浅色 / 后台配色），见 utils/settings.ts */
const themeStyle = usePageTheme()

/** 背诵 tab：背诵资料列表，每份资料四个入口。免费开放，不需要登录 */
const books = ref<ReciteBook[]>([])
const loaded = ref(false)

async function load() {
  try {
    books.value = await listReciteBooks()
  } finally {
    loaded.value = true
  }
}

onLoad(load)
onPullDownRefresh(async () => {
  clearReciteCache()
  await load().finally(() => uni.stopPullDownRefresh())
})

const ENTRIES = [
  { page: 'seq', title: '按套背', desc: '按试卷题号逐题过考点' },
  { page: 'points', title: '考点', desc: '按科目、考点归类通读' },
  { page: 'dist', title: '辨析', desc: '容易混的概念对照表' },
  { page: 'traps', title: '错误说法', desc: '练排除法，积累干扰项' },
]

function open(b: ReciteBook, page: string) {
  uni.navigateTo({ url: `/pages/recite/${page}?bookId=${b.id}` })
}
</script>

<template>
  <!-- #ifdef MP-WEIXIN -->
  <page-meta :page-style="themeStyle" />
  <!-- #endif -->
  <view class="page">
    <view class="section-label">肖八肖四背诵模块</view>

    <view v-for="b in books" :key="b.id" class="card book">
      <view class="book-name">{{ b.name }}</view>
      <view class="muted meta">{{ b.sets.length }} 套 · {{ b.itemCount }} 题<text v-if="b.intro"> · {{ b.intro }}</text></view>
      <view class="entries">
        <view v-for="e in ENTRIES" :key="e.page" class="entry" @click="open(b, e.page)">
          <view class="entry-title">{{ e.title }}</view>
          <view class="entry-desc">{{ e.desc }}</view>
        </view>
      </view>
    </view>

    <view v-if="loaded && !books.length" class="empty muted">暂时还没有背诵资料</view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding-bottom: 48rpx;
}
.book-name {
  font-size: 34rpx;
  font-weight: 600;
}
.meta {
  margin-top: 6rpx;
  font-size: 24rpx;
}
.entries {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-top: 24rpx;
}
.entry {
  background: var(--accent-soft);
  border-radius: 16rpx;
  padding: 22rpx 20rpx;
}
.entry:active {
  opacity: 0.8;
}
.entry-title {
  color: var(--accent);
  font-size: 30rpx;
  font-weight: 600;
}
.entry-desc {
  margin-top: 4rpx;
  color: var(--muted);
  font-size: 22rpx;
  line-height: 1.4;
}
.empty {
  padding: 120rpx 0;
  text-align: center;
}
</style>
