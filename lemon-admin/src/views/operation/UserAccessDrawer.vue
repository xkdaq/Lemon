<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api/access'

/** 用户的模块权限：查看到期时间、手动开通 / 延长 / 取消，以及变更记录（激活码、后台操作） */
const props = defineProps<{ userId: number | null }>()
const emit = defineEmits<{ close: [] }>()

const data = ref<api.UserAccess | null>(null)
const loading = ref(false)

async function load() {
  if (!props.userId) return
  loading.value = true
  try {
    data.value = await api.getUserAccess(props.userId)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.userId,
  (v) => {
    data.value = null
    if (v) load()
  },
  { immediate: true },
)

const SOURCE: Record<number, string> = { 1: '激活码', 2: '后台开通 / 延长', 3: '后台取消' }

// ---------- 开通 / 延长 ----------
const grantTarget = ref<api.ModuleAccess | null>(null)
const grantForm = reactive({ mode: 'days' as 'days' | 'until', days: 365, until: '', remark: '' })
const saving = ref(false)

function openGrant(m: api.ModuleAccess) {
  grantTarget.value = m
  grantForm.mode = 'days'
  grantForm.days = 365
  grantForm.until = ''
  grantForm.remark = ''
}

const disabledPast = (d: Date) => d.getTime() < new Date(new Date().toDateString()).getTime()

async function doGrant() {
  const m = grantTarget.value
  if (!m || !props.userId) return
  if (grantForm.mode === 'until' && !grantForm.until) return ElMessage.warning('请选择到期日期')
  saving.value = true
  try {
    const r = await api.grantAccess(props.userId, {
      moduleId: m.moduleId,
      days: grantForm.mode === 'days' ? grantForm.days : undefined,
      until: grantForm.mode === 'until' ? grantForm.until : undefined,
      remark: grantForm.remark.trim() || undefined,
    })
    ElMessage.success(`已开通，「${m.moduleName}」到期时间 ${r.expireAt}`)
    grantTarget.value = null
    load()
  } finally {
    saving.value = false
  }
}

async function revoke(m: api.ModuleAccess) {
  if (!props.userId) return
  const { value } = await ElMessageBox.prompt(`取消后「${m.moduleName}」立即到期，用户只能试做。可以填写原因（选填）：`, '取消模块权限', {
    confirmButtonText: '取消权限',
    cancelButtonText: '返回',
    inputPlaceholder: '原因，如：退款',
    type: 'warning',
  })
  await api.revokeAccess(props.userId, { moduleId: m.moduleId, remark: value?.trim() || undefined })
  ElMessage.success('已取消')
  load()
}
</script>

<template>
  <el-drawer :model-value="!!userId" size="640px" @close="emit('close')">
    <template #header>
      <span v-if="data">模块权限 ·「{{ data.user.nickname }}」（{{ data.user.username }} · {{ data.user.phone }}）</span>
      <span v-else>模块权限</span>
    </template>
    <div v-loading="loading">
      <template v-if="data">
        <el-table :data="data.modules" border>
          <el-table-column prop="moduleName" label="模块" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="(row as api.ModuleAccess).active" type="success" size="small">已激活</el-tag>
              <el-tag v-else-if="(row as api.ModuleAccess).expireAt" type="info" size="small">已过期</el-tag>
              <el-tag v-else type="info" size="small">未激活</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="expireAt" label="到期时间" width="170" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button link type="primary" @click="openGrant(row as api.ModuleAccess)">
                {{ (row as api.ModuleAccess).active ? '延长' : '开通' }}
              </el-button>
              <el-button v-if="(row as api.ModuleAccess).active" link type="danger" @click="revoke(row as api.ModuleAccess)">取消</el-button>
            </template>
          </el-table-column>
        </el-table>

        <h4 class="title">变更记录</h4>
        <el-table :data="data.logs" border size="small" empty-text="还没有激活记录">
          <el-table-column prop="createdAt" label="时间" width="160" />
          <el-table-column prop="moduleName" label="模块" width="80" />
          <el-table-column label="方式" width="110">
            <template #default="{ row }">
              {{ SOURCE[(row as api.AccessLog).source] }}
              <div v-if="(row as api.AccessLog).code" class="sub mono">{{ (row as api.AccessLog).code }}</div>
              <div v-if="(row as api.AccessLog).adminName" class="sub">{{ (row as api.AccessLog).adminName }}</div>
            </template>
          </el-table-column>
          <el-table-column label="到期时间变化" min-width="150">
            <template #default="{ row }">
              <div class="sub">{{ (row as api.AccessLog).beforeExpire ?? '无' }}</div>
              <div>→ {{ (row as api.AccessLog).afterExpire }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="90" show-overflow-tooltip />
        </el-table>
      </template>
    </div>

    <el-dialog :model-value="!!grantTarget" :title="`${grantTarget?.active ? '延长' : '开通'}「${grantTarget?.moduleName}」`" width="420px" append-to-body @close="grantTarget = null">
      <el-form label-width="80px">
        <el-form-item label="方式">
          <el-radio-group v-model="grantForm.mode">
            <el-radio value="days">增加天数</el-radio>
            <el-radio value="until">指定到期日</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="grantForm.mode === 'days'" label="天数">
          <el-input-number v-model="grantForm.days" :min="1" :max="3650" />
        </el-form-item>
        <el-form-item v-else label="到期日期">
          <el-date-picker v-model="grantForm.until" type="date" value-format="YYYY-MM-DD" :disabled-date="disabledPast" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="grantForm.remark" maxlength="255" placeholder="如：补偿、线下付款（选填）" />
        </el-form-item>
        <div class="sub hint">增加天数：没到期从当前到期日往后加，已过期从今天算。指定到期日：直接设为这一天 23:59。</div>
      </el-form>
      <template #footer>
        <el-button @click="grantTarget = null">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doGrant">确定</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.title {
  margin: 20px 0 10px;
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.mono {
  font-family: ui-monospace, Menlo, monospace;
}
.hint {
  margin-left: 80px;
}
</style>
