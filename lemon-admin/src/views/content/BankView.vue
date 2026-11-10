<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import * as api from '@/api/content'
import { useContentTree } from '@/composables/useContentTree'
import { BankType, bankTypeLabel, type ExamModule, type QuestionBank } from '@/types/content'

const router = useRouter()
const { reload: reloadTree } = useContentTree()
const modules = ref<ExamModule[]>([])
const moduleName = (id: number) => modules.value.find((m) => m.id === id)?.name ?? '-'

// ---- 列表 ----
const query = reactive<api.BankQuery>({ pageNum: 1, pageSize: 20 })
const list = ref<QuestionBank[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.pageBanks(query)
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

onMounted(async () => {
  modules.value = await api.listModules()
  load()
})

// ---- 编辑弹窗 ----
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const emptyForm = (): api.BankSave => ({
  moduleId: modules.value[0]?.id ?? 0,
  name: '',
  type: BankType.COURSE,
  cover: '',
  intro: '',
  sort: 0,
  status: 1,
})
const form = reactive<api.BankSave>(emptyForm())

function openDialog(row?: QuestionBank) {
  editingId.value = row?.id ?? null
  Object.assign(form, row
    ? { moduleId: row.moduleId, name: row.name, type: row.type, cover: row.cover, intro: row.intro, sort: row.sort, status: row.status }
    : emptyForm())
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  if (editingId.value) {
    await api.updateBank(editingId.value, form)
  } else {
    await api.createBank(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
  reloadTree()
}

/** 进入题库工作台：左侧目录、右侧题目 */
function enter(row: QuestionBank) {
  router.push(`/content/banks/${row.id}`)
}

async function toggleStatus(row: QuestionBank) {
  await api.updateBankStatus(row.id, row.status === 1 ? 0 : 1)
  load()
}

async function remove(row: QuestionBank) {
  await ElMessageBox.confirm(`确定删除题库「${row.name}」吗？`, '提示', { type: 'warning' })
  await api.deleteBank(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-select v-model="query.moduleId" placeholder="全部模块" clearable style="width: 140px" @change="search">
        <el-option v-for="m in modules" :key="m.id" :label="m.name" :value="m.id" />
      </el-select>
      <el-select v-model="query.type" placeholder="全部类型" clearable style="width: 140px" @change="search">
        <el-option label="科目型" :value="BankType.COURSE" />
        <el-option label="试卷型" :value="BankType.PAPER" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="题库名称" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
      <el-button icon="Search" @click="search">查询</el-button>
      <span class="spacer" />
      <el-button type="primary" icon="Plus" @click="openDialog()">新建题库</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="模块" width="100">
        <template #default="{ row }">{{ moduleName(row.moduleId) }}</template>
      </el-table-column>
      <el-table-column label="题库名称" min-width="180">
        <template #default="{ row }">
          <el-link type="primary" underline="never" @click="enter(row as QuestionBank)">{{ (row as QuestionBank).name }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.type === BankType.COURSE ? 'primary' : 'warning'">{{ bankTypeLabel[row.type] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="questionCount" label="题量" width="80" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button link type="primary" @click="enter(row as QuestionBank)">进入</el-button>
          <el-button link type="primary" @click="openDialog(row as QuestionBank)">编辑</el-button>
          <el-button link type="primary" @click="toggleStatus(row as QuestionBank)">{{ row.status === 1 ? '下架' : '上架' }}</el-button>
          <el-button link type="danger" @click="remove(row as QuestionBank)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="load"
      @size-change="search"
    />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑题库' : '新建题库'" width="520px">
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="所属模块" prop="moduleId" :rules="{ required: true, message: '请选择模块' }">
          <el-select v-model="form.moduleId">
            <el-option v-for="m in modules" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题库名称" prop="name" :rules="{ required: true, message: '请输入名称' }">
          <el-input v-model="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="题库类型">
          <el-radio-group v-model="form.type" :disabled="!!editingId">
            <el-radio :value="BankType.COURSE">科目型（题库 → 科目 → 章节）</el-radio>
            <el-radio :value="BankType.PAPER">试卷型（题库 → 试卷）</el-radio>
          </el-radio-group>
          <div v-if="editingId" class="tip">类型创建后不可修改</div>
        </el-form-item>
        <el-form-item label="封面">
          <el-input v-model="form.cover" placeholder="图片 URL（文件上传在 M2 接入）" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.intro" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
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
  </div>
</template>

<style scoped>
.tip {
  color: #909399;
  font-size: 12px;
  width: 100%;
}
</style>
