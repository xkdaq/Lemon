<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api/operation'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', status: undefined as number | undefined })
const list = ref<api.AppUser[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.pageAppUsers({ ...query, keyword: query.keyword.trim() || undefined })
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

onMounted(load)

const fromLabel: Record<number, string> = { 1: 'Web', 2: '小程序' }

/** 重置密码：拿到一次性临时密码后弹窗展示，管理员复制转告用户 */
const resetResult = ref<{ user: api.AppUser; password: string } | null>(null)

async function resetPassword(u: api.AppUser) {
  await ElMessageBox.confirm(
    `将为「${u.nickname ?? u.username}」生成一个临时密码，原密码立即失效、所有设备下线。用户用临时密码登录后会被要求修改密码。`,
    '重置密码',
    { type: 'warning', confirmButtonText: '重置' },
  )
  const { password } = await api.resetAppUserPassword(u.id)
  resetResult.value = { user: u, password }
}

async function copyPassword() {
  if (!resetResult.value) return
  try {
    await navigator.clipboard.writeText(resetResult.value.password)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选中复制')
  }
}

async function toggle(u: api.AppUser) {
  const disable = u.status === 1
  if (disable) {
    await ElMessageBox.confirm(`禁用后「${u.nickname ?? u.phone}」会被立即下线且无法登录，确定吗？`, '提示', { type: 'warning' })
  }
  await api.updateAppUserStatus(u.id, disable ? 0 : 1)
  ElMessage.success(disable ? '已禁用' : '已启用')
  load()
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="手机号 / 用户名 / 昵称" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="search">
        <el-option label="正常" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column label="注册来源" width="100">
        <template #default="{ row }">{{ fromLabel[(row as api.AppUser).registerFrom] ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="注册时间" width="170" />
      <el-table-column prop="lastLoginAt" label="最后登录" width="170" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as api.AppUser).status === 1 ? 'success' : 'danger'" size="small">
            {{ (row as api.AppUser).status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="resetPassword(row as api.AppUser)">重置密码</el-button>
          <el-button link :type="(row as api.AppUser).status === 1 ? 'danger' : 'primary'" @click="toggle(row as api.AppUser)">
            {{ (row as api.AppUser).status === 1 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :model-value="!!resetResult" title="密码已重置" width="420px" @close="resetResult = null">
      <template v-if="resetResult">
        <p>「{{ resetResult.user.nickname ?? resetResult.user.username }}」（{{ resetResult.user.username }}）的临时密码：</p>
        <div class="temp-pwd">{{ resetResult.password }}</div>
        <p class="tip">只显示这一次，请复制后发给用户。用户可以用手机号或用户名 + 临时密码登录，登录后会被要求修改密码。</p>
      </template>
      <template #footer>
        <el-button @click="resetResult = null">关闭</el-button>
        <el-button type="primary" @click="copyPassword">复制密码</el-button>
      </template>
    </el-dialog>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="load"
      @size-change="search"
    />
  </div>
</template>

<style scoped>
.temp-pwd {
  font-family: ui-monospace, Menlo, monospace;
  font-size: 22px;
  letter-spacing: 2px;
  padding: 12px 16px;
  margin: 8px 0 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  user-select: all;
}
.tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
