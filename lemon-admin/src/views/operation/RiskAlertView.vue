<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api/operation'
import UserAccessDrawer from './UserAccessDrawer.vue'

/**
 * 风险告警（防偷题）：同一账号 1 小时 / 1 天内看过的不同题目数、1 小时内开练习次数超过阈值时生成。
 * 阈值在「系统参数」里调（risk.*）。只告警不拦截，确认偷题后在这里禁用账号。
 */
const TYPES: Record<api.RiskAlert['alertType'], string> = {
  hour_questions: '1 小时看题过多',
  day_questions: '1 天看题过多',
  hour_sessions: '1 小时开练习过多',
}
const query = reactive({ pageNum: 1, pageSize: 20, status: 0 as number | undefined, alertType: undefined as string | undefined })
const list = ref<api.RiskAlert[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.pageRiskAlerts({ ...query })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}
onMounted(load)

function search() {
  query.pageNum = 1
  load()
}

async function handle(row: api.RiskAlert) {
  const { value } = await ElMessageBox.prompt('处理说明（选填），如：正常刷题 / 已禁用', '标记已处理', {
    inputPlaceholder: '处理说明',
    inputValidator: (v) => !v || v.length <= 255 || '最多 255 字',
  }).catch(() => ({ value: null as string | null }))
  if (value === null) return
  await api.handleRiskAlert(row.id, value ?? '')
  ElMessage.success('已处理')
  load()
}

async function disable(row: api.RiskAlert) {
  await ElMessageBox.confirm(`禁用后「${row.nickname ?? row.username}」会立即下线、不能再登录。确定禁用？`, '禁用账号', {
    type: 'warning',
    confirmButtonText: '禁用',
  })
  await api.updateAppUserStatus(row.userId, 0)
  await api.handleRiskAlert(row.id, '已禁用账号')
  ElMessage.success('已禁用并标记已处理')
  load()
}

const accessUserId = ref<number | null>(null)
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="query.status" @change="search">
        <el-radio-button :value="0">待处理</el-radio-button>
        <el-radio-button :value="1">已处理</el-radio-button>
        <el-radio-button :value="undefined">全部</el-radio-button>
      </el-radio-group>
      <el-select v-model="query.alertType" placeholder="全部类型" clearable style="width: 170px" @change="search">
        <el-option v-for="(label, k) in TYPES" :key="k" :label="label" :value="k" />
      </el-select>
      <el-button icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="用户" min-width="170">
        <template #default="{ row }">
          <div>
            {{ (row as api.RiskAlert).nickname }}（{{ (row as api.RiskAlert).username }}）
            <el-tag v-if="(row as api.RiskAlert).userStatus === 0" type="info" size="small">已禁用</el-tag>
          </div>
          <div class="sub">{{ (row as api.RiskAlert).phone }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="140">
        <template #default="{ row }">{{ TYPES[(row as api.RiskAlert).alertType] }}</template>
      </el-table-column>
      <el-table-column prop="alertDate" label="日期" width="110" />
      <el-table-column label="数值 / 阈值" width="110">
        <template #default="{ row }">
          <b class="danger">{{ (row as api.RiskAlert).metric }}</b> / {{ (row as api.RiskAlert).threshold }}
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column label="首次 / 最近" width="170">
        <template #default="{ row }">
          <div>{{ (row as api.RiskAlert).createdAt }}</div>
          <div class="sub">{{ (row as api.RiskAlert).updatedAt }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="160">
        <template #default="{ row }">
          <el-tag v-if="(row as api.RiskAlert).status === 0" type="danger" size="small">待处理</el-tag>
          <template v-else>
            <el-tag type="success" size="small">已处理</el-tag>
            <div class="sub">{{ (row as api.RiskAlert).handledBy }} {{ (row as api.RiskAlert).handledAt }}</div>
            <div v-if="(row as api.RiskAlert).remark" class="sub">{{ (row as api.RiskAlert).remark }}</div>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="accessUserId = (row as api.RiskAlert).userId">模块权限</el-button>
          <el-button v-if="(row as api.RiskAlert).status === 0" link type="primary" @click="handle(row as api.RiskAlert)">处理</el-button>
          <el-button v-if="(row as api.RiskAlert).userStatus !== 0" link type="danger" @click="disable(row as api.RiskAlert)">禁用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="load"
    />

    <UserAccessDrawer :user-id="accessUserId" @close="accessUserId = null" />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.danger {
  color: var(--el-color-danger);
}
</style>
