<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onReachBottom, onUnload } from '@dcloudio/uni-app'
import { NOTE_SAVED, listNotes, type NoteItem, type NoteSavedEvent } from '@/api/note'
import { assetUrl } from '@/utils/html'

/** 我的笔记：最近修改的在前 */
const list = ref<NoteItem[]>([])
const total = ref(0)
const loading = ref(false)
const loaded = ref(false)
let pageNum = 0

async function loadMore(reset = false) {
  if (loading.value) return
  if (reset) {
    pageNum = 0
  } else if (loaded.value && list.value.length >= total.value) {
    return
  }
  loading.value = true
  try {
    const r = await listNotes(pageNum + 1)
    pageNum++
    list.value = reset ? r.list : list.value.concat(r.list)
    total.value = r.total
    loaded.value = true
  } finally {
    loading.value = false
  }
}

// 详情页里改了 / 删了笔记：刷新列表
const onSaved = (_: NoteSavedEvent) => loadMore(true)

onLoad(() => {
  uni.$on(NOTE_SAVED, onSaved)
  loadMore(true)
})
onUnload(() => uni.$off(NOTE_SAVED, onSaved))
onReachBottom(() => loadMore())

function open(n: NoteItem) {
  uni.navigateTo({ url: `/pages/note/detail?questionId=${n.questionId}` })
}
</script>

<template>
  <view class="page">
    <view v-for="n in list" :key="n.questionId" class="card item" @click="open(n)">
      <view class="stem muted">{{ n.stemText }}</view>
      <view v-if="n.content" class="content">{{ n.content }}</view>
      <view v-if="n.images.length" class="imgs">
        <image v-for="u in n.images.slice(0, 3)" :key="u" class="img" :src="assetUrl(u)" mode="aspectFill" />
        <text v-if="n.images.length > 3" class="more muted">+{{ n.images.length - 3 }}</text>
      </view>
      <view class="foot muted">
        <text class="where">{{ [n.bankName, n.chapterName].filter(Boolean).join(' · ') }}</text>
        <text>{{ n.updatedAt }}</text>
      </view>
    </view>
    <view v-if="loaded && !list.length" class="empty muted">还没有笔记</view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding-bottom: 48rpx;
}
.stem {
  font-size: 24rpx;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.content {
  margin-top: 12rpx;
  font-size: 28rpx;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.imgs {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 12rpx;
}
.img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 10rpx;
}
.more {
  font-size: 24rpx;
}
.foot {
  display: flex;
  gap: 16rpx;
  margin-top: 12rpx;
  font-size: 22rpx;
}
.where {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.empty {
  padding: 120rpx 0;
  text-align: center;
}
</style>
