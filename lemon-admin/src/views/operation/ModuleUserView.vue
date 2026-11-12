<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import * as api from '@/api/access'
import UserAccessDrawer from './UserAccessDrawer.vue'

/**
 * 模块用户：每个模块开通了哪些用户、怎么开通的（激活码 / 后台开通 / 老系统赠送 / 注册赠送）、激活时间、到期时间。
 * 点「详情」打开用户的模块权限，可以看全部变更记录、延长或取消。
 */
const stats = ref<api.ModuleStat[]>([])
const tab = ref('all')
const query = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
  status: undefined as number | undefined,
  source: undefined as number | undefined,
  activated: null as [string, string] | null,
  orderBy: 'lastAt' as api.ModuleUserQuery['orderBy'],
  order: 'desc' as api.ModuleUserQuery['order'],
})
const list = ref<api.ModuleUser[]>([])
const total = ref(0)
const loading = ref(false)

const STATUS = [
  { value: 1, label: '有效' },
  { value: 2, label: '7 天内到期' },
  { value: 3, label: '已过期' },
]
const SOURCE: Record<number, string> = { 1: '激活码', 2: '后台开通', 4: '老系统赠送', 5: '注册赠送' }

const moduleId = computed(() => (tab.value === 'all' ? undefined : Number(tab.value)))
const current = computed(() => {
  const s = moduleId.value ? stats.value.filter((m) => m.moduleId === moduleId.value) : stats.value
  return s.reduce((a, m) => ({ active: a.active + m.active, expiring: a.expiring + m.expiring, expired: a.expired + m.expired }), {
    active: 0,
    expiring: 0,
    expired: 0,
  })
})

function filters() {
  return {
    keyword: query.keyword.trim() || undefined,
    moduleId: moduleId.value,
    status: query.status,
    source: query.source,
    activatedFrom: query.activated?.[0],
    activatedTo: query.activated?.[1],
    orderBy: query.orderBy,
    order: query.order,
  }
}

async function load() {
  loading.value = true
  try {
    const res = await api.pageModuleUsers({ pageNum: query.pageNum, pageSize: query.pageSize, ...filters() })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  stats.value = await api.moduleUserStats()
}

function search() {
  query.pageNum = 1
  load()
}

function pickStatus(s: number) {
  query.status = query.status === s ? undefined : s
  search()
}

const SORT_KEYS: Record<string, api.ModuleUserQuery['orderBy']> = { lastAt: 'lastAt', expireAt: 'expireAt', firstAt: 'createdAt' }
function onSort({ prop, order }: { prop: string | null; order: string | null }) {
  if (!order) {
    query.orderBy = 'lastAt'
    query.order = 'desc'
  } else {
    query.orderBy = (prop && SORT_KEYS[prop]) || 'lastAt'
    query.order = order === 'ascending' ? 'asc' : 'desc'
  }
  search()
}

onMounted(() => {
  loadStats()
  load()
})

const exportCsv = () => api.exportModuleUsers(filters())

const accessUserId = ref<number | null>(null)
function closeDrawer() {
  accessUserId.value = null
  loadStats()
  load()
}

function statusTag(r: api.ModuleUser): { label: string; type: 'success' | 'warning' | 'info' } {
  if (!r.active) return { label: '已过期', type: 'info' }
  if ((r.remainDays ?? 0) <= 7) return { label: '快到期', type: 'warning' }
  return { label: '有效', type: 'success' }
}
</script>

<template>
  <div class="page-card">
    <el-tabs v-model="tab" @tab-change="search">
      <el-tab-pane name="all" label="全部" />
      <el-tab-pane v-for="m in stats" :key="m.moduleId" :name="String(m.moduleId)" :label="`${m.moduleName} ${m.active}`" />
    </el-tabs>

    <div class="summary">
      <span class="stat" :class="{ on: query.status === 1 }" @click="pickStatus(1)">有效 <b>{{ current.active }}</b></span>
      <span class="stat" :class="{ on: query.status === 2 }" @click="pickStatus(2)">7 天内到期 <b class="warn">{{ current.expiring }}</b></span>
      <span class="stat" :class="{ on: query.status === 3 }" @click="pickStatus(3)">已过期 <b class="muted">{{ current.expired }}</b></span>
    </div>

    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="手机号 / 用户名 / 昵称 / 激活码" clearable style="width: 240px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="search">
        <el-option v-for="s in STATUS" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-select v-model="query.source" placeholder="全部开通方式" clearable style="width: 140px" @change="search">
        <el-option v-for="(label, k) in SOURCE" :key="k" :label="label" :value="Number(k)" />
      </el-select>
      <el-date-picker
        v-model="query.activated"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="激活开始日期"
        end-placeholder="激活结束日期"
        style="width: 260px"
        @change="search"
      />
      <el-button icon="Search" @click="search">查询</el-button>
      <span class="spacer" />
      <el-button icon="Download" @click="exportCsv">导出</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border :default-sort="{ prop: 'lastAt', order: 'descending' }" @sort-change="onSort">
      <el-table-column label="用户" min-width="180">
        <template #default="{ row }">
          <div>{{ (row as api.ModuleUser).nickname }}（{{ (row as api.ModuleUser).username }}）</div>
          <div class="sub">{{ (row as api.ModuleUser).phone }}</div>
        </template>
      </el-table-column>
      <el-table-column v-if="!moduleId" prop="moduleName" label="模块" width="90" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusTag(row as api.ModuleUser).type" size="small">{{ statusTag(row as api.ModuleUser).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="expireAt" label="到期时间" width="170" sortable="custom">
        <template #default="{ row }">
          <div>{{ (row as api.ModuleUser).expireAt }}</div>
          <div v-if="(row as api.ModuleUser).remainDays" class="sub">剩 {{ (row as api.ModuleUser).remainDays }} 天</div>
        </template>
      </el-table-column>
      <el-table-column label="开通方式" min-width="150">
        <template #default="{ row }">
          {{ (row as api.ModuleUser).source ? SOURCE[(row as api.ModuleUser).source!] : '—' }}
          <div v-if="(row as api.ModuleUser).code" class="sub mono">{{ (row as api.ModuleUser).code }}</div>
          <div v-if="(row as api.ModuleUser).adminName" class="sub">{{ (row as api.ModuleUser).adminName }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="lastAt" label="激活时间" width="170" sortable="custom" />
      <el-table-column prop="firstAt" label="首次开通" width="170" sortable="custom" />
      <el-table-column prop="grantCount" label="开通次数" width="90" align="center" />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="accessUserId = (row as api.ModuleUser).userId">详情</el-button>
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

    <UserAccessDrawer :user-id="accessUserId" @close="closeDrawer" />
  </div>
</template>

<style scoped>
.summary {
  display: flex;
  gap: 24px;
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.stat {
  cursor: pointer;
  padding: 2px 0;
  border-bottom: 2px solid transparent;
}
.stat.on {
  border-bottom-color: var(--el-color-primary);
}
.stat b {
  font-size: 16px;
  margin-left: 4px;
  color: var(--el-color-success);
}
.stat b.warn {
  color: var(--el-color-warning);
}
.stat b.muted {
  color: var(--el-text-color-secondary);
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.mono {
  font-family: ui-monospace, Menlo, monospace;
}
</style>
