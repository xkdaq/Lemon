<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ChapterCascader, { type Scope } from '@/components/ChapterCascader.vue'
import QuestionTable from './QuestionTable.vue'

/**
 * 题目搜索：跨题库按范围 / 题型 / 关键字查找题目。
 * 日常录题在“题库管理 → 进入题库”的工作台里进行，这里用于全局查找和反馈“改题”。
 */
const route = useRoute()
const scope = ref<Scope | undefined>()
const table = ref<InstanceType<typeof QuestionTable>>()

onMounted(() => {
  const chapterId = Number(route.query.chapterId)
  if (chapterId) {
    scope.value = { chapterId }
  }
  // 从反馈处理“改题”进来时直接打开该题
  const questionId = Number(route.query.questionId)
  if (questionId) {
    table.value?.edit(questionId)
  }
})
</script>

<template>
  <div class="page-card">
    <QuestionTable ref="table" :scope="scope">
      <template #scope>
        <ChapterCascader v-model="scope" mode="any" placeholder="全部题库（可选 题库 / 科目 / 章节）" style="width: 280px" />
      </template>
    </QuestionTable>
  </div>
</template>
