<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import * as api from '@/api/stats'
import LineChart from '@/components/LineChart.vue'

/**
 * 数据统计：每日趋势（新增 / 活跃 / 做题人数 / 答题数）、新用户留存、内容热度。
 * 活跃 = 当天用过用户端的登录用户；留存 = 注册后第 N 天仍活跃的比例。
 */
const days = ref(30)
const trend = ref<api.DayStat[]>([])
const retention = ref<api.Retention[]>([])
const contentDays = ref(7)
const content = ref<api.ContentStat>({ banks: [], chapters: [], hardQuestions: [] })
const loading = ref(false)

async function loadTrend() {
  loading.value = true
  try {
    trend.value = await api.getTrend(days.value)
  } finally {
    loading.value = false
  }
}
async function loadContent() {
  content.value = await api.getContent(contentDays.value)
}
onMounted(async () => {
  await Promise.all([loadTrend(), loadContent(), api.getRetention(14).then((r) => (retention.value = r))])
})

const labels = computed(() => trend.value.map((d) => d.date))
const userSeries = computed(() => [
  { name: '新增用户', color: '#409eff', data: trend.value.map((d) => d.newUsers) },
  { name: '活跃用户', color: '#67c23a', data: trend.value.map((d) => d.activeUsers) },
  { name: '做题人数', color: '#e6a23c', data: trend.value.map((d) => d.answerUsers) },
])
const answerSeries = computed(() => [{ name: '答题数', color: '#b4432f', data: trend.value.map((d) => d.answerCount) }])
const rateSeries = computed(() => [{ name: '正确率', color: '#909399', data: trend.value.map((d) => d.correctRate) }])

/** 区间汇总 */
const summary = computed(() => {
  const t = trend.value
  const sum = (k: keyof api.DayStat) => t.reduce((a, d) => a + ((d[k] as number) || 0), 0)
  const n = t.length || 1
  return [
    { label: '新增用户', value: sum('newUsers') },
    { label: '日均活跃', value: Math.round(sum('activeUsers') / n) },
    { label: '答题数', value: sum('answerCount') },
    { label: '人均答题', value: sum('answerUsers') ? Math.round(sum('answerCount') / sum('answerUsers')) : 0 },
    { label: '激活码激活', value: sum('activations') },
    { label: '新增笔记', value: sum('newNotes') },
  ]
})

const tableRows = computed(() => [...trend.value].reverse())
const pct = (n: number | null, total: number) => (n == null ? '—' : total ? `${Math.round((n * 100) / total)}%` : '—')
</script>

<template>
  <div>
    <div class="page-card">
      <div class="head">
        <h4 class="section-title">每日趋势</h4>
        <el-radio-group v-model="days" size="small" @change="loadTrend">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
          <el-radio-button :value="90">近 90 天</el-radio-button>
        </el-radio-group>
      </div>
      <div class="tiles">
        <div v-for="s in summary" :key="s.label" class="tile">
          <div class="tile-value">{{ s.value.toLocaleString() }}</div>
          <div class="tile-label">{{ s.label }}</div>
        </div>
      </div>
      <div v-loading="loading" class="charts">
        <div class="chart-box"><LineChart :labels="labels" :series="userSeries" /></div>
        <div class="chart-box"><LineChart :labels="labels" :series="answerSeries" /></div>
        <div class="chart-box"><LineChart :labels="labels" :series="rateSeries" unit="%" :height="180" /></div>
      </div>
      <el-table :data="tableRows" border size="small" max-height="360" style="margin-top: 12px">
        <el-table-column prop="date" label="日期" width="110" />
        <el-table-column prop="newUsers" label="新增" />
        <el-table-column prop="activeUsers" label="活跃" />
        <el-table-column prop="answerUsers" label="做题人数" />
        <el-table-column prop="answerCount" label="答题数" />
        <el-table-column label="正确率">
          <template #default="{ row }">{{ (row as api.DayStat).correctRate == null ? '—' : `${(row as api.DayStat).correctRate}%` }}</template>
        </el-table-column>
        <el-table-column prop="activations" label="激活码激活" />
        <el-table-column prop="newNotes" label="新增笔记" />
      </el-table>
      <div class="hint">活跃：当天用过用户端的登录用户（2026-11-12 之前按注册、登录、答题记录估算）。</div>
    </div>

    <div class="page-card" style="margin-top: 16px">
      <h4 class="section-title">新用户留存（近 14 天注册）</h4>
      <el-table :data="retention" border size="small">
        <el-table-column prop="date" label="注册日期" width="120" />
        <el-table-column prop="newUsers" label="新增用户" />
        <el-table-column label="次日留存">
          <template #default="{ row }">{{ pct((row as api.Retention).d1, (row as api.Retention).newUsers) }}</template>
        </el-table-column>
        <el-table-column label="3 日留存">
          <template #default="{ row }">{{ pct((row as api.Retention).d3, (row as api.Retention).newUsers) }}</template>
        </el-table-column>
        <el-table-column label="7 日留存">
          <template #default="{ row }">{{ pct((row as api.Retention).d7, (row as api.Retention).newUsers) }}</template>
        </el-table-column>
      </el-table>
      <div class="hint">N 日留存：注册后第 N 天又打开过的人数占比；还没到那一天显示「—」。</div>
    </div>

    <div class="page-card" style="margin-top: 16px">
      <div class="head">
        <h4 class="section-title">内容热度</h4>
        <el-radio-group v-model="contentDays" size="small" @change="loadContent">
          <el-radio-button :value="1">今天</el-radio-button>
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
      </div>
      <div class="rank-grid">
        <div>
          <div class="sub-title">热门题库</div>
          <el-table :data="content.banks" border size="small">
            <el-table-column label="题库" min-width="160">
              <template #default="{ row }">
                {{ (row as api.RankItem).name }}<span class="muted"> · {{ (row as api.RankItem).sub }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="answers" label="答题数" width="80" />
            <el-table-column prop="users" label="人数" width="70" />
          </el-table>
        </div>
        <div>
          <div class="sub-title">热门章节</div>
          <el-table :data="content.chapters" border size="small">
            <el-table-column label="章节" min-width="160">
              <template #default="{ row }">
                {{ (row as api.RankItem).name }}<div class="muted">{{ (row as api.RankItem).sub }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="answers" label="答题数" width="80" />
            <el-table-column prop="users" label="人数" width="70" />
          </el-table>
        </div>
      </div>
      <div class="sub-title" style="margin-top: 16px">错误率最高的题（至少 10 次作答）</div>
      <el-table :data="content.hardQuestions" border size="small">
        <el-table-column label="题目" min-width="300">
          <template #default="{ row }">
            <span class="muted">#{{ (row as api.RankItem).id }}</span> {{ (row as api.RankItem).name }}
            <div class="muted">{{ (row as api.RankItem).sub }}</div>
          </template>
        </el-table-column>
        <el-table-column label="错误率" width="80">
          <template #default="{ row }"><b class="danger">{{ (row as api.RankItem).wrongRate }}%</b></template>
        </el-table-column>
        <el-table-column prop="answers" label="作答次数" width="90" />
        <el-table-column prop="users" label="人数" width="70" />
      </el-table>
      <div class="hint">错误率特别高的题，可能是题目或答案有误，可以到「题目搜索」按题号查看。</div>
    </div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  margin: 0;
}
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.tile {
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  padding: 12px 16px;
}
.tile-value {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.tile-label {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.charts {
  display: grid;
  gap: 16px;
}
.chart-box {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
}
.rank-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.sub-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.danger {
  color: var(--el-color-danger);
}
.hint {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
