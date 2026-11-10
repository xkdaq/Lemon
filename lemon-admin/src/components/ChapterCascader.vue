<script setup lang="ts">
import { computed } from 'vue'
import type { CascaderOption, CascaderValue } from 'element-plus'
import type { TreeNode } from '@/api/content'
import { findChapterPath, useContentTree } from '@/composables/useContentTree'

/**
 * 题库 → 科目 → 章节 级联选择。
 * - mode="chapter"：只能选到章节，v-model 为章节 ID
 * - mode="any"：可选任意一级（用于筛选），v-model 为 { bankId, courseId, chapterId }
 * bankId 传入时只显示该题库。
 */
export interface Scope {
  bankId?: number
  courseId?: number
  chapterId?: number
}

const props = withDefaults(defineProps<{
  modelValue?: number | Scope | null
  mode?: 'chapter' | 'any'
  bankId?: number
  placeholder?: string
}>(), { mode: 'chapter', placeholder: '选择章节' })

const emit = defineEmits<{ 'update:modelValue': [value: number | Scope | undefined] }>()

const { tree } = useContentTree()
const key = (n: TreeNode) => `${n.kind}:${n.id}`
const statusSuffix = (n: TreeNode) => (n.status === 0 ? '（下架）' : '')

function toOption(n: TreeNode): CascaderOption {
  const children = n.children?.map(toOption)
  return {
    value: key(n),
    label: n.label + statusSuffix(n),
    children: n.kind === 'chapter' ? undefined : children,
    // 选章节模式下，没有章节的题库 / 科目不能选
    disabled: props.mode === 'chapter' && n.kind !== 'chapter' && !children?.length,
  }
}

const options = computed(() =>
  tree.value.filter((b) => !props.bankId || b.id === props.bankId).map(toOption),
)

/** 外部值 → 级联路径 */
const path = computed<string[]>(() => {
  const v = props.modelValue
  if (v == null) {
    return []
  }
  if (typeof v === 'number') {
    return findChapterPath(tree.value, v)?.map(key) ?? []
  }
  if (v.chapterId) {
    return findChapterPath(tree.value, v.chapterId)?.map(key) ?? []
  }
  const bank = tree.value.find((b) => b.id === v.bankId)
  if (!bank) {
    return []
  }
  return v.courseId ? [key(bank), `course:${v.courseId}`] : [key(bank)]
})

function onChange(value: CascaderValue | null | undefined) {
  const keys = (Array.isArray(value) ? value : []) as string[]
  if (props.mode === 'chapter') {
    const last = keys[keys.length - 1]
    emit('update:modelValue', last?.startsWith('chapter:') ? Number(last.split(':')[1]) : undefined)
    return
  }
  const scope: Scope = {}
  for (const k of keys) {
    const [kind, id] = k.split(':')
    if (kind === 'bank') scope.bankId = Number(id)
    if (kind === 'course') scope.courseId = Number(id)
    if (kind === 'chapter') scope.chapterId = Number(id)
  }
  emit('update:modelValue', keys.length ? scope : undefined)
}
</script>

<template>
  <el-cascader
    :model-value="path"
    :options="options"
    :props="{ checkStrictly: mode === 'any', expandTrigger: 'hover' }"
    :placeholder="placeholder"
    filterable
    clearable
    style="width: 100%"
    @update:model-value="onChange"
  />
</template>
