<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import CollectionHome from '@/components/CollectionHome.vue'

const home = ref<InstanceType<typeof CollectionHome>>()
// 首次显示由组件 onMounted 加载；之后每次切回本页刷新
let shown = false
onShow(() => {
  if (shown) home.value?.load()
  shown = true
})
onPullDownRefresh(() => (home.value?.load() ?? Promise.resolve()).finally(() => uni.stopPullDownRefresh()))
</script>

<template>
  <CollectionHome ref="home" kind="wrong" />
</template>
