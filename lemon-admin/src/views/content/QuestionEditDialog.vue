<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api/question'
import RichEditor from '@/components/RichEditor.vue'
import ChapterCascader from '@/components/ChapterCascader.vue'
import { QuestionType, questionTypeLabel } from '@/types/content'
import { htmlToText } from '@/utils/html'

/**
 * 新建 / 编辑题目。
 * 选项在表单里按顺序存放，答案存“选中的下标”，保存时再按顺序生成 A/B/C… 和答案字母，
 * 这样删掉中间某个选项时字母自动顺延，答案也不会错位。
 */
const emit = defineEmits<{ saved: [] }>()

const MAX_OPTIONS = 8
const LETTERS = 'ABCDEFGH'
const JUDGE_DEFAULT = ['正确', '错误']

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
/** 连续新建：保存后清空题干等内容，保留章节和题型 */
const keepCreating = ref(false)

const form = reactive({
  chapterId: undefined as number | undefined,
  type: QuestionType.SINGLE as number,
  stem: '',
  options: [] as string[],
  /** 单选 / 判断：选中的下标 */
  single: -1,
  /** 多选：选中的下标 */
  multiple: [] as number[],
  analysis: '',
  score: 0,
  difficulty: 2,
  source: '',
  status: 1,
})

const isChoice = computed(() => form.type === QuestionType.SINGLE || form.type === QuestionType.MULTIPLE)
const isSubjective = computed(() => form.type === QuestionType.SUBJECTIVE)
/** 判断题选项：默认 正确/错误；导入的数据可能是自定义的两个选项 */
const judgeOptions = computed(() => (form.options.length === 2 ? form.options : JUDGE_DEFAULT))

function resetContent() {
  Object.assign(form, { stem: '', options: ['', '', '', ''], single: -1, multiple: [], analysis: '', source: '' })
}

function open(id?: number, defaults?: { chapterId?: number; type?: number }) {
  editingId.value = id ?? null
  keepCreating.value = false
  resetContent()
  Object.assign(form, { score: 0, difficulty: 2, status: 1, type: defaults?.type ?? QuestionType.SINGLE, chapterId: defaults?.chapterId })
  visible.value = true
  if (id) {
    load(id)
  }
}

async function load(id: number) {
  loading.value = true
  try {
    const q = await api.getQuestion(id)
    const keys = q.options.map((o) => o.key)
    const answer = q.answer ?? ''
    Object.assign(form, {
      chapterId: q.chapterId,
      type: q.type,
      stem: q.stem,
      options: q.type === QuestionType.SUBJECTIVE ? [] : q.options.map((o) => o.content),
      single: keys.indexOf(answer),
      multiple: answer.split('').map((k) => keys.indexOf(k)).filter((i) => i >= 0),
      analysis: q.analysis ?? '',
      score: Number(q.score ?? 0),
      difficulty: q.difficulty ?? 2,
      source: q.source ?? '',
      status: q.status,
    })
  } finally {
    loading.value = false
  }
}

function onTypeChange(type: number) {
  form.single = -1
  form.multiple = []
  if (type === QuestionType.SINGLE || type === QuestionType.MULTIPLE) {
    if (form.options.length < 2 || form.options.every((o, i) => o === JUDGE_DEFAULT[i])) {
      form.options = ['', '', '', '']
    }
  } else if (type === QuestionType.JUDGE) {
    form.options = []
  }
}

function addOption() {
  if (form.options.length < MAX_OPTIONS) {
    form.options.push('')
  }
}

function removeOption(index: number) {
  form.options.splice(index, 1)
  // 答案下标跟着调整
  if (form.single === index) form.single = -1
  else if (form.single > index) form.single--
  form.multiple = form.multiple.filter((i) => i !== index).map((i) => (i > index ? i - 1 : i))
}

/** 前端先做一遍基本校验，给出更直接的提示；最终以后端校验为准 */
function validate(): string | null {
  if (!form.chapterId) return '请选择所属章节'
  if (!htmlToText(form.stem) && !form.stem.includes('<img')) return '请填写题干'
  if (isChoice.value) {
    const filled = form.options.filter((o) => o.trim())
    if (filled.length < 2) return '至少需要 2 个选项'
    if (filled.length !== form.options.length) return '有空的选项，请填写或删除'
    if (form.type === QuestionType.SINGLE && form.single < 0) return '请选择正确答案'
    if (form.type === QuestionType.MULTIPLE && !form.multiple.length) return '请至少选择一个正确答案'
  }
  if (form.type === QuestionType.JUDGE && form.single < 0) return '请选择正确答案'
  if (isSubjective.value && !htmlToText(form.analysis) && !form.analysis.includes('<img')) return '主观题请填写参考答案'
  return null
}

