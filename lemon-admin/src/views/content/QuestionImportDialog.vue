<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import * as api from '@/api/question'
import ChapterCascader from '@/components/ChapterCascader.vue'
import { useContentTree } from '@/composables/useContentTree'
import { BankType } from '@/types/content'

const emit = defineEmits<{ imported: [] }>()

const { tree, reload } = useContentTree()

const visible = ref(false)
const bankId = ref<number>()
const chapterId = ref<number>()
const file = ref<File>()
const uploading = ref(false)
const result = ref<api.ImportResult | null>(null)

const bank = computed(() => tree.value.find((b) => b.id === bankId.value))
const columnHint = computed(() => {
  if (chapterId.value) return '已选择章节：表格中的“科目”“章节”列会被忽略，所有题目导入到该章节。'
  if (!bank.value) return ''
  return bank.value.bankType === BankType.COURSE
    ? '未选择章节：按表格中的“科目”“章节”列归类，不存在的科目和章节会自动创建。'
    : '未选择试卷：按表格中的“章节”列（试卷名）归类，不存在的会自动创建；“科目”列须为空。'
})

function open(defaults?: { bankId?: number; chapterId?: number }) {
  bankId.value = defaults?.bankId
  chapterId.value = defaults?.chapterId
  file.value = undefined
  result.value = null
  visible.value = true
}

function onFileChange(f: UploadFile) {
  file.value = f.raw
  result.value = null
}

function onBankChange() {
  chapterId.value = undefined
}

async function submit() {
  if (!bankId.value) {
    ElMessage.warning('请选择导入到哪个题库')
    return
  }
  if (!file.value) {
    ElMessage.warning('请选择 Excel 文件')
    return
  }
  uploading.value = true
  try {
    result.value = await api.importQuestions(file.value, bankId.value, chapterId.value)
    if (!result.value.errors.length) {
      const r = result.value
      const created = [r.createdCourses && `${r.createdCourses} 个科目`, r.createdChapters && `${r.createdChapters} 个章节`]
        .filter(Boolean).join('、')
      ElMessage.success(`成功导入 ${r.imported} 题${created ? `，新建 ${created}` : ''}`)
      reload()
      emit('imported')
      visible.value = false
    }
  } finally {
    uploading.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="Excel 导入题目" width="640px" :close-on-click-modal="false">
    <el-form label-width="84px">
      <el-form-item label="导入到" required>
        <el-select v-model="bankId" placeholder="选择题库" filterable style="width: 100%" @change="onBankChange">
          <el-option v-for="b in tree" :key="b.id" :label="b.label" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="章节">
        <ChapterCascader v-model="chapterId" :bank-id="bankId" :placeholder="bankId ? '选填：直接导入到某个章节' : '先选择题库'" />
        <div v-if="columnHint" class="tip">{{ columnHint }}</div>
      </el-form-item>
      <el-form-item label="文件" required>
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept=".xlsx,.xls"
          :on-change="onFileChange"
        >
          <el-button icon="Upload">选择 Excel</el-button>
        </el-upload>
        <span v-if="file" class="file-name">{{ file.name }}</span>
        <el-button link type="primary" style="margin-left: auto" @click="api.downloadTemplate()">下载导入模板</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="result?.errors.length"
      type="error"
      :closable="false"
      :title="`共 ${result.total} 题，其中 ${result.errors.length} 行有问题，本次没有导入任何题目。请修改后重新上传。`"
      style="margin-bottom: 12px"
    />
    <el-table v-if="result?.errors.length" :data="result.errors" max-height="300" border size="small">
      <el-table-column prop="row" label="Excel 行号" width="100" />
      <el-table-column prop="message" label="问题" />
    </el-table>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" @click="submit">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tip {
  width: 100%;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}
.file-name {
  margin-left: 12px;
  color: var(--el-text-color-regular);
}
</style>
