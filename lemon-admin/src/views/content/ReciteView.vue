<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import * as api from '@/api/recite'
import { useContentTree } from '@/composables/useContentTree'

const { tree } = useContentTree()
const banks = computed(() => tree.value)

// ==================== 列表 ====================

const list = ref<api.ReciteBook[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await api.listReciteBooks()
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function toggleStatus(row: api.ReciteBook, val: string | number | boolean) {
  const status = val ? 1 : 0
  await api.updateReciteBook(row.id, { status })
  row.status = status
  ElMessage.success(status ? '已上架' : '已下架')
}

async function remove(row: api.ReciteBook) {
  await ElMessageBox.confirm(
    `确定删除背诵资料「${row.name}」吗？删除后用户端立即看不到，题库里的题目不受影响。`,
    '提示',
    { type: 'warning' },
  )
  await api.deleteReciteBook(row.id)
  ElMessage.success('已删除')
  load()
}

// ==================== 编辑名称 / 简介 / 排序 ====================

const editVisible = ref(false)
const editing = reactive({ id: 0, name: '', intro: '', sort: 0 })
const editSaving = ref(false)

function openEdit(row: api.ReciteBook) {
  Object.assign(editing, { id: row.id, name: row.name, intro: row.intro ?? '', sort: row.sort })
  editVisible.value = true
}

async function saveEdit() {
  if (!editing.name.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  editSaving.value = true
  try {
    await api.updateReciteBook(editing.id, { name: editing.name.trim(), intro: editing.intro.trim(), sort: editing.sort })
    ElMessage.success('已保存')
    editVisible.value = false
    load()
  } finally {
    editSaving.value = false
  }
}

// ==================== 导入 ====================

const importVisible = ref(false)
const importTarget = ref<api.ReciteBook | null>(null)
const importFile = ref<File | null>(null)
const linkBankId = ref<number>()
const importing = ref(false)
const result = ref<api.ReciteImportResult | null>(null)

function openImport(row?: api.ReciteBook) {
  importTarget.value = row ?? null
  importFile.value = null
  linkBankId.value = row?.linkBankId ?? undefined
  result.value = null
  importVisible.value = true
}

function onFile(f: UploadFile) {
  importFile.value = f.raw ?? null
}

async function doImport() {
  if (!importFile.value) {
    ElMessage.warning('请选择背诵资料 JSON 文件')
    return
  }
  importing.value = true
  try {
    result.value = await api.importReciteBook(importFile.value, importTarget.value?.id, linkBankId.value)
    ElMessage.success(`导入成功，共 ${result.value.itemCount} 题`)
    load()
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="muted">
        背诵模块免费开放，用户端底部“背诵”tab 里按资料展示。资料独立存储，不会修改题库里的题目。
      </span>
      <span class="spacer" />
      <el-button type="primary" icon="Upload" @click="openImport()">导入新资料</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="名称" min-width="200">
        <template #default="{ row }">
          <div>{{ (row as api.ReciteBook).name }}</div>
          <div v-if="(row as api.ReciteBook).intro" class="muted small">{{ (row as api.ReciteBook).intro }}</div>
        </template>
      </el-table-column>
      <el-table-column label="套数 / 题数" width="110">
        <template #default="{ row }">
          {{ (row as api.ReciteBook).sets.length }} 套 / {{ (row as api.ReciteBook).itemCount }} 题
        </template>
      </el-table-column>
      <el-table-column label="对照题库" min-width="190">
        <template #default="{ row }">
          <template v-if="(row as api.ReciteBook).linkBankId">
            <div>{{ (row as api.ReciteBook).linkBankName ?? `题库 ${(row as api.ReciteBook).linkBankId}（已删除）` }}</div>
            <div class="muted small">
              对照上 {{ (row as api.ReciteBook).linkedCount }} / {{ (row as api.ReciteBook).itemCount }} 题
            </div>
          </template>
          <span v-else class="muted">未对照</span>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="70" />
      <el-table-column label="上架" width="80">
        <template #default="{ row }">
          <el-switch
            :model-value="(row as api.ReciteBook).status === 1"
            @change="(v: string | number | boolean) => toggleStatus(row as api.ReciteBook, v)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="150" />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as api.ReciteBook)">编辑</el-button>
          <el-button link type="primary" @click="openImport(row as api.ReciteBook)">重新导入</el-button>
          <el-button link type="danger" @click="remove(row as api.ReciteBook)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑 -->
    <el-dialog v-model="editVisible" title="编辑背诵资料" width="480px">
      <el-form label-width="70px">
        <el-form-item label="名称" required>
          <el-input v-model="editing.name" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editing.intro" type="textarea" :rows="2" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editing.sort" :min="0" :max="9999" />
          <span class="muted small" style="margin-left: 8px">越小越靠前</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入 -->
    <el-dialog
      v-model="importVisible"
      :title="importTarget ? `重新导入「${importTarget.name}」` : '导入新背诵资料'"
      width="560px"
    >
      <el-form label-width="80px">
        <el-form-item label="资料文件" required>
          <el-upload :auto-upload="false" :limit="1" accept=".json" :on-change="onFile" :on-remove="() => (importFile = null)">
            <el-button icon="Document">选择 JSON 文件</el-button>
            <template #tip>
              <div class="muted small">
                由背诵网站的 export_lemon.py 生成。
                <template v-if="importTarget">重新导入会替换这份资料的全部条目，用户的“已掌握”标记按套号和题号保留。</template>
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="对照题库">
          <el-select v-model="linkBankId" placeholder="不对照" clearable filterable style="width: 100%">
            <el-option v-for="b in banks" :key="b.id" :label="b.label" :value="b.id" />
          </el-select>
          <div class="muted small">
            可选。按题干只读对照题库里的题目，对照上后用户端可以“去刷这套”。不会修改题库。
          </div>
        </el-form-item>
      </el-form>

      <el-alert v-if="result" :closable="false" type="success" show-icon style="margin-top: 8px">
        <template #title>
          导入 {{ result.itemCount }} 题<template v-if="linkBankId">，对照上题库 {{ result.linkedCount }} 题</template>
        </template>
        <div v-if="linkBankId && result.unlinked.length" class="small">
          没对照上的题（{{ result.unlinked.length }}）：{{ result.unlinked.slice(0, 30).join('、') }}
          <template v-if="result.unlinked.length > 30"> 等</template>
        </div>
      </el-alert>

      <template #footer>
        <el-button @click="importVisible = false">{{ result ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!result" type="primary" :loading="importing" @click="doImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.small {
  font-size: 12px;
  line-height: 1.5;
}
</style>
