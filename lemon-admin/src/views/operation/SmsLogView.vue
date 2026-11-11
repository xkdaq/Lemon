<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import * as api from '@/api/operation'

/**
 * 短信记录：每次实际调用短信服务记一条（被限流挡掉的不记）。
 * 「已提交」表示短信服务接收了请求；是否最终送达，用请求 ID 到 Spug「发送记录」里查。
 */
const query = reactive({
  pageNum: 1,
  pageSize: 20,
  phone: '',
  status: undefined as number | undefined,
  scene: undefined as number | undefined,
  range: null as [string, string] | null,
})
const list = ref<api.SmsLog[]>([])
const total = ref(0)
const loading = ref(false)
const stats = ref<api.SmsStats>()

async function load() {
  loading.value = true
  try {
    const res = await api.pageSmsLogs({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      phone: query.phone.trim() || undefined,
      status: query.status,
      scene: query.scene,
      startDate: query.range?.[0],
      endDate: query.range?.[1],
    })
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

async function refresh() {
  stats.value = await api.getSmsStats()
  load()
}

onMounted(refresh)

const STATUS: Record<number, { label: string; type: 'success' | 'danger' | 'info' }> = {
  1: { label: '已提交', type: 'success' },
  0: { label: '提交失败', type: 'danger' },
  2: { label: '未实际发送', type: 'info' },
}
const SCENE: Record<number, string> = { 3: '注册', 4: '找回密码', 1: '验证码登录（旧）', 2: '设置密码（旧）' }

const rate = (c?: api.SmsDayCount) => (c && c.ok + c.fail > 0 ? `${Math.round((c.ok / (c.ok + c.fail)) * 100)}%` : '—')

const tiles = computed(() => {
  const s = stats.value
  return [
    { label: '今日发送', value: s?.today.total, sub: s ? `失败 ${s.today.fail}` : '' },
    { label: '今日成功率', value: rate(s?.today), sub: '' },
    { label: '近 7 天', value: s?.last7.total, sub: s ? `失败 ${s.last7.fail}` : '' },
    { label: '近 30 天', value: s?.last30.total, sub: s ? `成功率 ${rate(s.last30)}` : '' },
  ]
})

/** 柱状图：每天一根，绿色已提交、红色失败、灰色未实际发送 */
const maxDay = computed(() => Math.max(1, ...(stats.value?.days.map((d) => d.total) ?? [1])))
const pct = (n: number) => `${(n / maxDay.value) * 100}%`

/** 点某一天的柱子：筛选这一天的记录 */
function pickDay(day: string) {
  query.range = [day, day]
  search()
}
</script>

<template>
  <div>
    <div class="page-card">
      <div class="head">
        <h4 class="section-title">短信发送统计</h4>
        <el-button icon="Refresh" @click="refresh">刷新</el-button>
      </div>
      <div class="tiles">
        <div v-for="t in tiles" :key="t.label" class="tile">
          <div class="tile-value">{{ t.value ?? '—' }}</div>
          <div class="tile-label">{{ t.label }}<span v-if="t.sub" class="tile-sub"> · {{ t.sub }}</span></div>
        </div>
      </div>

      <div class="chart">
        <div class="chart-title">近 30 天每天发送条数（点柱子查看当天记录）</div>
        <div class="bars">
          <el-tooltip v-for="d in stats?.days ?? []" :key="d.day" placement="top" :show-after="100">
            <template #content>
              {{ d.day }}<br />共 {{ d.total }} 条：已提交 {{ d.ok }}，失败 {{ d.fail }}
              <template v-if="d.total - d.ok - d.fail > 0">，未实际发送 {{ d.total - d.ok - d.fail }}</template>
            </template>
            <div class="bar" @click="pickDay(d.day)">
              <div class="stack" :style="{ height: pct(d.total) }">
                <span class="seg fail" :style="{ flex: d.fail }" />
                <span class="seg mock" :style="{ flex: d.total - d.ok - d.fail }" />
                <span class="seg ok" :style="{ flex: d.ok }" />
              </div>
            </div>
          </el-tooltip>
        </div>
        <div class="axis">
          <span>{{ stats?.days[0]?.day.slice(5) }}</span>
          <span>{{ stats?.days[stats.days.length - 1]?.day.slice(5) }}</span>
        </div>
      </div>
      <div class="hint">
        「已提交」表示短信服务已接收请求，是否最终送达可用请求 ID 到 Spug 控制台「发送记录」里查。被限流挡掉的请求不会发短信，也不记录。
      </div>
    </div>

    <div class="page-card" style="margin-top: 16px">
      <div class="toolbar">
        <el-input v-model="query.phone" placeholder="手机号" clearable style="width: 180px" @keyup.enter="search" @clear="search" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="search">
          <el-option v-for="(s, k) in STATUS" :key="k" :label="s.label" :value="Number(k)" />
        </el-select>
        <el-select v-model="query.scene" placeholder="全部用途" clearable style="width: 150px" @change="search">
          <el-option v-for="(s, k) in SCENE" :key="k" :label="s" :value="Number(k)" />
        </el-select>
        <el-date-picker
          v-model="query.range"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 260px"
          @change="search"
        />
        <el-button icon="Search" @click="search">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border>
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="用途" width="100">
          <template #default="{ row }">{{ SCENE[(row as api.SmsLog).scene] ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="STATUS[(row as api.SmsLog).status]?.type ?? 'info'" size="small">
              {{ STATUS[(row as api.SmsLog).status]?.label ?? '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestId" label="请求 ID" width="190">
          <template #default="{ row }">{{ (row as api.SmsLog).requestId ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as api.SmsLog).error ?? '' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="150" />
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
  </div>
</template>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.section-title {
  margin: 0;
}
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
}
.tile {
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  padding: 16px;
}
.tile-value {
  font-size: 24px;
  font-weight: 600;
}
.tile-label {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.tile-sub {
  color: var(--el-text-color-placeholder);
}
.chart {
  margin-top: 20px;
}
.chart-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.bars {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 120px;
  border-bottom: 1px solid var(--el-border-color);
}
.bar {
  flex: 1;
  height: 100%;
  display: flex;
  align-items: flex-end;
  cursor: pointer;
  border-radius: 3px 3px 0 0;
}
.bar:hover {
  background: var(--el-fill-color-light);
}
.stack {
  width: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 3px 3px 0 0;
  overflow: hidden;
}
.seg.ok {
  background: var(--el-color-success);
}
.seg.fail {
  background: var(--el-color-danger);
}
.seg.mock {
  background: var(--el-color-info-light-5);
}
.axis {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 4px;
}
.hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
