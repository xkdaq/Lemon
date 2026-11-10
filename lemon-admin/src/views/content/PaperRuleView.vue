<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type ElTree, type FormInstance } from 'element-plus'
import * as api from '@/api/paperRule'
import type { TreeNode } from '@/api/content'
import { useContentTree } from '@/composables/useContentTree'

const TYPES = [
  { type: 1, label: '单选题' },
  { type: 2, label: '多选题' },
  { type: 3, label: '判断题' },
  { type: 4, label: '主观题' },
]
const typeLabel = (t: number) => TYPES.find((x) => x.type === t)?.label ?? `题型${t}`
const DIFFICULTY = ['易', '中', '难']

const { tree } = useContentTree()
const banks = computed(() => tree.value)

// ==================== 列表 ====================

const bankFilter = ref<number>()
const list = ref<api.PaperRule[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await api.listPaperRules(bankFilter.value)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const itemsText = (r: api.PaperRule) => r.items.map((i) => `${typeLabel(i.type)} ${i.count}`).join(' · ')
const ratioText = (r: api.PaperRule) => (r.difficultyRatio ? r.difficultyRatio.join(' : ') : '不限')

async function remove(row: api.PaperRule) {
  await ElMessageBox.confirm(`确定删除组卷规则「${row.name}」吗？`, '提示', { type: 'warning' })
  await api.deletePaperRule(row.id)
  ElMessage.success('已删除')
  load()
}

// ==================== 编辑 ====================

interface TypeRow {
  type: number
  label: string
  enabled: boolean
  count: number
}

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  bankId: undefined as number | undefined,
  scope: 'all' as 'all' | 'chapters',
  status: 1,
  useRatio: false,
  ratio: [30, 40, 30],
})
const typeRows = ref<TypeRow[]>([])
const chapterTree = ref<InstanceType<typeof ElTree>>()
const checkedChapters = ref<number[]>([])
const pool = ref<api.PoolStat[]>([])
const saving = ref(false)

/** 所选题库下的科目 / 章节树；key 加前缀避免科目和章节 id 重复 */
interface ScopeNode {
  key: string
  id: number
  kind: string
  label: string
  children?: ScopeNode[]
}
const scopeTree = computed<ScopeNode[]>(() => {
  const bank = banks.value.find((b) => b.id === form.bankId)
  const map = (n: TreeNode): ScopeNode => ({
    key: `${n.kind}-${n.id}`,
    id: n.id,
    kind: n.kind,
    label: n.status === 1 ? n.label : `${n.label}（已下架）`,
    children: n.children?.map(map),
  })
  return bank?.children?.map(map) ?? []
})

function openDialog(row?: api.PaperRule) {
  editingId.value = row?.id ?? null
  Object.assign(form, {
    name: row?.name ?? '',
    bankId: row?.bankId ?? bankFilter.value,
    scope: row?.chapterIds?.length ? 'chapters' : 'all',
    status: row?.status ?? 1,
    useRatio: !!row?.difficultyRatio,
    ratio: row?.difficultyRatio ? [...row.difficultyRatio] : [30, 40, 30],
  })
  typeRows.value = TYPES.map((t) => {
    const item = row?.items.find((i) => i.type === t.type)
    return { ...t, enabled: !!item, count: item?.count ?? 10 }
  })
  checkedChapters.value = row?.chapterIds ? [...row.chapterIds] : []
  dialogVisible.value = true
  nextTick(() => {
    chapterTree.value?.setCheckedKeys(checkedChapters.value.map((id) => `chapter-${id}`))
    loadPool()
  })
}

function onBankChange() {
  checkedChapters.value = []
  chapterTree.value?.setCheckedKeys([])
  loadPool()
}

function onCheck() {
  const nodes = (chapterTree.value?.getCheckedNodes(true) ?? []) as ScopeNode[]
  checkedChapters.value = nodes.filter((n) => n.kind === 'chapter').map((n) => n.id)
  loadPool()
}

