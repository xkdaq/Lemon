<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { Mode, getResult, getSession, modeLabel, type Result } from '@/api/practice'
import { formatSeconds } from '@/utils/html'

const result = ref<Result>()
const firstWrongIndex = ref(-1)
/** 按小节练习时的小节，再练一次沿用 */
let section: string | null | undefined

onLoad(async (query) => {
  const id = Number(query?.sessionId)
  const [r, s] = await Promise.all([getResult(id), getSession(id)])
  result.value = r
  section = s.section
  firstWrongIndex.value = r.wrongQuestionIds.length ? s.questionIds.indexOf(r.wrongQuestionIds[0]) : -1
  uni.setNavigationBarTitle({ title: r.mode === Mode.EXAM ? '考试成绩' : `${modeLabel[r.mode].replace('模式', '')}结果` })
})

const isExam = computed(() => result.value?.mode === Mode.EXAM)
/** 正确率按已判题目计算（考试中主观题不判） */
const accuracy = computed(() => {
  const r = result.value
  if (!r) return 0
  const judged = isExam.value ? r.total - r.subjectiveCount : r.answered
  return judged > 0 ? Math.round((r.correct / judged) * 100) : 0
})

function review(index = 0) {
  uni.navigateTo({ url: `/pages/practice/index?sessionId=${result.value!.sessionId}&index=${index}` })
}

/** 错题重练 / 收藏练习没有章节，回到列表重新发起 */
const canRestart = computed(() => !!result.value?.chapterId)

function again() {
  const r = result.value!
  const mode = r.mode === Mode.EXAM ? 'exam' : 'practice'
  const sec = section ? `&section=${encodeURIComponent(section)}` : ''
  uni.redirectTo({ url: `/pages/practice/index?chapterId=${r.chapterId}&mode=${mode}&restart=1${sec}` })
}

function back() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/index/index' }) })
}
</script>

<template>
  <view v-if="result" class="result">
    <view class="card hero">
      <view class="muted">{{ result.chapterName ? `${result.chapterName} · ` : '' }}{{ modeLabel[result.mode] }}</view>
      <view v-if="isExam" class="big">
        <text class="num">{{ result.score ?? 0 }}</text><text class="unit"> / {{ result.totalScore }} 分</text>
      </view>
      <view v-else class="big">
        <text class="num">{{ accuracy }}</text><text class="unit">% 正确率</text>
      </view>
      <view v-if="isExam && result.subjectiveCount" class="muted note">
        含 {{ result.subjectiveCount }} 道主观题未计分，请对照参考答案自查
      </view>
    </view>

    <view class="card stats">
      <view class="stat"><view class="v">{{ result.total }}</view><view class="muted">总题数</view></view>
      <view class="stat"><view class="v">{{ result.answered }}</view><view class="muted">已作答</view></view>
      <view class="stat"><view class="v right">{{ result.correct }}</view><view class="muted">答对</view></view>
      <view class="stat"><view class="v wrong">{{ result.wrongQuestionIds.length }}</view><view class="muted">答错</view></view>
      <view class="stat"><view class="v">{{ formatSeconds(result.costSeconds) }}</view><view class="muted">用时</view></view>
    </view>

    <view class="actions">
      <button v-if="firstWrongIndex >= 0" class="btn primary" @click="review(firstWrongIndex)">查看错题</button>
      <button class="btn" @click="review(0)">查看全部解析</button>
      <button v-if="canRestart" class="btn" @click="again">{{ isExam ? '重新考试' : '再练一次' }}</button>
      <button class="btn plain" @click="back">返回</button>
    </view>
    <view v-if="result.wrongQuestionIds.length" class="muted foot">答错的题已自动加入错题集</view>
    <view v-else-if="result.mode === Mode.WRONG && result.correct" class="muted foot">连续答对达到设定次数的题会自动移出错题集</view>
    <view v-else-if="result.mode === Mode.PUNCH" class="muted foot">打卡已完成，返回活动页可查看打卡日历</view>
  </view>
</template>

<style lang="scss" scoped>
.hero {
  text-align: center;
  padding: 48rpx 28rpx;
}
.big {
  margin-top: 20rpx;
}
.num {
  font-size: 96rpx;
  font-weight: 700;
  color: $lemon-primary;
  font-variant-numeric: tabular-nums;
}
.unit {
  font-size: 28rpx;
  color: $lemon-muted;
}
.note {
  margin-top: 12rpx;
}
.stats {
  display: flex;
}
.stat {
  flex: 1;
  text-align: center;
}
.v {
  font-size: 34rpx;
  font-weight: 600;
  margin-bottom: 6rpx;
  font-variant-numeric: tabular-nums;
}
.right {
  color: $lemon-success;
}
.wrong {
  color: $lemon-danger;
}
.actions {
  padding: 8rpx 24rpx;
}
.btn {
  margin-bottom: 20rpx;
  border-radius: 999rpx;
  font-size: 30rpx;
  background: $lemon-surface;
  color: $lemon-text;
}
.btn.primary {
  background: $lemon-primary;
  color: #fff;
}
.btn.plain {
  background: transparent;
  color: $lemon-muted;
}
.foot {
  text-align: center;
}
</style>
