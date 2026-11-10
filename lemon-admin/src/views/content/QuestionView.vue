<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type TableInstance } from 'element-plus'
import * as api from '@/api/question'
import ChapterCascader, { type Scope } from '@/components/ChapterCascader.vue'
import QuestionEditDialog from './QuestionEditDialog.vue'
import QuestionImportDialog from './QuestionImportDialog.vue'
import { useContentTree } from '@/composables/useContentTree'
import { QuestionType, questionTypeLabel, type Question } from '@/types/content'
import { htmlToText } from '@/utils/html'

const route = useRoute()
const { reload: reloadTree } = useContentTree()

// ---- 筛选 ----
const scope = ref<Scope | undefined>()
const filters = reactive({ type: undefined as number | undefined, status: undefined as number | undefined, keyword: '' })
const pageNum = ref(1)
const pageSize = ref(20)

const queryParams = computed(() => ({
  ...scope.value,
  type: filters.type,
  status: filters.status,
  keyword: filters.keyword.trim() || undefined,
}))

// ---- 列表 ----
const list = ref<Question[]>([])
const total = ref(0)
const loading = ref(false)
const selected = ref<Question[]>([])
const tableRef = ref<TableInstance>()

async function load() {
  loading.value = true
  try {
    const res = await api.pageQuestions({ pageNum: pageNum.value, pageSize: pageSize.value, ...queryParams.value })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

function resetFilters() {
  scope.value = undefined
  Object.assign(filters, { type: undefined, status: undefined, keyword: '' })
  search()
}

onMounted(() => {
  // 从“题库结构”页点“题目”进来时带着章节
  const chapterId = Number(route.query.chapterId)
  if (chapterId) {
    scope.value = { chapterId }
  }
  load()
  // 从反馈处理“改题”进来时直接打开该题
  const questionId = Number(route.query.questionId)
  if (questionId) {
    editDialog.value?.open(questionId)
  }
})

const typeTag: Record<number, 'primary' | 'success' | 'warning' | 'info'> = {
  [QuestionType.SINGLE]: 'primary',
  [QuestionType.MULTIPLE]: 'success',
  [QuestionType.JUDGE]: 'warning',
  [QuestionType.SUBJECTIVE]: 'info',
}
const difficultyLabel = ['', '简单', '中等', '困难']

function location(q: Question) {
  return [q.bankName, q.courseName, q.chapterName].filter(Boolean).join(' / ')
}

function answerText(q: Question) {
  if (q.type === QuestionType.SUBJECTIVE) return '—'
  if (q.type === QuestionType.JUDGE) return q.options.find((o) => o.key === q.answer)?.content ?? q.answer
  return q.answer
}

// ---- 单题操作 ----
const editDialog = ref<InstanceType<typeof QuestionEditDialog>>()
const importDialog = ref<InstanceType<typeof QuestionImportDialog>>()

function create() {
  editDialog.value?.open(undefined, { chapterId: scope.value?.chapterId, type: filters.type })
}

async function remove(q: Question) {
  await ElMessageBox.confirm(`确定删除题目 #${q.id} 吗？`, '提示', { type: 'warning' })
  await api.deleteQuestion(q.id)
  ElMessage.success('已删除')
  load()
}

async function toggleStatus(q: Question) {
  await api.batchStatus([q.id], q.status === 1 ? 0 : 1)
  load()
}

// ---- 批量操作 ----
const ids = computed(() => selected.value.map((q) => q.id))

async function batchStatus(status: number) {
  await api.batchStatus(ids.value, status)
  ElMessage.success(`已${status === 1 ? '上架' : '下架'} ${ids.value.length} 题`)
  load()
}

async function batchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${ids.value.length} 道题吗？`, '提示', { type: 'warning' })
  await api.batchDelete(ids.value)
  ElMessage.success('已删除')
  load()
}

const moveVisible = ref(false)
const moveTarget = ref<number>()

function openMove() {
  moveTarget.value = undefined
  moveVisible.value = true
}

async function confirmMove() {
  if (!moveTarget.value) {
    ElMessage.warning('请选择目标章节')
    return
  }
  await api.batchMove(ids.value, moveTarget.value)
  ElMessage.success(`已移动 ${ids.value.length} 题`)
  moveVisible.value = false
  load()
}

// ---- 导入导出 ----
function openImport() {
  importDialog.value?.open({ bankId: scope.value?.bankId, chapterId: scope.value?.chapterId })
}

const exporting = ref(false)
async function exportExcel() {
  exporting.value = true
  try {
    await api.exportQuestions(queryParams.value)
  } finally {
    exporting.value = false
  }
}

function onImported() {
  reloadTree()
  search()
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar filters">
      <ChapterCascader v-model="scope" mode="any" placeholder="题库 / 科目 / 章节" style="width: 300px" @update:model-value="search" />
      <el-select v-model="filters.type" placeholder="全部题型" clearable style="width: 120px" @change="search">
        <el-option v-for="(label, code) in questionTypeLabel" :key="code" :label="label" :value="Number(code)" />
      </el-select>
      <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 110px" @change="search">
        <el-option label="上架" :value="1" />
        <el-option label="下架" :value="0" />
      </el-select>
      <el-input v-model="filters.keyword" placeholder="搜索题干" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
      <el-button icon="Search" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <div class="toolbar">
      <el-button type="primary" icon="Plus" @click="create">新建题目</el-button>
      <el-button icon="Upload" @click="openImport">Excel 导入</el-button>
      <el-button icon="Download" :loading="exporting" @click="exportExcel">按筛选导出</el-button>
      <el-button link type="primary" @click="api.downloadTemplate()">下载导入模板</el-button>
    </div>

    <div v-if="selected.length" class="batch-bar">
      <span class="selected">已选 {{ selected.length }} 题</span>
      <el-button size="small" @click="batchStatus(1)">上架</el-button>
      <el-button size="small" @click="batchStatus(0)">下架</el-button>
      <el-button size="small" @click="openMove">移动到…</el-button>
      <el-button size="small" type="danger" plain @click="batchDelete">删除</el-button>
      <el-button size="small" link @click="tableRef?.clearSelection()">取消选择</el-button>
    </div>

    <el-table ref="tableRef" v-loading="loading" :data="list" border row-key="id" @selection-change="(rows: Question[]) => (selected = rows)">
      <el-table-column type="selection" width="44" />
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="题型" width="80">
        <template #default="{ row }">
          <el-tag :type="typeTag[(row as Question).type]" size="small">{{ questionTypeLabel[(row as Question).type] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="题干" min-width="300">
        <template #default="{ row }">
          <div class="stem" :title="htmlToText((row as Question).stem)">{{ htmlToText((row as Question).stem) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="答案" width="80">
        <template #default="{ row }">{{ answerText(row as Question) }}</template>
      </el-table-column>
      <el-table-column label="所属" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ location(row as Question) }}</template>
      </el-table-column>
      <el-table-column label="难度" width="70">
        <template #default="{ row }">{{ difficultyLabel[(row as Question).difficulty] }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as Question).status === 1 ? 'success' : 'info'" size="small">
            {{ (row as Question).status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="editDialog?.open((row as Question).id)">编辑</el-button>
          <el-button link type="primary" @click="toggleStatus(row as Question)">{{ (row as Question).status === 1 ? '下架' : '上架' }}</el-button>
          <el-button link type="danger" @click="remove(row as Question)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="load"
      @size-change="search"
    />

    <QuestionEditDialog ref="editDialog" @saved="load" />
    <QuestionImportDialog ref="importDialog" @imported="onImported" />

    <el-dialog v-model="moveVisible" :title="`移动 ${selected.length} 道题`" width="480px">
      <ChapterCascader v-model="moveTarget" placeholder="选择目标章节" />
      <div class="tip">题目会追加到目标章节末尾，可以跨题库移动。</div>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmMove">移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filters {
  flex-wrap: wrap;
}
.stem {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}
.toolbar {
  flex-wrap: wrap;
}
.batch-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
}
.batch-bar .el-button + .el-button {
  margin-left: 0;
}
.selected {
  color: var(--el-color-primary);
  font-size: 13px;
  margin-right: 4px;
}
.tip {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
