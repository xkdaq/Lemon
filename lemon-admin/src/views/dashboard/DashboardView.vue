<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDashboard, type Dashboard } from '@/api/operation'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()
const data = ref<Dashboard>()

onMounted(async () => {
  data.value = await getDashboard()
})

const tiles = [
  { key: 'userTotal', label: '注册用户' },
  { key: 'userToday', label: '今日新增' },
  { key: 'activeToday', label: '今日活跃' },
  { key: 'answerToday', label: '今日答题' },
  { key: 'questionTotal', label: '上架题目' },
  { key: 'draftTotal', label: '下架题目' },
  { key: 'riskPending', label: '待处理告警' },
] as const

/** 有待处理的风险告警时标红，点击进入「风险告警」（仅超级管理员可见该页面） */
const isRisk = (key: string) => key === 'riskPending' && (data.value?.riskPending ?? 0) > 0
function onTile(key: string) {
  if (key === 'riskPending' && userStore.isSuperAdmin) router.push('/operation/risk-alerts')
}
</script>

<template>
  <div>
    <div class="page-card">
      <h3 class="welcome">欢迎回来，{{ userStore.info?.nickname ?? '管理员' }}</h3>
      <div class="tiles">
        <div v-for="t in tiles" :key="t.key" class="tile" :class="{ risk: isRisk(t.key), link: t.key === 'riskPending' && userStore.isSuperAdmin }" @click="onTile(t.key)">
          <div class="tile-value">{{ data ? data[t.key].toLocaleString() : '—' }}</div>
          <div class="tile-label">{{ t.label }}</div>
        </div>
      </div>
    </div>

    <div class="page-card" style="margin-top: 16px">
      <h4 class="section-title">各模块内容</h4>
      <el-table :data="data?.modules ?? []" border>
        <el-table-column prop="name" label="模块" />
        <el-table-column prop="bankCount" label="题库数" />
        <el-table-column prop="questionCount" label="上架题目数" />
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.welcome {
  margin: 0 0 16px;
}
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}
.tile {
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
  padding: 16px;
}
.tile.link {
  cursor: pointer;
}
.tile.risk {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
.tile.risk .tile-label {
  color: var(--el-color-danger);
}
.tile-value {
  font-size: 26px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.tile-label {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.section-title {
  margin: 0 0 12px;
}
</style>
