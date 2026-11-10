<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import * as api from '@/api/content'
import type { ExamModule } from '@/types/content'
import { useUserStore } from '@/stores/user'
import CategoryDrawer from './CategoryDrawer.vue'

const userStore = useUserStore()

const list = ref<ExamModule[]>([])
const categoryDrawer = ref<InstanceType<typeof CategoryDrawer>>()
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await api.listModules()
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ---- 编辑弹窗 ----
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<api.ModuleSave>({ name: '', icon: '', sort: 0, status: 1 })

function openDialog(row?: ExamModule) {
  editingId.value = row?.id ?? null
  Object.assign(form, row ? { name: row.name, icon: row.icon, sort: row.sort, status: row.status } : { name: '', icon: '', sort: 0, status: 1 })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  if (editingId.value) {
    await api.updateModule(editingId.value, form)
  } else {
    await api.createModule(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function remove(row: ExamModule) {
  await ElMessageBox.confirm(`确定删除模块「${row.name}」吗？`, '提示', { type: 'warning' })
  await api.deleteModule(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <span>首页展示的大入口（政治 / 教育学）。点「分类」管理模块下的分类（强化刷题 / 冲刺刷题 / 时政 …）。</span>
      <span class="spacer" />
      <el-button v-if="userStore.isSuperAdmin" type="primary" icon="Plus" @click="openDialog()">新建模块</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="sort" label="排序" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="categoryDrawer?.open(row as ExamModule)">分类</el-button>
          <template v-if="userStore.isSuperAdmin">
          <el-button link type="primary" @click="openDialog(row as ExamModule)">编辑</el-button>
          <el-button link type="danger" @click="remove(row as ExamModule)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑模块' : '新建模块'" width="460px">
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="名称" prop="name" :rules="{ required: true, message: '请输入名称' }">
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="图片 URL（文件上传在 M2 接入）" />
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

    <CategoryDrawer ref="categoryDrawer" />
  </div>
</template>
