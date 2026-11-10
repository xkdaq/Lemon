<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '@/api/punch'
import { listPaperRules, type PaperRule } from '@/api/paperRule'

const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']
const route = useRoute()
const router = useRouter()
const campId = Number(route.params.id)

const camp = ref<api.PunchCamp>()
const rules = ref<PaperRule[]>([])
const tab = ref<'schedule' | 'members'>('schedule')

/** 本地日期 yyyy-MM-dd（toISOString 是 UTC，凌晨会差一天） */
function localToday() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(async () => {
  const [camps, ruleList] = await Promise.all([api.listCamps(), listPaperRules()])
  camp.value = camps.find((c) => c.id === campId)
  rules.value = ruleList
  if (!camp.value) {
    ElMessage.error('打卡活动不存在')
    router.replace({ name: 'punch' })
    return
  }
  // 默认显示本月；活动还没开始或已结束时显示开始 / 结束那个月
  const today = localToday()
  const day = today < camp.value.startDate ? camp.value.startDate : today > camp.value.endDate ? camp.value.endDate : today
  month.value = day.slice(0, 7)
  loadSchedule()
  loadMembers()
})

// ==================== 日程 ====================

const month = ref('')
const schedule = ref<api.ScheduleDay[]>([])
const scheduleLoading = ref(false)
const batch = reactive({ dates: [] as string[], paperRuleId: undefined as number | undefined })
const applying = ref(false)

async function loadSchedule() {
  if (!month.value) return
  scheduleLoading.value = true
  try {
    schedule.value = await api.getSchedule(campId, month.value)
  } finally {
    scheduleLoading.value = false
  }
}

function pickDay(d: api.ScheduleDay) {
  batch.dates = [d.day, d.day]
  batch.paperRuleId = d.paperRuleId
}

async function apply(restore: boolean) {
  if (batch.dates.length !== 2) return ElMessage.warning('请选择日期范围')
  if (!restore && !batch.paperRuleId) return ElMessage.warning('请选择组卷规则')
  applying.value = true
  try {
    await api.setSchedule(campId, batch.dates[0], batch.dates[1], restore ? null : batch.paperRuleId!)
  } finally {
    applying.value = false
  }
  ElMessage.success(restore ? '已恢复为默认规则' : '已设置')
  loadSchedule()
}

const isToday = (d: string) => d === localToday()

// ==================== 学员 ====================

const memberQuery = reactive({ pageNum: 1, pageSize: 20, keyword: '' })
const members = ref<api.PunchMember[]>([])
const memberTotal = ref(0)
const membersLoading = ref(false)

async function loadMembers() {
  membersLoading.value = true
  try {
    const res = await api.pageMembers(campId, { ...memberQuery, keyword: memberQuery.keyword.trim() || undefined })
    members.value = res.list
    memberTotal.value = res.total
  } finally {
    membersLoading.value = false
  }
}

function search() {
  memberQuery.pageNum = 1
  loadMembers()
}

const restText = computed(() => {
  const r = camp.value?.restWeekdays ?? []
  return r.length ? `周${r.map((d) => WEEKDAYS[d - 1]).join('、')}休息` : '每天打卡'
})
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-button icon="ArrowLeft" @click="router.push({ name: 'punch' })">返回</el-button>
      <template v-if="camp">
        <span class="title">{{ camp.name }}</span>
        <span class="muted">
          {{ camp.startDate }} ~ {{ camp.endDate }} · {{ restText }} · 默认规则：{{ camp.paperRuleName }} ·
          学员 {{ camp.memberCount }} · 今日打卡 {{ camp.todayCount }}
        </span>
      </template>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="日程" name="schedule">
        <div class="toolbar">
          <el-date-picker v-model="month" type="month" value-format="YYYY-MM" :clearable="false" style="width: 140px" @change="loadSchedule" />
          <span class="spacer" />
          <el-date-picker v-model="batch.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" style="width: 260px" />
          <el-select v-model="batch.paperRuleId" filterable placeholder="组卷规则" style="width: 240px">
            <el-option v-for="r in rules" :key="r.id" :label="`${r.name}（${r.questionCount} 题）`" :value="r.id" />
          </el-select>
          <el-button type="primary" :loading="applying" @click="apply(false)">设为该规则</el-button>
          <el-button :loading="applying" @click="apply(true)">恢复默认</el-button>
        </div>
        <el-table v-loading="scheduleLoading" :data="schedule" border size="small" max-height="62vh" empty-text="这个月不在活动期内">
          <el-table-column label="日期" width="130">
            <template #default="{ row }">
              {{ (row as api.ScheduleDay).day }}
              <el-tag v-if="isToday((row as api.ScheduleDay).day)" size="small" type="warning">今天</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="星期" width="70">
            <template #default="{ row }">周{{ WEEKDAYS[(row as api.ScheduleDay).weekday - 1] }}</template>
          </el-table-column>
          <el-table-column label="组卷规则" min-width="240">
            <template #default="{ row }">
              <span v-if="(row as api.ScheduleDay).rest" class="muted">休息</span>
              <template v-else>
                {{ (row as api.ScheduleDay).paperRuleName ?? '（规则已删除）' }}
                <el-tag v-if="(row as api.ScheduleDay).overridden" size="small">单独设置</el-tag>
              </template>
            </template>
          </el-table-column>
          <el-table-column prop="doneCount" label="打卡人数" width="90" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button v-if="!(row as api.ScheduleDay).rest" link type="primary" @click="pickDay(row as api.ScheduleDay)">改规则</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="学员" name="members">
        <div class="toolbar">
          <el-input v-model="memberQuery.keyword" placeholder="昵称 / 手机号" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
          <el-button type="primary" icon="Search" @click="search">搜索</el-button>
        </div>
        <el-table v-loading="membersLoading" :data="members" border>
          <el-table-column prop="userId" label="用户 ID" width="90" />
          <el-table-column prop="nickname" label="昵称" min-width="140" />
          <el-table-column prop="phone" label="手机号" width="130" />
          <el-table-column prop="punchDays" label="打卡天数" width="90" />
          <el-table-column prop="lastDay" label="最近打卡" width="120" />
          <el-table-column prop="makeupUsed" label="已补卡" width="80" />
          <el-table-column prop="joinedAt" label="加入时间" width="170" />
        </el-table>
        <el-pagination
          v-model:current-page="memberQuery.pageNum"
          :page-size="memberQuery.pageSize"
          :total="memberTotal"
          layout="total, prev, pager, next"
          style="margin-top: 16px; justify-content: flex-end"
          @current-change="loadMembers"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.title {
  font-size: 16px;
  font-weight: 600;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
