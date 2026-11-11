<script setup lang="ts">
import { confirmModal } from '@/utils/modal'
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import * as api from '@/api/practice'
import { Mode, QType, isPracticeLike, modeByName, modeLabel, qTypeLabel, type QuestionItem, type Session } from '@/api/practice'
import { startPractice as startCollectionPractice, type Kind } from '@/api/collection'
import { formatSeconds, renderHtml } from '@/utils/html'
import { isLoggedIn } from '@/utils/auth'
import { ApiError } from '@/utils/request'

/**
 * 做题页。三种模式共用：
 * - 练习：选完即判（多选需点“确认”），答对自动下一题，答错停留看解析
 * - 背题：直接显示答案和解析
 * - 考试：选项可反复修改，自动保存草稿；倒计时结束自动交卷
 * 已结束的会话以只读方式打开（结果页“查看解析”进入）。
 */
const CHUNK = 20

const session = ref<Session>()
const questions = reactive<Record<number, QuestionItem>>({})
const index = ref(0)
/** 当前题的选择（未提交）或考试草稿 */
const selection = ref<string[]>([])
/** 主观题：练习模式下是否已展开参考答案 */
const showReference = ref(false)
const submitting = ref(false)
const cardVisible = ref(false)
const remaining = ref<number | null>(null)
let timer: ReturnType<typeof setInterval> | undefined
let progressTimer: ReturnType<typeof setTimeout> | undefined
let shownAt = Date.now()

const mode = computed(() => session.value?.mode ?? Mode.PRACTICE)
const readonly = computed(() => session.value?.status !== 0)
const ids = computed(() => session.value?.questionIds ?? [])
const currentId = computed(() => ids.value[index.value])
const current = computed<QuestionItem | undefined>(() => questions[currentId.value])
const myAnswer = computed(() => session.value?.answers[String(currentId.value)])
/** 背诵题：主观题没有参考答案，题干本身就是要背的内容（考试未交卷时拿不到解析，不按背诵题处理） */
const isCard = computed(() => current.value?.type === QType.SUBJECTIVE && !current.value.analysis
  && !(mode.value === Mode.EXAM && !readonly.value))
/** 是否显示答案和解析 */
const revealed = computed(() => !!current.value?.answer || isCard.value
  || (current.value?.type === QType.SUBJECTIVE && !!current.value?.analysis
    && (mode.value === Mode.RECITE || readonly.value || !!myAnswer.value || showReference.value)))
const isLast = computed(() => index.value >= ids.value.length - 1)

// ==================== 加载 ====================

type Query = Record<string, string | undefined>
let pageQuery: Query = {}
let loading = false

onLoad((query) => {
  pageQuery = (query ?? {}) as Query
  init()
})

// 从登录页回来时（首次加载因未登录失败），重新加载
onShow(() => {
  if (!session.value && !loading && isLoggedIn()) {
    init()
  }
})

async function init() {
  const query = pageQuery
  if (!isLoggedIn()) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  loading = true
  try {
    let s: Session
    if (query?.sessionId) {
      s = await api.getSession(Number(query.sessionId))
    } else if (query?.kind) {
      // 错题重练 / 收藏练习：按范围新建会话，可指定从哪道题开始
      const num = (v?: string) => (v ? Number(v) : undefined)
      s = await startCollectionPractice(query.kind as Kind, {
        moduleId: num(query.moduleId), bankId: num(query.bankId), courseId: num(query.courseId), chapterId: num(query.chapterId),
        firstQuestionId: num(query.questionId),
      })
    } else {
      const m = modeByName[query?.mode ?? 'practice'] ?? Mode.PRACTICE
      const section = query?.section ? decodeURIComponent(query.section) : undefined
      s = await api.startSession(Number(query?.chapterId), m, query?.restart === '1', section)
      if (s.status === 0 && (s.answeredCount > 0 || s.currentIndex > 0) && query?.restart !== '1') {
        const { confirm } = await confirmModal({
          title: '继续上次进度？',
          content: `上次做到第 ${s.currentIndex + 1} 题，已答 ${s.answeredCount} 题`,
          confirmText: '继续',
          cancelText: '重新开始',
        })
        if (!confirm) {
          s = await api.startSession(s.chapterId!, s.mode, true, s.section ?? undefined)
        }
      }
    }
    session.value = s
    uni.setNavigationBarTitle({ title: s.chapterName || modeLabel[s.mode] })
    const start = Number(query?.index ?? (s.status === 0 ? s.currentIndex : 0))
    await goTo(Math.min(Math.max(start, 0), s.questionIds.length - 1))
    startTimer()
  } catch (e) {
    // 未登录时请求层已跳转登录页，这里不能再返回，否则会把登录页关掉
    if (!(e instanceof ApiError && e.code === 401)) {
      setTimeout(() => uni.navigateBack(), 1200)
    }
  } finally {
    loading = false
  }
}

