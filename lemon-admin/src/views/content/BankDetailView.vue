<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import * as api from '@/api/content'
import type { Scope } from '@/components/ChapterCascader.vue'
import QuestionTable from './QuestionTable.vue'
import { useContentTree } from '@/composables/useContentTree'
import { BankType, bankTypeLabel, type Chapter, type Course, type QuestionBank } from '@/types/content'

/**
 * 题库工作台：左侧是本题库的目录（科目型：全部 → 科目 → 章节；试卷型：全部 → 试卷），
 * 右侧是所选节点下的题目，录题、导入、导出、批量操作都限定在当前题库。
 * 当前选中的节点记在地址栏 ?node=，刷新和返回时保持。
 */
type NodeKind = 'all' | 'course' | 'chapter'
interface TreeNode {
  key: string
  kind: NodeKind
  label: string
  count: number
  status: number
  course?: Course
  chapter?: Chapter
  children?: TreeNode[]
}

const route = useRoute()
const router = useRouter()
const { reload: reloadTree } = useContentTree()
const bankId = Number(route.params.id)

const bank = ref<QuestionBank>()
const courses = ref<Course[]>([])
const chapters = ref<Chapter[]>([])
const isCourseBank = computed(() => bank.value?.type === BankType.COURSE)
const chapterLabel = computed(() => (isCourseBank.value ? '章节' : '试卷'))

async function load() {
  const [b, cs, chs] = await Promise.all([
    api.getBank(bankId),
    api.listCourses(bankId),
    api.listChapters(bankId),
  ])
  bank.value = b
  courses.value = cs
  chapters.value = chs
  // 选中的节点被删掉了，回到“全部”
  if (!findNode(currentKey.value)) {
    select('all')
  }
}
onMounted(load)

// ==================== 目录树 ====================

const chapterNode = (c: Chapter): TreeNode => ({
  key: `chapter:${c.id}`, kind: 'chapter', label: c.name, count: c.questionCount, status: c.status, chapter: c,
})

const treeData = computed<TreeNode[]>(() => {
  const all: TreeNode = { key: 'all', kind: 'all', label: '全部题目', count: bank.value?.questionCount ?? 0, status: 1 }
  if (!isCourseBank.value) {
    return [all, ...chapters.value.map(chapterNode)]
  }
  return [all, ...courses.value.map((c): TreeNode => {
    const children = chapters.value.filter((ch) => ch.courseId === c.id).map(chapterNode)
    return {
      key: `course:${c.id}`, kind: 'course', label: c.name, status: c.status, course: c, children,
      count: children.reduce((sum, n) => sum + n.count, 0),
    }
  })]
})

function findNode(key: string): TreeNode | undefined {
  for (const n of treeData.value) {
    if (n.key === key) return n
    const child = n.children?.find((c) => c.key === key)
    if (child) return child
  }
  return undefined
}

const currentKey = computed(() => (route.query.node as string) || 'all')
const current = computed(() => findNode(currentKey.value) ?? treeData.value[0])

function select(key: string) {
  router.replace({ query: { ...route.query, node: key === 'all' ? undefined : key } })
}

/** 右侧题目列表的范围 */
const scope = computed<Scope>(() => {
  const n = current.value
  if (n?.kind === 'chapter') return { chapterId: n.chapter!.id }
  if (n?.kind === 'course') return { bankId, courseId: n.course!.id }
  return { bankId }
})

/** 右侧标题：科目 / 章节路径 */
const currentTitle = computed(() => {
  const n = current.value
  if (!n || n.kind === 'all') return '全部题目'
  if (n.kind === 'course') return n.label
  const course = courses.value.find((c) => c.id === n.chapter?.courseId)
  return course ? `${course.name} / ${n.label}` : n.label
})

// ==================== 科目 ====================

const courseDialog = ref(false)
const courseEditingId = ref<number | null>(null)
const courseFormRef = ref<FormInstance>()
const courseForm = reactive<api.CourseSave>({ bankId, name: '', sort: 0, status: 1 })

