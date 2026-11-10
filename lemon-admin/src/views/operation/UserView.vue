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
      <el-input v-model="query.keyword" placeholder="手机号 / 昵称" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="search">
        <el-option label="正常" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="80" />
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
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link :type="(row as api.AppUser).status === 1 ? 'danger' : 'primary'" @click="toggle(row as api.AppUser)">
            {{ (row as api.AppUser).status === 1 ? '禁用' : '启用' }}
          </el-button>
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
  </div>
</template>
