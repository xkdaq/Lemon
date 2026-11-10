<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import * as api from '@/api/content'
import { useContentTree } from '@/composables/useContentTree'
import { BankType, bankTypeLabel, type Chapter, type Course, type QuestionBank } from '@/types/content'

/**
 * 题库结构维护：
 * - 科目型：左侧科目列表，右侧选中科目的章节
 * - 试卷型：只有章节（试卷）列表，可设置考试时长和总分
 */
const route = useRoute()
const router = useRouter()
const { reload: reloadTree } = useContentTree()
const bankId = Number(route.params.id)

const bank = ref<QuestionBank>()
const isCourseBank = computed(() => bank.value?.type === BankType.COURSE)
const courses = ref<Course[]>([])
const activeCourseId = ref<number>()
const chapters = ref<Chapter[]>([])

async function loadCourses() {
  courses.value = await api.listCourses(bankId)
  if (!courses.value.some((c) => c.id === activeCourseId.value)) {
    activeCourseId.value = courses.value[0]?.id
  }
}

async function loadChapters() {
  if (isCourseBank.value && !activeCourseId.value) {
    chapters.value = []
    return
  }
  chapters.value = await api.listChapters(bankId, isCourseBank.value ? activeCourseId.value : undefined)
}

function selectCourse(id: number) {
  activeCourseId.value = id
  loadChapters()
}

onMounted(async () => {
  bank.value = await api.getBank(bankId)
  if (isCourseBank.value) {
    await loadCourses()
  }
  loadChapters()
})

// ---- 科目弹窗 ----
const courseDialog = ref(false)
const courseEditingId = ref<number | null>(null)
const courseFormRef = ref<FormInstance>()
const courseForm = reactive<api.CourseSave>({ bankId, name: '', sort: 0, status: 1 })

function openCourse(row?: Course) {
  courseEditingId.value = row?.id ?? null
  Object.assign(courseForm, { bankId, name: row?.name ?? '', sort: row?.sort ?? 0, status: row?.status ?? 1 })
  courseDialog.value = true
}

async function saveCourse() {
  await courseFormRef.value?.validate()
  if (courseEditingId.value) {
    await api.updateCourse(courseEditingId.value, courseForm)
  } else {
    activeCourseId.value = await api.createCourse(courseForm)
  }
  ElMessage.success('保存成功')
  courseDialog.value = false
  await loadCourses()
  loadChapters()
  reloadTree()
}

async function removeCourse(row: Course) {
  await ElMessageBox.confirm(`确定删除科目「${row.name}」吗？`, '提示', { type: 'warning' })
  await api.deleteCourse(row.id)
  ElMessage.success('已删除')
  await loadCourses()
  loadChapters()
}

// ---- 章节弹窗 ----
const chapterDialog = ref(false)
const chapterEditingId = ref<number | null>(null)
const chapterFormRef = ref<FormInstance>()
const chapterForm = reactive<api.ChapterSave>({
  bankId, courseId: null, name: '', sort: 0, status: 1, timeLimit: null, totalScore: null,
})

function openChapter(row?: Chapter) {
  chapterEditingId.value = row?.id ?? null
  Object.assign(chapterForm, {
    bankId,
    courseId: isCourseBank.value ? (row?.courseId ?? activeCourseId.value) : null,
    name: row?.name ?? '',
    sort: row?.sort ?? 0,
    status: row?.status ?? 1,
    timeLimit: row?.timeLimit ?? (isCourseBank.value ? null : 180),
    totalScore: row?.totalScore ?? (isCourseBank.value ? null : 100),
  })
  chapterDialog.value = true
}

async function saveChapter() {
  await chapterFormRef.value?.validate()
  if (chapterEditingId.value) {
    await api.updateChapter(chapterEditingId.value, chapterForm)
  } else {
    await api.createChapter(chapterForm)
  }
  ElMessage.success('保存成功')
  chapterDialog.value = false
  loadChapters()
  reloadTree()
}

async function removeChapter(row: Chapter) {
  await ElMessageBox.confirm(`确定删除「${row.name}」吗？`, '提示', { type: 'warning' })
  await api.deleteChapter(row.id)
  ElMessage.success('已删除')
  loadChapters()
  reloadTree()
}