onUnload(() => {
  clearInterval(timer)
  clearTimeout(progressTimer)
})

/** 确保第 i 题所在的一批题目已加载 */
async function ensureLoaded(i: number) {
  const from = Math.floor(i / CHUNK) * CHUNK
  const batch = ids.value.slice(from, from + CHUNK).filter((id) => !questions[id])
  if (batch.length && session.value) {
    const list = await api.getQuestions(session.value.id, batch)
    list.forEach((q) => (questions[q.id] = q))
  }
}

async function goTo(i: number) {
  if (i < 0 || i >= ids.value.length) {
    return
  }
  await ensureLoaded(i)
  index.value = i
  showReference.value = false
  // 考试：恢复已保存的草稿；练习：恢复已提交的答案（用于高亮）
  selection.value = (myAnswer.value?.a ?? '').split('').filter((c) => /[A-Z]/.test(c))
  shownAt = Date.now()
  cardVisible.value = false
  // 预加载下一批
  if (i + 1 < ids.value.length) {
    ensureLoaded(i + 1)
  }
  if (!readonly.value) {
    clearTimeout(progressTimer)
    progressTimer = setTimeout(() => session.value && api.saveProgress(session.value.id, i), 800)
  }
}

// ==================== 练习 / 背题切换 ====================

/** 章节练习和背题可以互相切换（考试、错题重练、收藏练习、打卡、只读回看不行） */
const canSwitch = computed(() => !readonly.value && !!session.value?.chapterId
  && (mode.value === Mode.PRACTICE || mode.value === Mode.RECITE))
let switching = false

/**
 * 切到另一种模式：该章节（小节）在那个模式下有未完成的会话就续上，否则新开；停在当前这道题。
 * 题目详情要重新取（练习模式不提前返回答案）。
 */
async function switchMode(target: number) {
  const s = session.value
  if (!s || !canSwitch.value || target === s.mode || switching) return
  switching = true
  const qid = currentId.value
  const at = index.value
  try {
    const ns = await api.startSession(s.chapterId!, target, false, s.section ?? undefined)
    Object.keys(questions).forEach((k) => delete questions[Number(k)])
    session.value = ns
    const i = ns.questionIds.indexOf(qid)
    await goTo(i >= 0 ? i : Math.min(at, ns.questionIds.length - 1))
  } finally {
    switching = false
  }
}

// ==================== 作答 ====================

function onTapOption(key: string) {
  const q = current.value
  if (!q || readonly.value || mode.value === Mode.RECITE || submitting.value) {
    return
  }
  if (mode.value === Mode.EXAM) {
    selection.value = q.type === QType.MULTIPLE ? toggle(selection.value, key) : [key]
    saveExamDraft()
    return
  }
  if (myAnswer.value) {
    return // 练习模式已作答，不能改
  }
  if (q.type === QType.MULTIPLE) {
    selection.value = toggle(selection.value, key)
  } else {
    selection.value = [key]
    submitPractice(key)
  }
}

function toggle(list: string[], key: string) {
  return list.includes(key) ? list.filter((k) => k !== key) : [...list, key].sort()
}

async function submitPractice(answer: string) {
  const s = session.value
  const q = current.value
  if (!s || !q) return
  submitting.value = true
  try {
    const r = await api.submitAnswer(s.id, q.id, answer, Date.now() - shownAt)
    s.answers[String(q.id)] = { a: q.type === QType.SUBJECTIVE ? answer : answer.split('').sort().join(''), c: r.correct ? 1 : 0 }
    s.answeredCount = r.answeredCount
    s.correctCount = r.correctCount
    q.answer = r.answer ?? undefined
    q.analysis = r.analysis ?? undefined
    // 答对自动下一题；答错停留看解析
    if (r.correct && q.type !== QType.SUBJECTIVE && !isLast.value) {
      setTimeout(() => {
        if (currentId.value === q.id) goTo(index.value + 1)
      }, 600)
    }
  } finally {
    submitting.value = false
  }
}

