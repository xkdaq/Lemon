<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import CollectionHome from '@/components/CollectionHome.vue'
import { usePageTheme } from '@/utils/settings'

/** 小程序：页面配色（深浅色 / 后台配色），见 utils/settings.ts */
const themeStyle = usePageTheme()

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
  <!-- #ifdef MP-WEIXIN -->
  <page-meta :page-style="themeStyle" />
  <!-- #endif -->
  <CollectionHome ref="home" kind="wrong" />
</template>