function openCourse(row?: Course) {
  courseEditingId.value = row?.id ?? null
  Object.assign(courseForm, { bankId, name: row?.name ?? '', sort: row?.sort ?? courses.value.length + 1, status: row?.status ?? 1 })
  courseDialog.value = true
}

async function saveCourse() {
  await courseFormRef.value?.validate()
  let id = courseEditingId.value
  if (id) {
    await api.updateCourse(id, courseForm)
  } else {
    id = await api.createCourse(courseForm)
  }
  ElMessage.success('保存成功')
  courseDialog.value = false
  await load()
  reloadTree()
  select(`course:${id}`)
}

async function removeCourse(c: Course) {
  await ElMessageBox.confirm(`确定删除科目「${c.name}」吗？科目下还有章节时不能删除。`, '提示', { type: 'warning' })
  await api.deleteCourse(c.id)
  ElMessage.success('已删除')
  await load()
  reloadTree()
}

// ==================== 章节 / 试卷 ====================

const chapterDialog = ref(false)
const chapterEditingId = ref<number | null>(null)
const chapterFormRef = ref<FormInstance>()
const chapterForm = reactive<api.ChapterSave>({
  bankId, courseId: null, name: '', sort: 0, status: 1, timeLimit: null, totalScore: null,
})

function openChapter(row?: Chapter, courseId?: number) {
  chapterEditingId.value = row?.id ?? null
  const siblings = chapters.value.filter((c) => c.courseId === (courseId ?? row?.courseId ?? null))
  Object.assign(chapterForm, {
    bankId,
    courseId: isCourseBank.value ? (row?.courseId ?? courseId ?? courses.value[0]?.id ?? null) : null,
    name: row?.name ?? '',
    sort: row?.sort ?? siblings.length + 1,
    status: row?.status ?? 1,
    timeLimit: row?.timeLimit ?? (isCourseBank.value ? null : 180),
    totalScore: row?.totalScore ?? (isCourseBank.value ? null : 100),
  })
  chapterDialog.value = true
}

async function saveChapter() {
  await chapterFormRef.value?.validate()
  let id = chapterEditingId.value
  if (id) {
    await api.updateChapter(id, chapterForm)
  } else {
    id = await api.createChapter(chapterForm)
  }
  ElMessage.success('保存成功')
  chapterDialog.value = false
  await load()
  reloadTree()
  select(`chapter:${id}`)
}

async function removeChapter(c: Chapter) {
  await ElMessageBox.confirm(`确定删除「${c.name}」吗？下面还有题目时不能删除。`, '提示', { type: 'warning' })
  await api.deleteChapter(c.id)
  ElMessage.success('已删除')
  await load()
  reloadTree()
}

function onNodeCommand(node: TreeNode, cmd: string) {
  if (node.kind === 'course') {
    if (cmd === 'add') openChapter(undefined, node.course!.id)
    if (cmd === 'edit') openCourse(node.course)
    if (cmd === 'delete') removeCourse(node.course!)
  } else if (node.kind === 'chapter') {
    if (cmd === 'edit') openChapter(node.chapter)
    if (cmd === 'delete') removeChapter(node.chapter!)
  }
}

/** 题目变动后刷新左侧题量 */
async function onQuestionsChanged() {
  const [b, chs] = await Promise.all([api.getBank(bankId), api.listChapters(bankId)])
  bank.value = b
  chapters.value = chs
}
</script>