let poolSeq = 0
async function loadPool() {
  if (!form.bankId) {
    pool.value = []
    return
  }
  const seq = ++poolSeq
  const ids = form.scope === 'chapters' ? checkedChapters.value : null
  if (form.scope === 'chapters' && !ids?.length) {
    pool.value = []
    return
  }
  const res = await api.getPool(form.bankId, ids)
  if (seq === poolSeq) pool.value = res
}
watch(() => form.scope, loadPool)

const poolOf = (type: number) => pool.value.find((p) => p.type === type)
const poolText = (type: number) => {
  const p = poolOf(type)
  return p ? `可用 ${p.total} 道（易 ${p.byDifficulty[0]} / 中 ${p.byDifficulty[1]} / 难 ${p.byDifficulty[2]}）` : '—'
}
const total = computed(() => typeRows.value.filter((r) => r.enabled).reduce((n, r) => n + r.count, 0))
const ratioSum = computed(() => form.ratio.reduce((a, b) => a + b, 0))

async function save() {
  await formRef.value?.validate()
  const items = typeRows.value.filter((r) => r.enabled).map((r) => ({ type: r.type, count: r.count }))
  if (!items.length) return ElMessage.warning('至少选择一种题型')
  if (form.scope === 'chapters' && !checkedChapters.value.length) return ElMessage.warning('请勾选抽题章节')
  if (form.useRatio && ratioSum.value !== 100) return ElMessage.warning('难度比例合计需为 100%')
  const data: api.PaperRuleSave = {
    bankId: form.bankId!,
    name: form.name,
    chapterIds: form.scope === 'chapters' ? checkedChapters.value : null,
    items,
    difficultyRatio: form.useRatio ? [...form.ratio] : null,
    status: form.status,
  }
  saving.value = true
  try {
    if (editingId.value) {
      await api.updatePaperRule(editingId.value, data)
    } else {
      await api.createPaperRule(data)
    }
  } finally {
    saving.value = false
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

// ==================== 试抽 ====================

const previewVisible = ref(false)
const previewTitle = ref('')
const previewList = ref<api.PreviewQuestion[]>([])
const previewLoading = ref(false)
let previewId = 0

async function preview(row?: api.PaperRule) {
  if (row) {
    previewId = row.id
    previewTitle.value = `试抽：${row.name}`
    previewVisible.value = true
  }
  previewLoading.value = true
  try {
    previewList.value = await api.previewPaperRule(previewId)
  } finally {
    previewLoading.value = false
  }
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-select v-model="bankFilter" placeholder="全部题库" clearable filterable style="width: 260px" @change="load">
        <el-option v-for="b in banks" :key="b.id" :label="b.label" :value="b.id" />
      </el-select>
      <span class="muted">组卷规则决定从哪些章节、按什么题型和难度随机抽题，打卡活动每天按规则出题。</span>
      <span class="spacer" />
      <el-button type="primary" icon="Plus" @click="openDialog()">新建规则</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="bankName" label="题库" min-width="160" />
      <el-table-column label="抽题范围" width="120">
        <template #default="{ row }">
          {{ (row as api.PaperRule).chapterIds ? `${(row as api.PaperRule).chapterIds!.length} 个章节` : '整个题库' }}
        </template>
      </el-table-column>
      <el-table-column label="题型" min-width="200">
        <template #default="{ row }">{{ itemsText(row as api.PaperRule) }}</template>
      </el-table-column>
      <el-table-column label="难度 易:中:难" width="120">
        <template #default="{ row }">{{ ratioText(row as api.PaperRule) }}</template>
      </el-table-column>
      <el-table-column prop="questionCount" label="题数" width="70" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as api.PaperRule).status === 1 ? 'success' : 'info'" size="small">
            {{ (row as api.PaperRule).status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="preview(row as api.PaperRule)">试抽</el-button>
          <el-button link type="primary" @click="openDialog(row as api.PaperRule)">编辑</el-button>
          <el-button link type="danger" @click="remove(row as api.PaperRule)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑组卷规则' : '新建组卷规则'" width="720px" top="6vh">
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="名称" prop="name" :rules="{ required: true, message: '请输入名称' }">
          <el-input v-model="form.name" maxlength="64" placeholder="如：肖秀荣1000题 每日20题" />
        </el-form-item>
        <el-form-item label="题库" prop="bankId" :rules="{ required: true, message: '请选择题库' }">
          <el-select v-model="form.bankId" filterable placeholder="选择抽题题库" style="width: 100%" @change="onBankChange">
            <el-option v-for="b in banks" :key="b.id" :label="b.label" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="抽题范围">
          <el-radio-group v-model="form.scope">
            <el-radio value="all">整个题库</el-radio>
            <el-radio value="chapters">指定章节</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-show="form.scope === 'chapters'" label=" ">
          <div class="scope-tree">
            <el-tree
              ref="chapterTree"
              :data="scopeTree"
              node-key="key"
              show-checkbox
              default-expand-all
              :props="{ label: 'label', children: 'children' }"
              @check="onCheck"
            />
            <div v-if="!scopeTree.length" class="muted">{{ form.bankId ? '题库下还没有章节' : '请先选择题库' }}</div>
          </div>
        </el-form-item>
        <el-form-item label="题型">
          <el-table :data="typeRows" size="small" border style="width: 100%">
            <el-table-column label="抽取" width="70" align="center">
              <template #default="{ row }"><el-checkbox v-model="(row as TypeRow).enabled" /></template>
            </el-table-column>
            <el-table-column prop="label" label="题型" width="90" />
            <el-table-column label="数量" width="150">
              <template #default="{ row }">
                <el-input-number v-model="(row as TypeRow).count" :min="1" :max="200" size="small" :disabled="!(row as TypeRow).enabled" />
              </template>
            </el-table-column>
            <el-table-column label="题池">
              <template #default="{ row }">
                <span :class="{ danger: (row as TypeRow).enabled && (poolOf((row as TypeRow).type)?.total ?? 0) < (row as TypeRow).count }">
                  {{ poolText((row as TypeRow).type) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
          <div class="muted">每次共抽 {{ total }} 题</div>
        </el-form-item>
        <el-form-item label="难度比例">
          <el-switch v-model="form.useRatio" active-text="按比例抽" inactive-text="不限" />
          <template v-if="form.useRatio">
            <span v-for="(d, i) in DIFFICULTY" :key="d" class="ratio">
              {{ d }} <el-input-number v-model="form.ratio[i]" :min="0" :max="100" size="small" controls-position="right" /> %
            </span>
            <span :class="ratioSum === 100 ? 'muted' : 'danger'">合计 {{ ratioSum }}%</span>
          </template>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" :title="previewTitle" width="820px" top="6vh">
      <div class="toolbar">
        <span class="muted">共 {{ previewList.length }} 题；每次试抽结果随机，打卡时同一天所有人题目相同。</span>
        <span class="spacer" />
        <el-button icon="Refresh" :loading="previewLoading" @click="preview()">再抽一次</el-button>
      </div>
      <el-table v-loading="previewLoading" :data="previewList" border max-height="60vh" size="small">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="题型" width="80">
          <template #default="{ row }">{{ typeLabel((row as api.PreviewQuestion).type) }}</template>
        </el-table-column>
        <el-table-column label="难度" width="60">
          <template #default="{ row }">{{ DIFFICULTY[(row as api.PreviewQuestion).difficulty - 1] ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="chapterName" label="章节" width="160" show-overflow-tooltip />
        <el-table-column prop="stemText" label="题干" min-width="300" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.danger {
  color: var(--el-color-danger);
  font-size: 13px;
}
.scope-tree {
  width: 100%;
  max-height: 260px;
  overflow: auto;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 6px;
}
.ratio {
  margin-left: 16px;
}
.ratio :deep(.el-input-number) {
  width: 90px;
}
</style>