let draftChain: Promise<unknown> = Promise.resolve()
/** 考试草稿按顺序保存，避免快速切换选项时请求乱序 */
function saveExamDraft() {
  const s = session.value
  const q = current.value
  if (!s || !q) return
  const answer = selection.value.join('')
  if (answer) {
    s.answers[String(q.id)] = { a: answer, c: null }
  } else {
    delete s.answers[String(q.id)]
  }
  draftChain = draftChain.then(() => api.submitAnswer(s.id, q.id, answer)).then((r) => {
    s.answeredCount = r.answeredCount
  }).catch(() => undefined)
}

// ==================== 选项样式 ====================

function optionClass(key: string) {
  const q = current.value
  if (!q) return ''
  const correctKeys = q.answer ?? ''
  const mine = myAnswer.value?.a ?? ''
  if (revealed.value && q.answer) {
    if (correctKeys.includes(key)) return 'is-correct'
    if (mine.includes(key) || selection.value.includes(key)) return 'is-wrong'
    return ''
  }
  return selection.value.includes(key) ? 'is-selected' : ''
}

/** 答案展示：判断题显示“正确 / 错误”，其余显示字母 */
function answerText(q: QuestionItem, keys = q.answer) {
  if (q.type === QType.JUDGE) {
    return q.options.find((o) => o.key === keys)?.content ?? keys
  }
  return keys
}

// ==================== 收藏 / 答题卡 / 交卷 ====================

async function toggleFavorite() {
  const q = current.value
  if (!q) return
  if (q.favorited) {
    await api.unfavorite(q.id)
  } else {
    await api.favorite(q.id)
  }
  q.favorited = !q.favorited
  uni.showToast({ title: q.favorited ? '已收藏' : '已取消收藏', icon: 'none' })
}

function reportError() {
  if (current.value && !current.value.deleted) {
    uni.navigateTo({ url: `/pages/feedback/index?questionId=${current.value.id}` })
  }
}

/** 答题卡格子状态 */
function cardClass(id: number, i: number) {
  const a = session.value?.answers[String(id)]
  const cls = [i === index.value ? 'is-current' : '']
  if (a) {
    cls.push(a.c === 1 ? 'is-correct' : a.c === 0 ? 'is-wrong' : 'is-answered')
  }
  return cls.join(' ')
}

async function finish() {
  const s = session.value
  if (!s || submitting.value) return
  if (readonly.value) {
    uni.redirectTo({ url: `/pages/practice/result?sessionId=${s.id}` })
    return
  }
  const unanswered = s.questionIds.length - Object.keys(s.answers).length
  if (mode.value !== Mode.RECITE) {
    const { confirm } = await confirmModal({
      title: mode.value === Mode.EXAM ? '确认交卷？' : '结束练习？',
      content: unanswered > 0 ? `还有 ${unanswered} 题未作答` : '已全部作答',
      confirmText: mode.value === Mode.EXAM ? '交卷' : '结束',
    })
    if (!confirm) return
  }
  await doSubmit()
}

async function doSubmit() {
  const s = session.value
  if (!s) return
  submitting.value = true
  try {
    clearInterval(timer)
    await draftChain
    await api.submitSession(s.id)
    if (mode.value === Mode.RECITE) {
      uni.navigateBack()
    } else {
      uni.redirectTo({ url: `/pages/practice/result?sessionId=${s.id}` })
    }
  } finally {
    submitting.value = false
  }
}

function startTimer() {
  const s = session.value
  if (!s || s.mode !== Mode.EXAM || s.status !== 0 || s.remainingSeconds == null) return
  const deadline = Date.now() + s.remainingSeconds * 1000
  const tick = () => {
    remaining.value = Math.round((deadline - Date.now()) / 1000)
    if (remaining.value <= 0) {
      clearInterval(timer)
      uni.showToast({ title: '考试时间到，自动交卷', icon: 'none' })
      doSubmit()
    }
  }
  tick()
  timer = setInterval(tick, 1000)
}

