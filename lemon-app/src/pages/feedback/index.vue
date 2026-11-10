<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { myFeedback, submitFeedback, type Feedback } from '@/api/mine'

/** 意见反馈 / 题目纠错（从做题页“纠错”进入时带 questionId）+ 我的反馈记录 */
const questionId = ref<number>()
const type = ref(2)
const content = ref('')
const submitting = ref(false)
const list = ref<Feedback[]>([])
const total = ref(0)
let pageNum = 1

onLoad((query) => {
  if (query?.questionId) {
    questionId.value = Number(query.questionId)
    type.value = 1
    uni.setNavigationBarTitle({ title: '题目纠错' })
  } else {
    uni.setNavigationBarTitle({ title: '意见反馈' })
  }
  load(1)
})

onReachBottom(() => {
  if (list.value.length < total.value) load(pageNum + 1)
})

async function load(page: number) {
  const res = await myFeedback(page)
  list.value = page === 1 ? res.list : [...list.value, ...res.list]
  total.value = res.total
  pageNum = page
}

async function submit() {
  const text = content.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写内容', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await submitFeedback({ type: type.value, questionId: type.value === 1 ? questionId.value : undefined, content: text })
    uni.showToast({ title: '已提交，感谢反馈', icon: 'success' })
    content.value = ''
    load(1)
    if (questionId.value) {
      setTimeout(() => uni.navigateBack(), 800)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view>
    <view class="card">
      <view v-if="questionId" class="muted q-tip">正在纠错题目 #{{ questionId }}</view>
      <textarea
        v-model="content"
        class="input"
        maxlength="1000"
        :placeholder="type === 1 ? '请描述题目的问题，如：答案错误、选项有歧义、解析不对……' : '有什么建议或遇到的问题，告诉我们吧'"
      />
      <view class="count muted">{{ content.length }}/1000</view>
      <button class="submit" :disabled="submitting" @click="submit">提交</button>
    </view>

    <view v-if="list.length" class="section muted">我的反馈</view>
    <view v-for="f in list" :key="f.id" class="card fb">
      <view class="fb-head">
        <text class="fb-type">{{ f.type === 1 ? '题目纠错' : '意见建议' }}</text>
        <text class="muted">{{ f.createdAt }}</text>
        <text class="status" :class="{ done: f.status === 1 }">{{ f.status === 1 ? '已处理' : '处理中' }}</text>
      </view>
      <view v-if="f.stemText" class="muted stem">题目：{{ f.stemText }}</view>
      <view class="fb-content">{{ f.content }}</view>
      <view v-if="f.reply" class="reply"><text class="reply-label">回复：</text>{{ f.reply }}</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.q-tip {
  margin-bottom: 12rpx;
}
.input {
  width: 100%;
  height: 240rpx;
  font-size: 28rpx;
  line-height: 1.6;
  background: $lemon-bg;
  border-radius: 12rpx;
  padding: 20rpx;
  box-sizing: border-box;
}
.count {
  text-align: right;
  margin-top: 8rpx;
}
.submit {
  margin-top: 20rpx;
  background: $lemon-primary;
  color: #fff;
  border-radius: 999rpx;
  font-size: 30rpx;
}
.section {
  padding: 16rpx 32rpx 0;
}
.fb-head {
  display: flex;
  align-items: center;
  gap: 16rpx;
  font-size: 24rpx;
}
.fb-type {
  font-weight: 600;
  font-size: 26rpx;
}
.status {
  margin-left: auto;
  color: $lemon-key;
}
.status.done {
  color: $lemon-success;
}
.stem {
  margin-top: 10rpx;
}
.fb-content {
  margin-top: 12rpx;
  font-size: 28rpx;
  line-height: 1.6;
}
.reply {
  margin-top: 16rpx;
  padding: 16rpx 20rpx;
  background: $lemon-primary-light;
  border-radius: 12rpx;
  font-size: 26rpx;
  line-height: 1.6;
}
.reply-label {
  color: $lemon-primary;
  font-weight: 600;
}
</style>