const chapterLabel = computed(() => (isCourseBank.value ? '章节' : '试卷'))
</script>

<template>
  <div class="page-card">
    <el-page-header @back="router.push('/content/banks')">
      <template #content>
        <span>{{ bank?.name }}</span>
        <el-tag v-if="bank" style="margin-left: 8px">{{ bankTypeLabel[bank.type] }}</el-tag>
      </template>
    </el-page-header>

    <el-row :gutter="16" style="margin-top: 20px">
      <!-- 科目列表（仅科目型） -->
      <el-col v-if="isCourseBank" :span="7">
        <div class="toolbar">
          <strong>科目</strong>
          <span class="spacer" />
          <el-button size="small" type="primary" icon="Plus" @click="openCourse()">新建科目</el-button>
        </div>
        <el-empty v-if="!courses.length" description="还没有科目，例如：马原、毛中特" :image-size="80" />
        <div
          v-for="c in courses"
          :key="c.id"
          class="course-item"
          :class="{ active: c.id === activeCourseId }"
          @click="selectCourse(c.id)"
        >
          <span>{{ c.name }}<el-tag v-if="c.status === 0" size="small" type="info" style="margin-left: 6px">下架</el-tag></span>
          <span @click.stop>
            <el-button link size="small" icon="Edit" @click="openCourse(c)" />
            <el-button link size="small" type="danger" icon="Delete" @click="removeCourse(c)" />
          </span>
        </div>
      </el-col>

      <!-- 章节 / 试卷列表 -->
      <el-col :span="isCourseBank ? 17 : 24">
        <div class="toolbar">
          <strong>{{ chapterLabel }}</strong>
          <span class="spacer" />
          <el-button
            size="small"
            type="primary"
            icon="Plus"
            :disabled="isCourseBank && !activeCourseId"
            @click="openChapter()"
          >新建{{ chapterLabel }}</el-button>
        </div>
        <el-table :data="chapters" border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" :label="`${chapterLabel}名称`" min-width="200" />
          <el-table-column prop="questionCount" label="题量" width="80" />
          <template v-if="!isCourseBank">
            <el-table-column prop="timeLimit" label="时长(分钟)" width="100" />
            <el-table-column prop="totalScore" label="总分" width="80" />
          </template>
          <el-table-column prop="sort" label="排序" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push({ name: 'questions', query: { chapterId: row.id } })">题目</el-button>
              <el-button link type="primary" @click="openChapter(row as Chapter)">编辑</el-button>
              <el-button link type="danger" @click="removeChapter(row as Chapter)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-row>

    <el-dialog v-model="courseDialog" :title="courseEditingId ? '编辑科目' : '新建科目'" width="420px">
      <el-form ref="courseFormRef" :model="courseForm" label-width="70px">
        <el-form-item label="名称" prop="name" :rules="{ required: true, message: '请输入名称' }">
          <el-input v-model="courseForm.name" maxlength="64" placeholder="如：马原" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="courseForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="courseForm.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="courseDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCourse">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chapterDialog" :title="`${chapterEditingId ? '编辑' : '新建'}${chapterLabel}`" width="460px">
      <el-form ref="chapterFormRef" :model="chapterForm" label-width="90px">
        <el-form-item v-if="isCourseBank" label="所属科目">
          <el-select v-model="chapterForm.courseId">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="name" :rules="{ required: true, message: '请输入名称' }">
          <el-input v-model="chapterForm.name" maxlength="128" :placeholder="isCourseBank ? '如：第一章 世界的物质性' : '如：模拟卷（一）'" />
        </el-form-item>
        <template v-if="!isCourseBank">
          <el-form-item label="考试时长">
            <el-input-number v-model="chapterForm.timeLimit" :min="1" /> <span style="margin-left: 8px">分钟</span>
          </el-form-item>
          <el-form-item label="总分">
            <el-input-number v-model="chapterForm.totalScore" :min="1" />
          </el-form-item>
        </template>
        <el-form-item label="排序">
          <el-input-number v-model="chapterForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="chapterForm.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chapterDialog = false">取消</el-button>
        <el-button type="primary" @click="saveChapter">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.course-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
}
.course-item:hover {
  background: #f5f7fa;
}
.course-item.active {
  background: #fff8d6;
  font-weight: 500;
}
</style>
