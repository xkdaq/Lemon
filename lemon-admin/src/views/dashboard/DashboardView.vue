<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDashboard, type Dashboard } from '@/api/operation'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
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
] as const
</script>

<template>
  <div>
    <div class="page-card">
      <h3 class="welcome">欢迎回来，{{ userStore.info?.nickname ?? '管理员' }}</h3>
      <div class="tiles">
        <div v-for="t in tiles" :key="t.key" class="tile">
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
      <div class="hint">答题、活跃等数据在用户端做题功能（M3）上线后开始累计。</div>
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
.hint {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
