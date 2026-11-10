<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getBanks } from '@/api/content'
import { BankType, type QuestionBank } from '@/api/types'

const banks = ref<QuestionBank[]>([])
const loaded = ref(false)

onLoad(async (query) => {
  uni.setNavigationBarTitle({ title: decodeURIComponent(query?.name ?? '题库') })
  banks.value = await getBanks(Number(query?.moduleId))
  loaded.value = true
})

function openBank(b: QuestionBank) {
  uni.navigateTo({ url: `/pages/bank/detail?bankId=${b.id}` })
}
</script>

<template>
  <view>
    <view v-for="b in banks" :key="b.id" class="card bank" @click="openBank(b)">
      <view class="bank-head">
        <text class="bank-name">{{ b.name }}</text>
        <text class="tag" :class="b.type === BankType.PAPER ? 'tag-paper' : ''">
          {{ b.type === BankType.PAPER ? '模拟卷' : '章节练习' }}
        </text>
      </view>
      <view v-if="b.intro" class="muted intro">{{ b.intro }}</view>
      <view class="muted">共 {{ b.questionCount }} 题</view>
    </view>
    <view v-if="loaded && !banks.length" class="empty muted">暂无题库，敬请期待</view>
  </view>
</template>

<style lang="scss" scoped>
.bank-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.bank-name {
  font-size: 32rpx;
  font-weight: 600;
}
.tag {
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: #e8f3ff;
  color: #3a7bd5;
}
.tag-paper {
  background: #fff1e8;
  color: #e37318;
}
.intro {
  margin-bottom: 8rpx;
}
.empty {
  text-align: center;
  padding: 120rpx 0;
}
</style>
