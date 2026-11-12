<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { NOTE_SAVED, getNote, type NoteDetail, type NoteSavedEvent } from '@/api/note'
import { QType, qTypeLabel } from '@/api/practice'
import { assetUrl, renderHtml } from '@/utils/html'

/** 笔记详情：题目（激活了的模块带答案解析）+ 我的笔记 */
let questionId = 0
const d = ref<NoteDetail | null>(null)

async function load() {
  d.value = await getNote(questionId)
}

function onSaved(e: NoteSavedEvent) {
  if (e.questionId !== questionId) return
  if (!e.note) {
    // 删掉了：回到列表
    uni.navigateBack()
    return
  }
  if (d.value) d.value.note = e.note
}

onLoad((query) => {
  questionId = Number(query?.questionId)
  uni.$on(NOTE_SAVED, onSaved)
  load()
})
onUnload(() => uni.$off(NOTE_SAVED, onSaved))

const optionKey = (type: number, key: string) => (type === QType.JUDGE ? (key === 'A' ? '✓' : '✗') : key)
const answerText = (x: NoteDetail) =>
  x.type === QType.JUDGE ? (x.answer === 'A' ? '正确' : '错误') : (x.answer ?? '')

function edit() {
  uni.navigateTo({ url: `/pages/note/edit?questionId=${questionId}` })
}

function preview(i: number) {
  if (!d.value?.note) return
  uni.previewImage({ urls: d.value.note.images.map(assetUrl), current: i })
}
</script>

<template>
  <view v-if="d" class="page">
    <view class="card">
      <view class="meta muted">
        <text class="q-type">{{ qTypeLabel[d.type] }}</text>
        <text>{{ [d.bankName, d.chapterName].filter(Boolean).join(' · ') }}</text>
      </view>
      <rich-text class="stem" :nodes="renderHtml(d.stem)" />
      <view v-if="d.type !== QType.SUBJECTIVE" class="options">
        <view v-for="o in d.options" :key="o.key" class="option" :class="{ right: d.answer?.includes(o.key) }">
          <text class="option-key">{{ optionKey(d.type, o.key) }}</text>
          <text>{{ o.content }}</text>
        </view>
      </view>
      <view v-if="d.answer || d.analysis" class="analysis">
        <view v-if="d.answer && d.type !== QType.SUBJECTIVE" class="answer-line">正确答案：<text class="right-text">{{ answerText(d) }}</text></view>
        <template v-if="d.analysis">
          <view class="analysis-title">{{ d.type === QType.SUBJECTIVE ? '参考答案' : '解析' }}</view>
          <rich-text :nodes="renderHtml(d.analysis)" />
        </template>
      </view>
    </view>

    <view class="card note" @click="edit">
      <view class="note-head">
        <text class="note-title">我的笔记</text>
        <text class="note-action">编辑 ›</text>
      </view>
      <template v-if="d.note">
        <view v-if="d.note.content" class="note-text">{{ d.note.content }}</view>
        <view v-if="d.note.images.length" class="note-imgs">
          <image v-for="(u, i) in d.note.images" :key="u" class="note-img" :src="assetUrl(u)" mode="aspectFill" @click.stop="preview(i)" />
        </view>
        <view class="muted note-time">{{ d.note.updatedAt }}</view>
      </template>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding-bottom: 48rpx;
}
.meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  font-size: 24rpx;
  margin-bottom: 16rpx;
}
.q-type {
  padding: 2rpx 12rpx;
  border-radius: 6rpx;
  background: var(--accent-soft);
  color: $lemon-primary;
}
.stem {
  font-size: 30rpx;
  line-height: 1.7;
}
.options {
  margin-top: 20rpx;
}
.option {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 0;
  font-size: 28rpx;
  line-height: 1.6;
  border-bottom: 1px solid $lemon-line;
}
.option:last-child {
  border-bottom: none;
}
.option-key {
  font-weight: 600;
  min-width: 32rpx;
}
.option.right {
  color: $lemon-success;
}
.analysis {
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1px dashed $lemon-line;
  font-size: 28rpx;
  line-height: 1.7;
}
.right-text {
  color: $lemon-success;
  font-weight: 600;
}
.analysis-title {
  font-weight: 600;
  margin: 8rpx 0;
}
.note-head {
  display: flex;
  align-items: center;
}
.note-title {
  font-weight: 600;
  font-size: 30rpx;
}
.note-action {
  margin-left: auto;
  color: $lemon-primary;
  font-size: 26rpx;
}
.note-text {
  margin-top: 16rpx;
  font-size: 28rpx;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}
.note-imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}
.note-img {
  width: 180rpx;
  height: 180rpx;
  border-radius: 12rpx;
}
.note-time {
  margin-top: 12rpx;
  font-size: 22rpx;
}
</style>