function payload(): api.QuestionSave {
  let options: { key: string; content: string }[] = []
  let answer = ''
  if (isChoice.value) {
    options = form.options.map((content, i) => ({ key: LETTERS[i], content: content.trim() }))
    answer = form.type === QuestionType.SINGLE
      ? LETTERS[form.single]
      : [...form.multiple].sort().map((i) => LETTERS[i]).join('')
  } else if (form.type === QuestionType.JUDGE) {
    options = judgeOptions.value.map((content, i) => ({ key: LETTERS[i], content }))
    answer = LETTERS[form.single]
  }
  return {
    chapterId: form.chapterId!,
    type: form.type,
    stem: form.stem,
    options,
    answer: answer || undefined,
    analysis: form.analysis || undefined,
    score: form.score,
    difficulty: form.difficulty,
    source: form.source.trim() || undefined,
    status: form.status,
  }
}

async function save() {
  const error = validate()
  if (error) {
    ElMessage.warning(error)
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await api.updateQuestion(editingId.value, payload())
    } else {
      await api.createQuestion(payload())
    }
    ElMessage.success('保存成功')
    emit('saved')
    if (!editingId.value && keepCreating.value) {
      resetContent()
    } else {
      visible.value = false
    }
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="editingId ? `编辑题目 #${editingId}` : '新建题目'"
    width="820px"
    top="4vh"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-form v-loading="loading" label-width="84px">
      <el-form-item label="所属章节" required>
        <ChapterCascader v-model="form.chapterId" placeholder="选择 题库 / 科目 / 章节" />
      </el-form-item>
      <el-form-item label="题型" required>
        <el-radio-group v-model="form.type" @change="(v) => onTypeChange(Number(v))">
          <el-radio-button v-for="(label, code) in questionTypeLabel" :key="code" :value="Number(code)">{{ label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="题干" required>
        <RichEditor v-model="form.stem" placeholder="输入题干，可粘贴图片；材料题的材料可用“引用”格式" :min-height="100" />
      </el-form-item>

      <!-- 单选 / 多选 -->
      <el-form-item v-if="isChoice" label="选项" required>
        <div class="options">
          <div v-for="(_, i) in form.options" :key="i" class="option-row">
            <el-radio
              v-if="form.type === QuestionType.SINGLE"
              v-model="form.single"
              :value="i"
              class="option-mark"
            >{{ LETTERS[i] }}</el-radio>
            <el-checkbox
              v-else
              :model-value="form.multiple.includes(i)"
              class="option-mark"
              @change="(checked) => checked ? form.multiple.push(i) : (form.multiple = form.multiple.filter((x) => x !== i))"
            >{{ LETTERS[i] }}</el-checkbox>
            <el-input v-model="form.options[i]" :placeholder="`选项 ${LETTERS[i]}`" maxlength="2000" />
            <el-button link type="danger" icon="Delete" :disabled="form.options.length <= 2" @click="removeOption(i)" />
          </div>
          <div class="option-foot">
            <el-button link type="primary" icon="Plus" :disabled="form.options.length >= MAX_OPTIONS" @click="addOption">添加选项</el-button>
            <span class="tip">勾选左侧字母设置正确答案{{ form.type === QuestionType.MULTIPLE ? '（可多选）' : '' }}</span>
          </div>
        </div>
      </el-form-item>

      <!-- 判断 -->
      <el-form-item v-if="form.type === QuestionType.JUDGE" label="答案" required>
        <el-radio-group v-model="form.single">
          <el-radio v-for="(text, i) in judgeOptions" :key="i" :value="i">{{ text }}</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item :label="isSubjective ? '参考答案' : '解析'" :required="isSubjective">
        <RichEditor
          v-model="form.analysis"
          :placeholder="isSubjective ? '主观题的参考答案 / 得分要点' : '选填：答案解析'"
          :min-height="80"
        />
      </el-form-item>

      <el-row>
        <el-col :span="8">
          <el-form-item label="分值">
            <el-input-number v-model="form.score" :min="0" :max="999.9" :step="0.5" :precision="1" controls-position="right" style="width: 120px" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="难度">
            <el-radio-group v-model="form.difficulty">
              <el-radio-button :value="1">简单</el-radio-button>
              <el-radio-button :value="2">中等</el-radio-button>
              <el-radio-button :value="3">困难</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="来源">
        <el-input v-model="form.source" maxlength="64" placeholder="选填，如：2023 真题" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-checkbox v-if="!editingId" v-model="keepCreating" style="float: left">保存后继续新建（保留章节和题型）</el-checkbox>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.options {
  width: 100%;
}
.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.option-mark {
  width: 44px;
  margin-right: 0;
}
.option-foot {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
