<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { MAX_NOTE_CONTENT, MAX_NOTE_IMAGES, NOTE_SAVED, deleteNote, getNote, saveNote, uploadNoteImage, type NoteSavedEvent } from '@/api/note'
import { assetUrl } from '@/utils/html'
import { compressImage } from '@/utils/image'
import { confirmModal } from '@/utils/modal'

/** 写 / 编辑笔记：文字 + 最多 6 张图片，只有自己看得到 */
let questionId = 0
const stemText = ref('')
const content = ref('')
const images = ref<string[]>([])
const existed = ref(false)
const uploading = ref(0)
const saving = ref(false)
const loaded = ref(false)

const canAdd = computed(() => images.value.length + uploading.value < MAX_NOTE_IMAGES)

onLoad(async (query) => {
  questionId = Number(query?.questionId)
  const d = await getNote(questionId)
  stemText.value = d.stem.replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim()
  if (d.note) {
    content.value = d.note.content
    images.value = [...d.note.images]
    existed.value = true
  }
  loaded.value = true
})

async function addImages() {
  const r = await uni.chooseImage({
    count: MAX_NOTE_IMAGES - images.value.length - uploading.value,
    sizeType: ['compressed'],
  }).catch(() => null)
  if (!r) return
  const files = (r.tempFiles as { path: string; size: number }[]).slice(0, MAX_NOTE_IMAGES - images.value.length - uploading.value)
  for (const f of files) {
    uploading.value++
    try {
      const path = await compressImage(f.path, f.size)
      images.value.push(await uploadNoteImage(path))
    } catch {
      // 上传失败已提示
    } finally {
      uploading.value--
    }
  }
}

function removeImage(i: number) {
  images.value.splice(i, 1)
}

function preview(i: number) {
  uni.previewImage({ urls: images.value.map(assetUrl), current: i })
}

function done(note: NoteSavedEvent['note']) {
  uni.$emit(NOTE_SAVED, { questionId, note } as NoteSavedEvent)
  uni.navigateBack()
}

async function save() {
  if (uploading.value) return uni.showToast({ title: '图片还在上传', icon: 'none' })
  if (!content.value.trim() && !images.value.length) return uni.showToast({ title: '写点什么吧', icon: 'none' })
  saving.value = true
  try {
    const note = await saveNote(questionId, content.value, images.value)
    uni.showToast({ title: '已保存', icon: 'none' })
    setTimeout(() => done(note), 400)
  } finally {
    saving.value = false
  }
}

async function remove() {
  const { confirm } = await confirmModal({ title: '删除这条笔记？', confirmText: '删除' })
  if (!confirm) return
  await deleteNote(questionId)
  done(null)
}
</script>

<template>
  <view v-if="loaded" class="page">
    <view class="card">
      <view class="stem muted">{{ stemText }}</view>
      <textarea
        v-model="content"
        class="input"
        :maxlength="MAX_NOTE_CONTENT"
        auto-height
        placeholder="写下这道题的笔记"
      />
      <view class="count muted">{{ content.length }}/{{ MAX_NOTE_CONTENT }}</view>
      <view class="imgs">
        <view v-for="(u, i) in images" :key="u" class="img-box">
          <image class="img" :src="assetUrl(u)" mode="aspectFill" @click="preview(i)" />
          <text class="img-del" @click.stop="removeImage(i)">×</text>
        </view>
        <view v-for="n in uploading" :key="'up' + n" class="img-box img-loading">上传中</view>
        <view v-if="canAdd" class="img-box img-add" @click="addImages">+</view>
      </view>
    </view>
    <button class="submit" :disabled="saving || uploading > 0" @click="save">保存</button>
    <view v-if="existed" class="del" @click="remove">删除笔记</view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding-bottom: 48rpx;
}
.stem {
  font-size: 24rpx;
  line-height: 1.5;
  margin-bottom: 16rpx;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.input {
  width: 100%;
  min-height: 280rpx;
  font-size: 30rpx;
  line-height: 1.6;
  background: $lemon-bg;
  border-radius: 12rpx;
  padding: 20rpx;
  box-sizing: border-box;
}
.count {
  text-align: right;
  margin-top: 8rpx;
  font-size: 22rpx;
}
.imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 16rpx;
}
.img-box {
  position: relative;
  width: 180rpx;
  height: 180rpx;
  border-radius: 12rpx;
  overflow: hidden;
  background: $lemon-bg;
}
.img {
  width: 100%;
  height: 100%;
}
.img-del {
  position: absolute;
  top: 0;
  right: 0;
  width: 44rpx;
  height: 44rpx;
  line-height: 40rpx;
  text-align: center;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 32rpx;
  border-bottom-left-radius: 12rpx;
}
.img-add,
.img-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed $lemon-line;
  color: $lemon-muted;
  box-sizing: border-box;
}
.img-add {
  font-size: 60rpx;
}
.img-loading {
  font-size: 24rpx;
}
.submit {
  margin: 8rpx 32rpx 0;
  background: $lemon-primary;
  color: #fff;
  border-radius: 999rpx;
  font-size: 30rpx;
}
.del {
  margin-top: 32rpx;
  text-align: center;
  font-size: 26rpx;
  color: $lemon-danger;
}
</style>
