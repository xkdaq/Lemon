<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api/operation'

type Row = api.SysConfig & { draft: string }
const list = ref<Row[]>([])
const loading = ref(false)

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
    <el-table v-loading="loading" :data="list" border>
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