// ==================== 左右滑动切题 ====================

let touchX = 0
let touchY = 0
function onTouchStart(e: TouchEvent) {
  touchX = e.changedTouches[0].clientX
  touchY = e.changedTouches[0].clientY
}
function onTouchEnd(e: TouchEvent) {
  const dx = e.changedTouches[0].clientX - touchX
  const dy = e.changedTouches[0].clientY - touchY
  if (Math.abs(dx) > 70 && Math.abs(dx) > Math.abs(dy) * 1.5) {
    goTo(index.value + (dx < 0 ? 1 : -1))
  }
}
</script>

<template>
  <view v-if="session" class="practice">
    <!-- 顶部信息 -->
    <view class="topbar">
      <view v-if="canSwitch" class="mode-switch">
        <text class="seg" :class="{ on: mode === Mode.PRACTICE }" @click="switchMode(Mode.PRACTICE)">练习</text>
        <text class="seg" :class="{ on: mode === Mode.RECITE }" @click="switchMode(Mode.RECITE)">背题</text>
      </view>
      <text v-else class="mode-tag" :class="`mode-${session.mode}`">{{ modeLabel[session.mode] }}</text>
      <text v-if="readonly" class="muted">（已结束 · 查看解析）</text>
      <text class="progress">{{ index + 1 }} / {{ ids.length }}</text>
      <text v-if="remaining !== null" class="timer" :class="{ urgent: remaining < 300 }">⏱ {{ formatSeconds(remaining) }}</text>
    </view>

    <!-- 题目 -->
    <view class="question-wrap" @touchstart="onTouchStart" @touchend="onTouchEnd">
      <view v-if="current?.deleted" class="card muted">该题已被删除</view>
      <view v-else-if="current" class="card question">
        <view class="q-head">
          <text class="q-type">{{ qTypeLabel[current.type] }}</text>
          <text v-if="mode === Mode.EXAM && current.score" class="muted">{{ current.score }} 分</text>
        </view>
        <rich-text class="stem" :nodes="renderHtml(current.stem)" />

        <!-- 客观题选项 -->
        <view v-if="current.type !== QType.SUBJECTIVE" class="options">
          <view
            v-for="o in current.options"
            :key="o.key"
            class="option"
            :class="optionClass(o.key)"
            @click="onTapOption(o.key)"
          >
            <text class="option-key">{{ current.type === QType.JUDGE ? (o.key === 'A' ? '✓' : '✗') : o.key }}</text>
            <text class="option-content">{{ o.content }}</text>
          </view>
        </view>

        <!-- 多选确认 -->
        <button
          v-if="current.type === QType.MULTIPLE && mode !== Mode.EXAM && mode !== Mode.RECITE && !readonly && !myAnswer"
          class="primary-btn"
          :disabled="!selection.length || submitting"
          @click="submitPractice(selection.join(''))"
        >确认答案</button>

        <!-- 主观题 -->
        <view v-if="current.type === QType.SUBJECTIVE" class="subjective">
          <view v-if="mode === Mode.EXAM && !readonly" class="tip">主观题不计分，请在纸上作答，交卷后可查看参考答案。</view>
          <button
            v-else-if="isPracticeLike(mode) && !readonly && !myAnswer && !showReference && !isCard"
            class="primary-btn ghost"
            @click="showReference = true"
          >查看参考答案</button>
        </view>

        <!-- 答案与解析 -->
        <view v-if="revealed" class="analysis">
          <view v-if="current.type !== QType.SUBJECTIVE" class="answer-line">
            <text>正确答案：</text><text class="right">{{ answerText(current) }}</text>
            <template v-if="myAnswer?.a">
              <text class="gap">你的答案：</text>
              <text :class="myAnswer.c === 1 ? 'right' : myAnswer.c === 0 ? 'wrong' : ''">{{ answerText(current, myAnswer.a) }}</text>
            </template>
          </view>
          <text v-if="isCard" class="muted">背诵题：熟读上面的内容，背下来后自评</text>
          <template v-else>
            <view class="analysis-title">{{ current.type === QType.SUBJECTIVE ? '参考答案' : '解析' }}</view>
            <rich-text v-if="current.analysis" :nodes="renderHtml(current.analysis)" />
            <text v-else class="muted">暂无解析</text>
          </template>

          <!-- 主观题自评 -->
          <view v-if="current.type === QType.SUBJECTIVE && isPracticeLike(mode) && !readonly && !myAnswer" class="self-assess">
            <text class="muted">{{ isCard ? '背下来了吗？' : '对照参考答案，你掌握了吗？' }}</text>
            <view class="self-btns">
              <button class="assess-btn wrong" :disabled="submitting" @click="submitPractice('0')">没掌握</button>
              <button class="assess-btn right" :disabled="submitting" @click="submitPractice('1')">掌握了</button>
            </view>
          </view>
          <view v-if="current.type === QType.SUBJECTIVE && myAnswer" class="muted assessed">
            自评：{{ myAnswer.a === '1' ? '掌握了' : '没掌握（已加入错题集）' }}
          </view>
        </view>
      </view>
      <view v-else class="card muted loading">加载中…</view>
    </view>

    <!-- 底部操作 -->
    <view class="bottombar">
      <view class="bar-item" @click="toggleFavorite">
        <text class="icon" :class="{ fav: current?.favorited }">{{ current?.favorited ? '★' : '☆' }}</text>
        <text>收藏</text>
      </view>
      <view class="bar-item" @click="reportError">
        <text class="icon">✎</text>
        <text>纠错</text>
      </view>
      <view class="bar-item" @click="cardVisible = true">
        <text class="icon">▦</text>
        <text>答题卡</text>
      </view>
      <button class="nav-btn" :disabled="index === 0" @click="goTo(index - 1)">上一题</button>
      <button v-if="!isLast" class="nav-btn primary" @click="goTo(index + 1)">下一题</button>
      <button v-else class="nav-btn primary" :disabled="submitting" @click="finish">
        {{ readonly ? '查看结果' : mode === Mode.EXAM ? '交卷' : mode === Mode.RECITE ? '完成' : '完成练习' }}
      </button>
    </view>

    <!-- 答题卡 -->
    <view v-if="cardVisible" class="mask" @click="cardVisible = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <text class="sheet-title">答题卡</text>
          <text class="muted">已答 {{ Object.keys(session.answers).length }} / {{ ids.length }}</text>
        </view>
        <view class="legend muted">
          <template v-if="mode === Mode.EXAM && !readonly"><text class="dot is-answered" />已答</template>
          <template v-else><text class="dot is-correct" />答对 <text class="dot is-wrong" />答错</template>
          <text class="dot" />未答
        </view>
        <scroll-view scroll-y class="grid-scroll">
          <view class="grid">
            <view v-for="(id, i) in ids" :key="id" class="cell" :class="cardClass(id, i)" @click="goTo(i)">{{ i + 1 }}</view>
          </view>
        </scroll-view>
        <button v-if="!readonly && mode !== Mode.RECITE" class="primary-btn" :disabled="submitting" @click="finish">
          {{ mode === Mode.EXAM ? '交卷' : '结束练习' }}
        </button>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.practice {
  min-height: 100vh;
  padding-bottom: 140rpx;
  box-sizing: border-box;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 28rpx;
  background: $lemon-surface;
  font-size: 26rpx;
}
.mode-tag {
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  background: $lemon-primary-light;
  color: $lemon-primary;
  font-size: 22rpx;
}
.mode-switch {
  display: flex;
  padding: 4rpx;
  border-radius: 999rpx;
  background: $lemon-bg;
  border: 1px solid $lemon-line;
}
.seg {
  padding: 6rpx 24rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: $lemon-muted;
}
.seg.on {
  background: $lemon-primary;
  color: #fff;
}
.mode-tag.mode-3 {
  background: $lemon-bg;
  color: $lemon-key;
}
.mode-tag.mode-2 {
  background: $lemon-primary-light;
  color: $lemon-primary;
}
.progress {
  margin-left: auto;
  color: $lemon-muted;
}
.timer {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.timer.urgent {
  color: $lemon-danger;
}
.question-wrap {
  min-height: 60vh;
}
.q-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.q-type {
  font-size: 24rpx;
  color: $lemon-primary;
  background: $lemon-primary-light;
  padding: 2rpx 12rpx;
  border-radius: 6rpx;
}
.stem {
  font-size: 32rpx;
  line-height: 1.7;
}
.options {
  margin-top: 28rpx;
}
.option {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  border: 1px solid $lemon-line;
  border-radius: 16rpx;
  font-size: 30rpx;
  line-height: 1.5;
}
.option-key {
  flex-shrink: 0;
  width: 48rpx;
  height: 48rpx;
  line-height: 48rpx;
  text-align: center;
  border-radius: 50%;
  background: $lemon-line;
  font-weight: 600;
  font-size: 26rpx;
}
.option.is-selected {
  border-color: $lemon-primary;
  background: $lemon-primary-light;
}
.option.is-selected .option-key {
  background: $lemon-primary;
  color: #fff;
}
.option.is-correct {
  border-color: $lemon-success;
  background: $lemon-success-soft;
}
.option.is-correct .option-key {
  background: $lemon-success;
  color: #fff;
}
.option.is-wrong {
  border-color: $lemon-danger;
  background: $lemon-danger-soft;
}
.option.is-wrong .option-key {
  background: $lemon-danger;
  color: #fff;
}
.primary-btn {
  margin-top: 16rpx;
  background: $lemon-primary;
  color: #fff;
  border-radius: 999rpx;
  font-size: 30rpx;
}
.primary-btn.ghost {
  background: $lemon-primary-light;
  color: $lemon-primary;
}
.tip {
  margin-top: 24rpx;
  padding: 20rpx;
  background: $lemon-bg;
  border-radius: 12rpx;
  color: $lemon-muted;
  font-size: 26rpx;
}
.analysis {
  margin-top: 32rpx;
  padding-top: 24rpx;
  border-top: 1px dashed $lemon-line;
  font-size: 28rpx;
  line-height: 1.7;
}
.answer-line {
  margin-bottom: 12rpx;
}
.right {
  color: $lemon-success;
  font-weight: 600;
}
.wrong {
  color: $lemon-danger;
  font-weight: 600;
}
.gap {
  margin-left: 32rpx;
}
.analysis-title {
  font-weight: 600;
  margin: 8rpx 0;
}
.self-assess {
  margin-top: 28rpx;
  text-align: center;
}
.self-btns {
  display: flex;
  gap: 24rpx;
  margin-top: 16rpx;
}
.assess-btn {
  flex: 1;
  border-radius: 999rpx;
  font-size: 28rpx;
  color: #fff;
}
.assess-btn.right {
  background: $lemon-success;
}
.assess-btn.wrong {
  background: $lemon-danger;
}
.assessed {
  margin-top: 16rpx;
}
.loading {
  text-align: center;
  padding: 120rpx 0;
}
.bottombar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  /* 宽屏下与页面内容同宽居中（pages.json maxWidth 750） */
  max-width: 750px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: $lemon-surface;
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.05);
  box-sizing: border-box;
  z-index: 10;
}
.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 20rpx;
  color: $lemon-muted;
  width: 80rpx;
}
.icon {
  font-size: 36rpx;
  line-height: 1.2;
}
.icon.fav {
  color: $lemon-primary;
}
.nav-btn {
  flex: 1;
  margin: 0;
  font-size: 28rpx;
  border-radius: 999rpx;
  background: $lemon-line;
  color: $lemon-text;
}
.nav-btn.primary {
  background: $lemon-primary;
  color: #fff;
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 20;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.sheet {
  width: 100%;
  max-width: 750px;
  background: $lemon-surface;
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.sheet-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 600;
}
.legend {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin: 16rpx 0;
}
.dot {
  display: inline-block;
  width: 20rpx;
  height: 20rpx;
  border-radius: 6rpx;
  background: $lemon-line;
  margin-left: 16rpx;
}
.grid-scroll {
  max-height: 50vh;
}
.grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16rpx;
  padding: 8rpx 0 24rpx;
}
.cell {
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 12rpx;
  background: $lemon-line;
  font-size: 26rpx;
}
.cell.is-current,
.dot.is-current {
  outline: 3rpx solid $lemon-primary;
}
.is-correct.cell,
.dot.is-correct {
  background: $lemon-success;
  color: #fff;
}
.is-wrong.cell,
.dot.is-wrong {
  background: $lemon-danger;
  color: #fff;
}
.is-answered.cell,
.dot.is-answered {
  background: $lemon-primary;
  color: #fff;
}
</style>
