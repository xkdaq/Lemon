<script setup lang="ts">
import { computed, ref } from 'vue'
import { claimLegacy, lookupLegacy, type LegacyPreview } from '@/api/legacy'
import { confirmModal } from '@/utils/modal'

/**
 * 恢复老系统错题：输入当时的激活码或订单号 → 查到老账号 → 确认后把错题恢复进错题集
 */
const code = ref('')
const preview = ref<LegacyPreview | null>(null)
const busy = ref(false)
const done = ref<{ restored: number } | null>(null)

const unmatched = computed(() => (preview.value ? preview.value.wrongTotal - preview.value.restorable : 0))

function onInput() {
  // 改了输入就要重新查询
  preview.value = null
  done.value = null
}

async function lookup() {
  if (!code.value.trim() || busy.value) return
  busy.value = true
  try {
    preview.value = await lookupLegacy(code.value)
  } finally {
    busy.value = false
  }
}

async function claim() {
  const p = preview.value
  if (!p || busy.value) return
  const { confirm } = await confirmModal({
    title: '恢复错题',
    content: `将 ${p.restorable} 道错题恢复到当前账号的错题集。每个老账号只能恢复一次，确定吗？`,
    confirmText: '恢复',
  })
  if (!confirm) return
  busy.value = true
  try {
    done.value = await claimLegacy(code.value)
    preview.value = { ...p, state: 'mine' }
  } finally {
    busy.value = false
  }
}

const goWrong = () => uni.redirectTo({ url: '/pages/wrong/index' })
</script>

<template>
  <view class="page">
    <view class="card">
      <view class="title">找回老系统的错题</view>
      <view class="muted desc">
        输入你在老系统（猴子不吃柠檬）用过的<text class="em">激活码</text>或<text class="em">订单号</text>，
        我们会找到你当时的账号，把错题恢复到现在的错题集里，可以直接错题重练。
      </view>
      <input
        v-model="code"
        class="input"
        placeholder="激活码 / 订单号"
        maxlength="64"
        confirm-type="search"
        @input="onInput"
        @confirm="lookup"
      />
      <view class="btn btn-primary btn-block" :class="{ disabled: busy || !code.trim() }" @click="lookup">查询</view>
    </view>

    <view v-if="preview" class="card">
      <view class="row"><text class="muted">老账号</text><text>{{ preview.nickname || '（未设置昵称）' }}</text></view>
      <view class="row"><text class="muted">老错题</text><text>{{ preview.wrongTotal }} 题</text></view>
      <view class="row"><text class="muted">可恢复</text><text class="em">{{ preview.restorable }} 题</text></view>
      <view v-if="unmatched > 0" class="muted note">
        另有 {{ unmatched }} 题所在的题库（如 25 版、已下架题库）现在没有上线，暂时无法恢复。
      </view>

      <template v-if="done">
        <view class="success">已恢复 {{ done.restored }} 道错题</view>
        <view class="btn btn-primary btn-block" @click="goWrong">去错题集</view>
      </template>
      <view v-else-if="preview.state === 'available'" class="btn btn-primary btn-block action"
        :class="{ disabled: busy || !preview.restorable }" @click="preview.restorable && claim()">
        {{ preview.restorable ? `恢复 ${preview.restorable} 道错题` : '没有可恢复的错题' }}
      </view>
      <template v-else-if="preview.state === 'mine'">
        <view class="muted note">这个账号的错题已经恢复到你的错题集了。</view>
        <view class="btn btn-block action" @click="goWrong">去错题集</view>
      </template>
      <view v-else class="warn">这个账号的错题已被其他账号恢复。如果不是你本人操作，请在「意见反馈」联系我们。</view>
    </view>

    <view class="muted tips">
      <view>· 激活码一般是 10 位字母和数字组成，适用于在微店和闲鱼购买的用户。</view>
      <view>· 订单号只能恢复安卓手机在小程序里支付的订单：打开微信支付账单，找到当时支付的订单详情，复制「商户单号」或「交易单号」。</view>
      <view>· 一个老账号只能恢复到一个账号上，请登录你常用的账号再恢复。</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding-bottom: 48rpx;
}
.title {
  font-size: 34rpx;
  font-weight: 700;
  margin-bottom: 12rpx;
}
.desc {
  line-height: 1.7;
}
.em {
  color: $lemon-primary;
  font-weight: 600;
}
.input {
  margin: 28rpx 0 24rpx;
  height: 88rpx;
  padding: 0 24rpx;
  border: 1px solid $lemon-line;
  border-radius: 20rpx;
  background: $lemon-bg;
  font-size: 30rpx;
  letter-spacing: 1rpx;
}
.disabled {
  opacity: 0.5;
}
.row {
  display: flex;
  justify-content: space-between;
  padding: 14rpx 0;
  font-size: 30rpx;
}
.note {
  margin: 12rpx 0 4rpx;
  line-height: 1.6;
}
.action,
.success + .btn {
  margin-top: 24rpx;
}
.success {
  margin-top: 20rpx;
  text-align: center;
  color: $lemon-success;
  font-weight: 600;
}
.warn {
  margin-top: 16rpx;
  color: $lemon-danger;
  line-height: 1.6;
}
.tips {
  padding: 8rpx 40rpx;
  line-height: 1.8;
}
</style>
