<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api/operation'
import AppThemeCard from './AppThemeCard.vue'

/** 主题配色有专门的选择卡片，不放在下面的通用表格里 */
const THEME_KEY = 'app.theme'

type Row = api.SysConfig & { draft: string }
const list = ref<Row[]>([])
const loading = ref(false)
const themeValue = computed(() => list.value.find((c) => c.configKey === THEME_KEY)?.configValue ?? 'paper')
const plainList = computed(() => list.value.filter((c) => c.configKey !== THEME_KEY))

async function load() {
  loading.value = true
  try {
    list.value = (await api.listConfigs()).map((c) => ({ ...c, draft: c.configValue }))
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function save(row: Row) {
  await api.updateConfig(row.configKey, row.draft)
  ElMessage.success('已保存')
  load()
}
</script>

<template>
  <div class="page-card">
    <AppThemeCard :value="themeValue" @saved="load" />
    <el-table v-loading="loading" :data="plainList" border>
      <el-table-column prop="remark" label="说明" min-width="260" />
      <el-table-column prop="configKey" label="参数键" width="220" />
      <el-table-column label="值" width="220">
        <template #default="{ row }">
          <el-input v-model="row.draft" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.draft === row.configValue" @click="save(row as Row)">保存</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
