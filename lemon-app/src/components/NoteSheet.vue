<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { MAX_NOTE_CONTENT, MAX_NOTE_IMAGES, deleteNote, saveNote, uploadNoteImage, type Note } from '@/api/note'
import { assetUrl } from '@/utils/html'
import { compressImage } from '@/utils/image'
import { confirmModal } from '@/utils/modal'

/**
 * 写 / 编辑笔记的底部弹框：文字 + 最多 6 张图片，只有自己看得到。
 * 打开时用传入的笔记初始化；保存 / 删除后 emit saved（删除时为 null）。
 */
const props = defineProps<{ questionId: number | null; note?: Note | null }>()
const emit = defineEmits<{ close: []; saved: [note: Note | null] }>()

const content = ref('')
const images = ref<string[]>([])
const uploading = ref(0)
const saving = ref(false)

const visible = computed(() => props.questionId != null)
const existed = computed(() => !!props.note)
const canAdd = computed(() => images.value.length + uploading.value < MAX_NOTE_IMAGES)
const dirty = computed(() => content.value !== (props.note?.content ?? '')
  || images.value.join() !== (props.note?.images ?? []).join())

watch(
  () => props.questionId,
  (id) => {
    if (id == null) return
    content.value = props.note?.content ?? ''
    images.value = [...(props.note?.images ?? [])]
  },
  { immediate: true },
)

async function close() {
  if (uploading.value) return uni.showToast({ title: '图片还在上传', icon: 'none' })
  if (dirty.value) {
    const { confirm } = await confirmModal({ title: '放弃修改？', content: '这次写的内容不会保存', confirmText: '放弃' })
    if (!confirm) return
  }
  emit('close')
}

async function addImages() {
  const left = MAX_NOTE_IMAGES - images.value.length - uploading.value
  const r = await uni.chooseImage({ count: left, sizeType: ['compressed'] }).catch(() => null)
  if (!r) return
  for (const f of (r.tempFiles as { path: string; size: number }[]).slice(0, left)) {
    uploading.value++
    try {
      images.value.push(await uploadNoteImage(await compressImage(f.path, f.size)))
    } catch {
      // 上传失败已提示
    } finally {
      uploading.value--
    }
  }
}

function preview(i: number) {
  uni.previewImage({ urls: images.value.map(assetUrl), current: i })
}

async function save() {
  if (props.questionId == null) return
  if (uploading.value) return uni.showToast({ title: '图片还在上传', icon: 'none' })
  if (!content.value.trim() && !images.value.length) return uni.showToast({ title: '写点什么吧', icon: 'none' })
  saving.value = true
  try {
    const note = await saveNote(props.questionId, content.value, images.value)
    uni.showToast({ title: '已保存', icon: 'none' })
    emit('saved', note)
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (props.questionId == null) return
  const { confirm } = await confirmModal({ title: '删除这条笔记？', confirmText: '删除' })
  if (!confirm) return
  await deleteNote(props.questionId)
  uni.showToast({ title: '已删除', icon: 'none' })
  emit('saved', null)
}
</script>

<template>
  <view v-if="visible" class="sheet-mask" @click="close" @touchmove.stop>
    <view class="sheet" @click.stop>
      <view class="sheet-head">
        <text class="sheet-title">笔记</text>
        <text class="close" @click="close">✕</text>
      </view>
      <scroll-view scroll-y class="body">
        <textarea
          v-model="content"
          class="input"
          :maxlength="MAX_NOTE_CONTENT"
          :show-confirm-bar="false"
          placeholder="写下这道题的笔记"
        />
        <view class="count muted">{{ content.length }}/{{ MAX_NOTE_CONTENT }}</view>
        <view class="imgs">
          <view v-for="(u, i) in images" :key="u" class="img-box">
            <image class="img" :src="assetUrl(u)" mode="aspectFill" @click="preview(i)" />
            <text class="img-del" @click.stop="images.splice(i, 1)">×</text>
          </view>
          <view v-for="n in uploading" :key="'up' + n" class="img-box img-loading">上传中</view>
          <view v-if="canAdd" class="img-box img-add" @click="addImages">+</view>
        </view>
      </scroll-view>
      <view class="actions">
        <text v-if="existed" class="del" @click="remove">删除</text>
        <button class="submit" :disabled="saving || uploading > 0" @click="save">保存</button>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.sheet-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.sheet {
  width: 100%;
  max-width: 750px;
  box-sizing: border-box;
  background: $lemon-bg;
  border-radius: 32rpx 32rpx 0 0;
  padding: 28rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  max-height: 85vh;
}
.sheet-head {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  margin-bottom: 20rpx;
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 700;
}
.close {
  position: absolute;
  right: 0;
  padding: 8rpx 12rpx;
  font-size: 32rpx;
  color: $lemon-muted;
}
.body {
  flex: 1;
  min-height: 0;
}
.input {
  width: 100%;
  height: 320rpx;
  font-size: 30rpx;
  line-height: 1.6;
  background: $lemon-surface;
  border: 1px solid $lemon-line;
  border-radius: 16rpx;
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
  margin-top: 12rpx;
}
.img-box {
  position: relative;
  width: 150rpx;
  height: 150rpx;
  border-radius: 12rpx;
  overflow: hidden;
  background: $lemon-surface;
}
.img {
  width: 100%;
  height: 100%;
}
.img-del {
  position: absolute;
  top: 0;
  right: 0;
  width: 40rpx;
  height: 40rpx;
  line-height: 36rpx;
  text-align: center;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 30rpx;
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
  font-size: 56rpx;
}
.img-loading {
  font-size: 22rpx;
}
.actions {
  display: flex;
  align-items: center;
  gap: 32rpx;
  margin-top: 24rpx;
}
.del {
  font-size: 28rpx;
  color: $lemon-danger;
  padding: 0 8rpx;
}
.submit {
  flex: 1;
  margin: 0;
  background: $lemon-primary;
  color: #fff;
  border-radius: 999rpx;
  font-size: 30rpx;
}
</style>