<template>
  <div class="workspace">
    <div class="page-card header">
      <el-page-header @back="router.push('/content/banks')">
        <template #content>
          <span class="bank-name">{{ bank?.name }}</span>
          <el-tag v-if="bank" size="small" style="margin-left: 8px">{{ bankTypeLabel[bank.type] }}</el-tag>
          <el-tag v-if="bank?.status === 0" size="small" type="info" style="margin-left: 6px">已下架</el-tag>
        </template>
      </el-page-header>
    </div>

    <div class="body">
      <!-- 左侧目录 -->
      <aside class="page-card side">
        <div class="side-head">
          <strong>{{ isCourseBank ? '科目 / 章节' : '试卷' }}</strong>
          <el-button v-if="isCourseBank" size="small" link type="primary" icon="Plus" @click="openCourse()">科目</el-button>
          <el-button v-else size="small" link type="primary" icon="Plus" @click="openChapter()">试卷</el-button>
        </div>
        <el-tree
          v-if="bank"
          :data="treeData"
          node-key="key"
          :current-node-key="current?.key"
          :props="{ label: 'label', children: 'children' }"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          @node-click="(n: TreeNode) => select(n.key)"
        >
          <template #default="{ data }">
            <div class="node" :class="{ off: (data as TreeNode).status === 0 }">
              <span class="node-label" :title="(data as TreeNode).label">{{ (data as TreeNode).label }}</span>
              <span class="node-count">{{ (data as TreeNode).count }}</span>
              <el-dropdown
                v-if="(data as TreeNode).kind !== 'all'"
                trigger="click"
                @command="(cmd: string) => onNodeCommand(data as TreeNode, cmd)"
                @click.stop
              >
                <el-icon class="node-more" @click.stop><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="(data as TreeNode).kind === 'course'" command="add">新建章节</el-dropdown-item>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-tree>
        <div v-if="bank && treeData.length === 1" class="empty-tip">
          {{ isCourseBank ? '先新建科目（如：马原），再在科目下新建章节' : '先新建一套试卷' }}
        </div>
        <div class="side-foot">数字为上架题数</div>
      </aside>

      <!-- 右侧题目 -->
      <section class="page-card main">
        <div class="main-head">
          <span class="main-title">{{ currentTitle }}</span>
          <template v-if="current?.kind === 'chapter' && !isCourseBank">
            <span class="muted">{{ current.chapter?.timeLimit ?? '-' }} 分钟 · 满分 {{ current.chapter?.totalScore ?? '-' }}</span>
          </template>
          <el-tag v-if="current?.status === 0" size="small" type="info">已下架</el-tag>
          <span class="spacer" />
          <el-button v-if="current?.kind === 'course'" size="small" @click="openChapter(undefined, current.course!.id)">新建章节</el-button>
          <el-button v-if="current?.kind === 'chapter'" size="small" @click="openChapter(current.chapter)">编辑{{ chapterLabel }}</el-button>
        </div>
        <QuestionTable
          v-if="bank"
          :scope="scope"
          :bank-id="bankId"
          location-level="chapter"
          :show-location="current?.kind !== 'chapter'"
          @changed="onQuestionsChanged"
        />
      </section>
    </div>

    <el-dialog v-model="courseDialog" :title="courseEditingId ? '编辑科目' : '新建科目'" width="420px">
      <el-form ref="courseFormRef" :model="courseForm" label-width="70px" @submit.prevent>
        <el-form-item label="名称" prop="name" :rules="{ required: true, message: '请输入名称' }">
          <el-input v-model="courseForm.name" maxlength="64" placeholder="如：马原" @keyup.enter="saveCourse" />
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
      <el-form ref="chapterFormRef" :model="chapterForm" label-width="90px" @submit.prevent>
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
.header {
  padding: 14px 20px;
  margin-bottom: 12px;
}
.bank-name {
  font-weight: 600;
}
.body {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.side {
  width: 260px;
  flex-shrink: 0;
  padding: 12px 8px;
  position: sticky;
  top: 0;
  max-height: calc(100vh - 160px);
  overflow-y: auto;
}
.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 8px;
}
.side-foot {
  padding: 12px 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.empty-tip {
  padding: 12px 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.node {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding-right: 6px;
  font-size: 14px;
}
.node.off .node-label {
  color: var(--el-text-color-placeholder);
  text-decoration: line-through;
}
.node-label {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.node-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.node-more {
  color: var(--el-text-color-secondary);
  padding: 2px;
  border-radius: 4px;
}
.node-more:hover {
  background: var(--el-fill-color);
  color: var(--el-color-primary);
}
.side :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: 6px;
}
.side :deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content) {
  background: #fff8d6;
  font-weight: 500;
}
.main {
  flex: 1;
  min-width: 0;
}
.main-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.main-title {
  font-size: 16px;
  font-weight: 600;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.spacer {
  flex: 1;
}
</style>
