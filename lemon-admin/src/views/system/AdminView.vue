<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import * as api from '@/api/admin'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const roles = ref<api.Role[]>([])
const roleName = (code: string) => roles.value.find((r) => r.code === code)?.name ?? code

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '' })
const list = ref<api.AdminItem[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.pageAdmins({ ...query, keyword: query.keyword.trim() || undefined })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  roles.value = await api.listRoles()
  load()
})

const isSelf = (a: api.AdminItem) => a.id === userStore.info?.id

// ---- 新建 / 编辑 ----
const dialogVisible = ref(false)
const editing = ref<api.AdminItem | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '', nickname: '', status: 1, roles: [] as string[] })
const rules = computed<FormRules>(() => ({
  username: editing.value ? [] : [
    { required: true, message: '请输入用户名' },
    { pattern: /^[a-zA-Z0-9_]{3,32}$/, message: '3-32 位字母、数字、下划线' },
  ],
  password: editing.value ? [] : [{ required: true, min: 6, max: 32, message: '6-32 位' }],
  roles: [{ required: true, type: 'array', min: 1, message: '请选择角色' }],
}))

function openDialog(row?: api.AdminItem) {
  editing.value = row ?? null
  Object.assign(form, {
    username: row?.username ?? '',
    password: '',
    nickname: row?.nickname ?? '',
    status: row?.status ?? 1,
    roles: row ? [...row.roles] : ['editor'],
  })
  dialogVisible.value = true
}

async function save() {
  await formRef.value?.validate()
  if (editing.value) {
    await api.updateAdmin(editing.value.id, { nickname: form.nickname, status: form.status, roles: form.roles })
  } else {
    await api.createAdmin({ username: form.username, password: form.password, nickname: form.nickname, roles: form.roles })
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function resetPassword(row: api.AdminItem) {
  const { value } = await ElMessageBox.prompt(`为「${row.nickname || row.username}」设置新密码`, '重置密码', {
    inputType: 'password',
    inputPattern: /^.{6,32}$/,
    inputErrorMessage: '6-32 位',
  })
  await api.resetAdminPassword(row.id, value)
  ElMessage.success('密码已重置，对方需要重新登录')
}

async function remove(row: api.AdminItem) {
  await ElMessageBox.confirm(`确定删除管理员「${row.username}」吗？`, '提示', { type: 'warning' })
  await api.deleteAdmin(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="用户名 / 昵称" clearable style="width: 220px" @keyup.enter="load" @clear="load" />
      <el-button icon="Search" @click="load">查询</el-button>
      <span class="spacer" />
      <el-button type="primary" icon="Plus" @click="openDialog()">新建管理员</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column label="角色">
        <template #default="{ row }">
          <el-tag v-for="r in (row as api.AdminItem).roles" :key="r" size="small" style="margin-right: 4px">{{ roleName(r) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as api.AdminItem).status === 1 ? 'success' : 'info'" size="small">
            {{ (row as api.AdminItem).status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最后登录" width="170" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row as api.AdminItem)">编辑</el-button>
          <el-button link type="primary" @click="resetPassword(row as api.AdminItem)">重置密码</el-button>
          <el-button link type="danger" :disabled="isSelf(row as api.AdminItem)" @click="remove(row as api.AdminItem)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑管理员' : '新建管理员'" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!editing" maxlength="32" />
        </el-form-item>
        <el-form-item v-if="!editing" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password maxlength="32" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="32" placeholder="不填则同用户名" />
        </el-form-item>
        <el-form-item label="角色" prop="roles">
          <el-checkbox-group v-model="form.roles">
            <el-checkbox v-for="r in roles" :key="r.code" :value="r.code" :disabled="!!editing && isSelf(editing) && r.code === 'super_admin'">
              {{ r.name }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="禁用" :disabled="isSelf(editing)" />
        </el-form-item>
      </el-form>
      <div class="tip">超级管理员：全部权限。录题员：题库、科目、章节、题目管理，模块只读，不能管理账号和运营配置。</div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
