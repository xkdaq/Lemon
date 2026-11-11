<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import * as api from '@/api/access'
import { listModules } from '@/api/content'
import type { ExamModule } from '@/types/content'

/**
 * 激活码：一个码激活一个模块（模块下所有题库都能刷），只能用一次。
 * 有效期两种：激活后有效 N 天，或到某个固定日期为止；同一模块没到期再激活会顺延。
 */
const modules = ref<ExamModule[]>([])
const batches = ref<string[]>([])
const query = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
  moduleId: undefined as number | undefined,
  status: undefined as number | undefined,
  batchName: undefined as string | undefined,
})
const list = ref<api.ActivationCode[]>([])
const total = ref(0)
const loading = ref(false)

const STATUS: Record<number, { label: string; type: 'success' | 'info' | 'danger' }> = {
  0: { label: '未使用', type: 'success' },
  1: { label: '已使用', type: 'info' },
  2: { label: '已作废', type: 'danger' },
}

function filters() {
  return {
    keyword: query.keyword.trim() || undefined,
    moduleId: query.moduleId,
    status: query.status,
    batchName: query.batchName || undefined,
  }
}

async function load() {
  loading.value = true
  try {
    const res = await api.pageCodes({ pageNum: query.pageNum, pageSize: query.pageSize, ...filters() })
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

onMounted(async () => {
  ;[modules.value, batches.value] = await Promise.all([listModules(), api.listBatches()])
  load()
})

const validityText = (c: api.ActivationCode) =>
  c.validityType === 1 ? `激活后 ${c.validDays} 天` : `至 ${c.expireAt?.slice(0, 10)}`

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选中复制')
  }
}

async function voidCode(c: api.ActivationCode) {
  await ElMessageBox.confirm(`作废后激活码 ${c.code} 不能再使用，确定吗？`, '作废激活码', { type: 'warning', confirmButtonText: '作废' })
  await api.voidCode(c.id)
  ElMessage.success('已作废')
  load()
}

async function exportCsv() {
  await api.exportCodes(filters())
}

// ---------- 生成 ----------
const genVisible = ref(false)
const genFormRef = ref<FormInstance>()
const genForm = reactive({
  moduleId: undefined as number | undefined,
  count: 10,
  validityType: 1,
  validDays: 365,
  expireDate: '' as string,
  batchName: '',
})
const genRules: FormRules = {
  moduleId: [{ required: true, message: '请选择模块' }],
  count: [{ required: true, message: '请填写数量' }],
}
const generating = ref(false)
const generated = ref<{ codes: string[]; moduleName: string; validity: string; batchName: string } | null>(null)

function openGenerate() {
  genForm.moduleId = query.moduleId ?? modules.value[0]?.id
  genVisible.value = true
}

const disabledPast = (d: Date) => d.getTime() < new Date(new Date().toDateString()).getTime()

async function doGenerate() {
  await genFormRef.value?.validate()
  if (genForm.validityType === 2 && !genForm.expireDate) return ElMessage.warning('请选择到期日期')
  generating.value = true
  try {
    const { codes } = await api.generateCodes({
      moduleId: genForm.moduleId!,
      count: genForm.count,
      validityType: genForm.validityType,
      validDays: genForm.validityType === 1 ? genForm.validDays : undefined,
      expireDate: genForm.validityType === 2 ? genForm.expireDate : undefined,
      batchName: genForm.batchName.trim() || undefined,
    })
    generated.value = {
      codes,
      moduleName: modules.value.find((m) => m.id === genForm.moduleId)?.name ?? '',
      validity: genForm.validityType === 1 ? `激活后 ${genForm.validDays} 天` : `至 ${genForm.expireDate}`,
      batchName: genForm.batchName.trim(),
    }
    genVisible.value = false
    batches.value = await api.listBatches()
    search()
  } finally {
    generating.value = false
  }
}

const generatedText = computed(() => generated.value?.codes.join('\n') ?? '')

