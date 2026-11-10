<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getGroups, kindLabel, type Group, type Kind, type Scope } from '@/api/collection'
import { isLoggedIn } from '@/utils/auth'

/**
 * 收藏集 / 错题集首页（两个 Tab 共用）：按模块切换，展示 题库 → 章节 分组和题数，
 * 可以整体练习、按章节练习，或进入列表逐题查看。
 */
const props = defineProps<{ kind: Kind }>()

const loggedIn = ref(false)
const groups = ref<Group[]>([])
const activeModuleId = ref<number>()
const loaded = ref(false)

const activeModule = computed(() => groups.value.find((g) => g.id === activeModuleId.value) ?? groups.value[0])
const total = computed(() => groups.value.reduce((sum, g) => sum + g.count, 0))
const label = computed(() => kindLabel[props.kind])

async function load() {
  loggedIn.value = isLoggedIn()
  if (!loggedIn.value) {
    groups.value = []
    loaded.value = true
    return
  }
  groups.value = await getGroups(props.kind)
  if (!groups.value.some((g) => g.id === activeModuleId.value)) {
    activeModuleId.value = groups.value[0]?.id
  }
  loaded.value = true
}

// 页面生命周期只在页面组件里可靠，由所在页面在 onShow / 下拉刷新时调用
onMounted(load)
defineExpose({ load })

function practice(scope: Scope) {
  const q = Object.entries(scope).filter(([, v]) => v).map(([k, v]) => `${k}=${v}`).join('&')
  uni.navigateTo({ url: `/pages/practice/index?kind=${props.kind}${q ? `&${q}` : ''}` })
}

function openList(scope: Scope, title: string) {
  const q = Object.entries(scope).filter(([, v]) => v).map(([k, v]) => `${k}=${v}`).join('&')
  uni.navigateTo({ url: `/pages/collection/list?kind=${props.kind}&title=${encodeURIComponent(title)}&${q}` })
}

const goLogin = () => uni.navigateTo({ url: '/pages/login/index' })
</script>

<template>
  <view>
    <view v-if="!loggedIn && loaded" class="card empty">
      <view class="empty-title">{{ label }}</view>
      <view class="muted">登录后查看{{ label }}</view>
      <button class="login-btn" @click="goLogin">去登录</button>
    </view>

    <view v-else-if="loaded && !groups.length" class="card empty">
      <view class="empty-icon">{{ kind === 'favorite' ? '☆' : '✓' }}</view>
      <view class="muted">
        {{ kind === 'favorite' ? '还没有收藏题目，做题时点“收藏”即可加入' : '暂无错题，继续保持！练习中答错的题会自动收录到这里' }}
      </view>
    </view>

    <template v-else-if="activeModule">
      <!-- 模块切换 -->
      <view v-if="groups.length > 1" class="tabs">
        <view
          v-for="g in groups"
          :key="g.id"
          class="tab"
          :class="{ active: g.id === activeModule.id }"
          @click="activeModuleId = g.id"
        >{{ g.name }} <text class="tab-count">{{ g.count }}</text></view>
      </view>

      <view class="card summary">
        <view>
          <view class="summary-num">{{ activeModule.count }}</view>
          <view class="muted">{{ activeModule.name }} · 共 {{ total }} 题</view>
        </view>
        <view class="summary-actions">
          <text class="link" @click="openList({ moduleId: activeModule.id }, `${activeModule.name}${label}`)">查看全部</text>
          <button class="practice-btn" @click="practice({ moduleId: activeModule.id })">
            {{ kind === 'wrong' ? '错题重练' : '开始练习' }}
          </button>
        </view>
      </view>
      <view v-if="kind === 'wrong'" class="muted hint">错题重练中连续答对达到设定次数，会自动移出错题集</view>

      <view v-for="bank in activeModule.children" :key="bank.id" class="card bank">
        <view class="bank-head" @click="openList({ bankId: bank.id }, bank.name)">
          <text class="bank-name">{{ bank.name }}</text>
          <text class="muted">{{ bank.count }} 题 ›</text>
        </view>
        <view v-for="ch in bank.children" :key="ch.id" class="chapter">
          <view class="chapter-name" @click="openList({ chapterId: ch.id }, ch.name)">
            <text>{{ ch.name }}</text>
            <text class="muted count">{{ ch.count }} 题</text>
          </view>
          <text class="mini-btn" @click="practice({ chapterId: ch.id })">练习</text>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.empty {
  text-align: center;
  padding: 80rpx 32rpx;
  line-height: 2;
}
.empty-title {
  font-size: 34rpx;
  font-weight: 600;
}
.empty-icon {
  font-size: 80rpx;
  color: $lemon-primary;
}
.login-btn {
  margin-top: 24rpx;
  width: 300rpx;
  background: $lemon-primary;
  color: #fff;
  border-radius: 999rpx;
  font-size: 28rpx;
}
.tabs {
  display: flex;
  gap: 16rpx;
  padding: 24rpx 24rpx 0;
}
.tab {
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: #fff;
  font-size: 28rpx;
}
.tab.active {
  background: $lemon-primary;
  color: #fff;
}
.tab-count {
  font-size: 22rpx;
  opacity: 0.8;
}
.summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.summary-num {
  font-size: 56rpx;
  font-weight: 700;
  color: $lemon-primary;
}
.summary-actions {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.link {
  color: #b88a00;
  font-size: 26rpx;
}
.practice-btn {
  margin: 0;
  background: $lemon-primary;
  color: #fff;
  border-radius: 999rpx;
  font-size: 28rpx;
  padding: 0 32rpx;
}
.hint {
  padding: 0 32rpx;
  margin-top: -8rpx;
}
.bank-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16rpx;
  border-bottom: 2rpx solid #f2f3f5;
}
.bank-name {
  font-size: 30rpx;
  font-weight: 600;
}
.chapter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
  border-bottom: 2rpx solid #f7f8fa;
}
.chapter:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.chapter-name {
  flex: 1;
  font-size: 28rpx;
}
.count {
  margin-left: 12rpx;
}
.mini-btn {
  font-size: 24rpx;
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
  background: $lemon-primary-light;
  color: #b88a00;
}
</style>
