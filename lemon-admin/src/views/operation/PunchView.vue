<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type UploadFile } from 'element-plus'
import * as api from '@/api/punch'
import { listPaperRules, type PaperRule } from '@/api/paperRule'
import { uploadImage } from '@/api/file'

const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']
const router = useRouter()

const list = ref<api.PunchCamp[]>([])
const loading = ref(false)
const rules = ref<PaperRule[]>([])

async function load() {
  loading.value = true
  try {
    list.value = await api.listCamps()
  } finally {
    loading.value = false
  }
}
onMounted(load)

const restText = (c: api.PunchCamp) => (c.restWeekdays.length ? `周${c.restWeekdays.map((d) => WEEKDAYS[d - 1]).join('、')}休息` : '每天')

async function toggle(row: api.PunchCamp) {
  await api.updateCampStatus(row.id, row.status === 1 ? 0 : 1)
  load()
}

async function remove(row: api.PunchCamp) {
  await ElMessageBox.confirm(`确定删除打卡活动「${row.name}」吗？用户端将不再显示。`, '提示', { type: 'warning' })
  await api.deleteCamp(row.id)
  ElMessage.success('已删除')
  load()
}

// ==================== 编辑 ====================

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const uploading = ref(false)
const saving = ref(false)
const form = reactive({
  name: '',
  cover: '' as string | null,
  intro: '' as string | null,
  paperRuleId: undefined as number | undefined,
  dates: [] as string[],
  restWeekdays: [] as number[],
  makeupLimit: 3,
  sort: 0,
  status: 1,
})

async function openDialog(row?: api.PunchCamp) {
  editingId.value = row?.id ?? null
  Object.assign(form, {
    name: row?.name ?? '',
    cover: row?.cover ?? '',
    intro: row?.intro ?? '',
    paperRuleId: row?.paperRuleId,
    dates: row ? [row.startDate, row.endDate] : [],
    restWeekdays: row ? [...row.restWeekdays] : [],
    makeupLimit: row?.makeupLimit ?? 3,
    sort: row?.sort ?? 0,
    status: row?.status ?? 1,
  })
  dialogVisible.value = true
  rules.value = await listPaperRules()
}

async function onPick(file: UploadFile) {
  if (!file.raw) return
  uploading.value = true
  try {
    form.cover = await uploadImage(file.raw)
  } finally {
    uploading.value = false
  }
}

async function save() {
  await formRef.value?.validate()
  const data: api.CampSave = {
    name: form.name,
    cover: form.cover || null,
    intro: form.intro?.trim() || null,
    paperRuleId: form.paperRuleId!,
    startDate: form.dates[0],
    endDate: form.dates[1],
    restWeekdays: form.restWeekdays,
    makeupLimit: form.makeupLimit,
    sort: form.sort,
    status: form.status,
  }
  saving.value = true
  try {
    if (editingId.value) {
      await api.updateCamp(editingId.value, data)
    } else {
      await api.createCamp(data)
    }
  } finally {
    saving.value = false
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="muted">活动期内每天按组卷规则抽一套题，学员做完即完成当天打卡。题目先在「组卷规则」里配置。</span>
      <span class="spacer" />
      <el-button type="primary" icon="Plus" @click="openDialog()">新建活动</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="封面" width="110">
        <template #default="{ row }">
          <el-image v-if="(row as api.PunchCamp).cover" :src="(row as api.PunchCamp).cover!" fit="cover" class="cover" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="paperRuleName" label="默认组卷规则" min-width="160" />
      <el-table-column label="活动期" width="200">
        <template #default="{ row }">
          <div>{{ (row as api.PunchCamp).startDate }} ~ {{ (row as api.PunchCamp).endDate }}</div>
          <div class="muted">{{ restText(row as api.PunchCamp) }} · 可补卡 {{ (row as api.PunchCamp).makeupLimit }} 次</div>
        </template>
      </el-table-column>
      <el-table-column prop="memberCount" label="学员" width="70" />
      <el-table-column prop="todayCount" label="今日打卡" width="85" />
      <el-table-column prop="sort" label="排序" width="65" />
      <el-table-column label="状态" width="75">
        <template #default="{ row }">
          <el-tag :type="(row as api.PunchCamp).status === 1 ? 'success' : 'info'" size="small">
            {{ (row as api.PunchCamp).status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push({ name: 'punchDetail', params: { id: (row as api.PunchCamp).id } })">日程 / 学员</el-button>
          <el-button link type="primary" @click="openDialog(row as api.PunchCamp)">编辑</el-button>
          <el-button link type="primary" @click="toggle(row as api.PunchCamp)">{{ (row as api.PunchCamp).status === 1 ? '下架' : '上架' }}</el-button>
          <el-button link type="danger" @click="remove(row as api.PunchCamp)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑打卡活动' : '新建打卡活动'" width="620px" top="6vh">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="名称" prop="name" :rules="{ required: true, message: '请输入名称' }">
          <el-input v-model="form.name" maxlength="64" placeholder="如：27考研政治每日一练" />
        </el-form-item>
        <el-form-item label="封面">
          <div class="image-field">
            <el-image v-if="form.cover" :src="form.cover" fit="cover" class="cover" />
            <el-upload :auto-upload="false" :show-file-list="false" accept="image/png,image/jpeg,image/webp" :on-change="onPick">
              <el-button :loading="uploading" icon="Upload">{{ form.cover ? '更换' : '上传' }}</el-button>
            </el-upload>
            <el-button v-if="form.cover" link type="danger" @click="form.cover = ''">移除</el-button>
          </div>
        </el-form-item>
        <el-form-item label="介绍">
          <el-input v-model="form.intro" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="打卡规则、题源说明等" />
        </el-form-item>
        <el-form-item label="组卷规则" prop="paperRuleId" :rules="{ required: true, message: '请选择组卷规则' }">
          <el-select v-model="form.paperRuleId" filterable placeholder="每天按此规则抽题" style="width: 100%">
            <el-option v-for="r in rules" :key="r.id" :label="`${r.name}（${r.bankName ?? ''} · ${r.questionCount} 题）`" :value="r.id" />
          </el-select>
          <div class="muted">某些日期要换题源，可在「日程」里按日期单独设置。</div>
        </el-form-item>
        <el-form-item label="活动期" prop="dates" :rules="{ required: true, type: 'array', len: 2, message: '请选择活动日期' }">
          <el-date-picker v-model="form.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item label="休息日">
          <el-checkbox-group v-model="form.restWeekdays">
            <el-checkbox v-for="(d, i) in WEEKDAYS" :key="d" :value="i + 1">周{{ d }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="补卡次数">
          <el-input-number v-model="form.makeupLimit" :min="0" :max="365" />
          <span class="muted" style="margin-left: 8px">每人最多补卡次数，0 为不允许补卡</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
          <span class="muted" style="margin-left: 8px">越小越靠前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.cover {
  width: 88px;
  height: 50px;
  border-radius: 4px;
}
.image-field {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
