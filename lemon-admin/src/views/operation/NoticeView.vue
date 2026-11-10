<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import * as api from '@/api/operation'

const query = reactive({ pageNum: 1, pageSize: 20 })
const list = ref<api.Notice[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.pageNotices(query)
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}
onMounted(load)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<api.NoticeSave>({ title: '', content: '', sort: 0, status: 1 })

function openDialog(row?: api.Notice) {
  editingId.value = row?.id ?? null
  Object.assign(form, { title: row?.title ?? '', content: row?.content ?? '', sort: row?.sort ?? 0, status: row?.status ?? 1 })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  if (editingId.value) {
    await api.updateNotice(editingId.value, form)
  } else {
    await api.createNotice(form)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function toggle(row: api.Notice) {
  await api.updateNotice(row.id, { title: row.title, content: row.content, sort: row.sort, status: row.status === 1 ? 0 : 1 })
  load()
}

async function remove(row: api.Notice) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '提示', { type: 'warning' })
  await api.deleteNotice(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="muted">上架的公告按排序显示在用户端首页（最多 5 条，首页展示第一条）。</span>
      <span class="spacer" />
      <el-button type="primary" icon="Plus" @click="openDialog()">新建公告</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as api.Notice).status === 1 ? 'success' : 'info'" size="small">
            {{ (row as api.Notice).status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row as api.Notice)">编辑</el-button>
          <el-button link type="primary" @click="toggle(row as api.Notice)">{{ (row as api.Notice).status === 1 ? '下架' : '上架' }}</el-button>
          <el-button link type="danger" @click="remove(row as api.Notice)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="load"
    />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑公告' : '新建公告'" width="520px">
      <el-form ref="formRef" :model="form" label-width="70px">
        <el-form-item label="标题" prop="title" :rules="{ required: true, message: '请输入标题' }">
          <el-input v-model="form.title" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="5" maxlength="5000" />
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
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
