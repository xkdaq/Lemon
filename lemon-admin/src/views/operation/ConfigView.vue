<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api/operation'
import AppThemeCard from './AppThemeCard.vue'
import DocumentCard from './DocumentCard.vue'

/** 主题配色有专门的选择卡片，不放在下面的通用表格里 */
const THEME_KEY = 'app.theme'

type Row = api.SysConfig & { draft: string }
const list = ref<Row[]>([])
const loading = ref(false)
/** 取值只有 1 / 0 的开关参数：显示成开关，切换即保存 */
const SWITCH_KEYS = ['app.mp_activation', 'app.mp_restore']
const isSwitch = (key: string) => SWITCH_KEYS.includes(key)
/** 密钥参数：后端只返回后 4 位，输入框留空，填新值覆盖 */
const SECRET_KEYS = ['wx.secret']
const isSecret = (key: string) => SECRET_KEYS.includes(key)
const themeValue = computed(() => list.value.find((c) => c.configKey === THEME_KEY)?.configValue ?? 'paper')
const plainList = computed(() => list.value.filter((c) => c.configKey !== THEME_KEY))

async function load() {
  loading.value = true
  try {
    list.value = (await api.listConfigs()).map((c) => ({ ...c, draft: isSecret(c.configKey) ? '' : c.configValue }))
  } finally {
    loading.value = false
  }
}
onMounted(load)

const sending = ref('')
async function feishu(kind: 'test' | 'report') {
  sending.value = kind
  try {
    await (kind === 'test' ? api.sendFeishuTest() : api.sendDailyReport())
    ElMessage.success('已发送，请到飞书群查看')
  } finally {
    sending.value = ''
  }
}

async function toggle(row: Row, value: string) {
  try {
    await api.updateConfig(row.configKey, value)
    ElMessage.success(value === '1' ? '已开启' : '已关闭')
  } finally {
    load()
  }
}

const canSave = (row: Row) => (isSecret(row.configKey) ? row.draft.trim() !== '' : row.draft !== row.configValue)

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
          <el-switch
            v-if="isSwitch(row.configKey)"
            :model-value="row.configValue === '1'"
            active-text="显示"
            inactive-text="隐藏"
            @change="(v: boolean | string | number) => toggle(row as Row, v ? '1' : '0')"
          />
          <el-input
            v-else-if="isSecret(row.configKey)"
            v-model="row.draft"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="row.configValue ? `已设置（${row.configValue}），填新值覆盖` : '未设置'"
          />
          <el-input v-else v-model="row.draft" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-if="!isSwitch(row.configKey)" link type="primary" :disabled="!canSave(row as Row)" @click="save(row as Row)">保存</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="notify-card">
      <div>
        <div class="notify-title">飞书推送</div>
        <div class="notify-sub">重要告警（风险告警、系统异常、短信发送失败）即时推送，每天 9 点推送前一天的日报。先在上面填好 webhook 地址并保存。</div>
      </div>
      <div class="notify-btns">
        <el-button :loading="sending === 'test'" @click="feishu('test')">发送测试消息</el-button>
        <el-button :loading="sending === 'report'" @click="feishu('report')">推送昨天的日报</el-button>
      </div>
    </div>
    <DocumentCard />
  </div>
</template>

<style scoped>
.notify-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px 20px;
  margin-top: 16px;
}
.notify-title {
  font-weight: 600;
}
.notify-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.notify-btns {
  flex-shrink: 0;
}
</style>
