<script setup lang="ts">
import { nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import Sortable from 'sortablejs'
import { ElMessage, ElMessageBox, type FormInstance, type TableInstance } from 'element-plus'
import * as api from '@/api/content'
import type { BankCategory, ExamModule } from '@/types/content'

/** 模块下的分类管理：模块 → 分类（强化刷题 / 冲刺刷题 / 时政 …）→ 题库 */
const visible = ref(false)
const module = ref<ExamModule>()
const list = ref<BankCategory[]>([])
const loading = ref(false)
const tableRef = ref<TableInstance>()
let sortable: Sortable | null = null

async function open(m: ExamModule) {
  module.value = m
  visible.value = true
  await load()
}
defineExpose({ open })

async function load() {
  if (!module.value) return
  loading.value = true
  try {
    list.value = await api.listCategories(module.value.id)
  } finally {
    loading.value = false
  }
  await nextTick()
  setupSortable()
}

function setupSortable() {
  sortable?.destroy()
  sortable = null
  const tbody = tableRef.value?.$el.querySelector('.el-table__body-wrapper tbody') as HTMLElement | null
  if (!tbody || list.value.length < 2) return
  sortable = Sortable.create(tbody, {
    handle: '.drag-handle',
    animation: 150,
    onEnd: async ({ oldIndex, newIndex, item, from }) => {
      if (oldIndex == null || newIndex == null || oldIndex === newIndex) return
      from.removeChild(item)
      from.insertBefore(item, from.children[oldIndex] ?? null)
      const rows = [...list.value]
      rows.splice(newIndex, 0, rows.splice(oldIndex, 1)[0])
      list.value = rows
      try {
        await api.sortCategories(rows.map((c) => c.id))
        ElMessage.success('排序已保存')
      } finally {
        load()
      }
    },
  })
}
onBeforeUnmount(() => sortable?.destroy())

// ---- 新建 / 编辑 ----
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', status: 1 })

function openDialog(row?: BankCategory) {
  editingId.value = row?.id ?? null
  Object.assign(form, row ? { name: row.name, status: row.status } : { name: '', status: 1 })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  const data: api.CategorySave = { moduleId: module.value!.id, name: form.name, status: form.status }
  if (editingId.value) {
    const old = list.value.find((c) => c.id === editingId.value)
    await api.updateCategory(editingId.value, { ...data, sort: old?.sort })
  } else {
    await api.createCategory(data)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function toggleStatus(row: BankCategory) {
  await api.updateCategory(row.id, { moduleId: row.moduleId, name: row.name, sort: row.sort, status: row.status === 1 ? 0 : 1 })
  load()
}

async function remove(row: BankCategory) {
  await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '提示', { type: 'warning' })
  await api.deleteCategory(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <el-drawer v-model="visible" :title="`分类管理 · ${module?.name ?? ''}`" size="600px">
    <div class="toolbar">
      <span class="tip">用户进入模块后按分类切换题库；分类下架后其题库在用户端隐藏。拖动 ⠿ 调整顺序。</span>
      <span class="spacer" />
      <el-button type="primary" icon="Plus" @click="openDialog()">新建分类</el-button>
    </div>

    <el-table ref="tableRef" v-loading="loading" :data="list" border row-key="id" empty-text="暂无分类，题库将直接平铺展示">
      <el-table-column width="44" align="center">
        <template #default>
          <el-icon class="drag-handle" title="拖动排序"><Rank /></el-icon>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="110" />
      <el-table-column prop="bankCount" label="题库数" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row as BankCategory)">编辑</el-button>
          <el-button link type="primary" @click="toggleStatus(row as BankCategory)">{{ row.status === 1 ? '下架' : '上架' }}</el-button>
          <el-button link type="danger" @click="remove(row as BankCategory)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分类' : '新建分类'" width="420px" append-to-body>
      <el-form ref="formRef" :model="form" label-width="70px" @submit.prevent="save">
        <el-form-item label="名称" prop="name" :rules="{ required: true, message: '请输入名称' }">
          <el-input v-model="form.name" maxlength="32" placeholder="如：强化刷题、冲刺刷题、时政、冲刺背诵" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.spacer {
  flex: 1;
}
.tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.drag-handle {
  cursor: move;
  color: var(--el-text-color-secondary);
  font-size: 16px;
}
.drag-handle:hover {
  color: var(--el-color-primary);
}
:deep(.sortable-ghost) {
  background: var(--el-color-primary-light-9);
}
</style>