/** 下载刚生成的码（本地生成 CSV，不再请求后端） */
function downloadGenerated() {
  const g = generated.value
  if (!g) return
  const rows = ['激活码,模块,有效期,批次', ...g.codes.map((c) => [c, g.moduleName, g.validity, g.batchName].join(','))]
  const blob = new Blob(['﻿' + rows.join('\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `激活码-${g.moduleName}-${g.codes.length}个.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

const queryBatch = (q: string, cb: (items: { value: string }[]) => void) =>
  cb(batches.value.filter((b) => !q || b.includes(q)).map((value) => ({ value })))
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="激活码 / 使用者手机号 / 用户名" clearable style="width: 240px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.moduleId" placeholder="全部模块" clearable style="width: 130px" @change="search">
        <el-option v-for="m in modules" :key="m.id" :label="m.name" :value="m.id" />
      </el-select>
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="search">
        <el-option v-for="(s, k) in STATUS" :key="k" :label="s.label" :value="Number(k)" />
      </el-select>
      <el-select v-model="query.batchName" placeholder="全部批次" clearable filterable style="width: 160px" @change="search">
        <el-option v-for="b in batches" :key="b" :label="b" :value="b" />
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
      <span class="spacer" />
      <el-button icon="Download" @click="exportCsv">导出</el-button>
      <el-button type="primary" icon="Plus" @click="openGenerate">生成激活码</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="激活码" width="170">
        <template #default="{ row }">
          <span class="code" @click="copy((row as api.ActivationCode).code)">{{ (row as api.ActivationCode).code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="moduleName" label="模块" width="90" />
      <el-table-column label="有效期" width="130">
        <template #default="{ row }">{{ validityText(row as api.ActivationCode) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="STATUS[(row as api.ActivationCode).status]?.type" size="small">
            {{ STATUS[(row as api.ActivationCode).status]?.label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="使用者" min-width="170">
        <template #default="{ row }">
          <template v-if="(row as api.ActivationCode).user">
            <div>{{ (row as api.ActivationCode).user!.nickname }}（{{ (row as api.ActivationCode).user!.username }}）</div>
            <div class="sub">{{ (row as api.ActivationCode).user!.phone }}</div>
          </template>
          <span v-else class="sub">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="usedAt" label="激活时间" width="165" />
      <el-table-column prop="accessUntil" label="模块到期" width="165" />
      <el-table-column prop="batchName" label="批次" min-width="110" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="生成时间" width="165" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="copy((row as api.ActivationCode).code)">复制</el-button>
          <el-button v-if="(row as api.ActivationCode).status === 0" link type="danger" @click="voidCode(row as api.ActivationCode)">作废</el-button>
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

    <!-- 生成 -->
    <el-dialog v-model="genVisible" title="生成激活码" width="480px">
      <el-form ref="genFormRef" :model="genForm" :rules="genRules" label-width="90px">
        <el-form-item label="模块" prop="moduleId">
          <el-select v-model="genForm.moduleId" placeholder="选择模块" style="width: 100%">
            <el-option v-for="m in modules" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="count">
          <el-input-number v-model="genForm.count" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-radio-group v-model="genForm.validityType">
            <el-radio :value="1">激活后有效天数</el-radio>
            <el-radio :value="2">到固定日期</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="genForm.validityType === 1" label="天数">
          <el-input-number v-model="genForm.validDays" :min="1" :max="3650" />
          <span class="hint">从用户激活那天开始算</span>
        </el-form-item>
        <el-form-item v-else label="到期日期">
          <el-date-picker v-model="genForm.expireDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disabledPast" placeholder="到这一天 23:59 为止" />
        </el-form-item>
        <el-form-item label="批次备注">
          <el-autocomplete v-model="genForm.batchName" :fetch-suggestions="queryBatch" maxlength="64" placeholder="如：闲鱼 11 月（选填，方便筛选）" style="width: 100%" />
        </el-form-item>
        <div class="hint block">同一个模块还没到期时再激活会顺延：按天数的从当前到期日往后加，按日期的取较晚的那个。</div>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="doGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 生成结果 -->
    <el-dialog :model-value="!!generated" title="激活码已生成" width="480px" @close="generated = null">
      <template v-if="generated">
        <p>「{{ generated.moduleName }}」· {{ generated.validity }} · 共 {{ generated.codes.length }} 个</p>
        <el-input :model-value="generatedText" type="textarea" :rows="10" readonly class="codes" />
      </template>
      <template #footer>
        <el-button @click="downloadGenerated">下载 CSV</el-button>
        <el-button type="primary" @click="copy(generatedText)">复制全部</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.code {
  font-family: ui-monospace, Menlo, monospace;
  cursor: pointer;
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.hint.block {
  margin: 0 0 0 90px;
}
.codes :deep(textarea) {
  font-family: ui-monospace, Menlo, monospace;
}
</style>
