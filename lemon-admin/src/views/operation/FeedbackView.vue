<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '@/api/operation'

const router = useRouter()
const query = reactive({ pageNum: 1, pageSize: 20, type: undefined as number | undefined, status: 0 as number | undefined })
const list = ref<api.Feedback[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.pageFeedback(query)
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

onMounted(load)

// ---- 处理 ----
const dialogVisible = ref(false)
const current = ref<api.Feedback | null>(null)
const form = reactive({ reply: '', status: 1 })

function openHandle(row: api.Feedback) {
  current.value = row
  Object.assign(form, { reply: row.reply ?? '', status: 1 })
  dialogVisible.value = true
}

async function save() {
  await api.handleFeedback(current.value!.id, { reply: form.reply.trim() || undefined, status: form.status })
  ElMessage.success('已保存')
  dialogVisible.value = false
  load()
}

/** 纠错：跳到题目管理并打开该题编辑 */
function editQuestion(row: api.Feedback) {
  router.push({ name: 'questions', query: { questionId: row.questionId } })
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="query.status" @change="search">
        <el-radio-button :value="0">待处理</el-radio-button>
        <el-radio-button :value="1">已处理</el-radio-button>
        <el-radio-button :value="undefined">全部</el-radio-button>
      </el-radio-group>
      <el-select v-model="query.type" placeholder="全部类型" clearable style="width: 130px" @change="search">
        <el-option label="题目纠错" :value="1" />
        <el-option label="意见建议" :value="2" />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="(row as api.Feedback).type === 1 ? 'danger' : 'info'" size="small">
            {{ (row as api.Feedback).type === 1 ? '题目纠错' : '意见建议' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内容" min-width="260">
        <template #default="{ row }">
          <div v-if="(row as api.Feedback).stemText" class="stem">
            题目 #{{ (row as api.Feedback).questionId }}：{{ (row as api.Feedback).stemText }}
          </div>
          <div>{{ (row as api.Feedback).content }}</div>
          <div v-if="(row as api.Feedback).reply" class="reply">回复：{{ (row as api.Feedback).reply }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="user" label="提交人" width="150" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="提交时间" width="150" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as api.Feedback).status === 1 ? 'success' : 'warning'" size="small">
            {{ (row as api.Feedback).status === 1 ? '已处理' : '待处理' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-if="(row as api.Feedback).questionId" link type="primary" @click="editQuestion(row as api.Feedback)">改题</el-button>
          <el-button link type="primary" @click="openHandle(row as api.Feedback)">处理</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="load"
    />

    <el-dialog v-model="dialogVisible" title="处理反馈" width="520px">
      <div v-if="current" class="detail">
        <div v-if="current.stemText" class="stem">题目 #{{ current.questionId }}：{{ current.stemText }}</div>
        <div>{{ current.content }}</div>
      </div>
      <el-form label-width="70px">
        <el-form-item label="回复">
          <el-input v-model="form.reply" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="选填，用户在“我的反馈”里可以看到" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">已处理</el-radio>
            <el-radio :value="0">待处理</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stem {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-bottom: 4px;
}
.reply {
  margin-top: 6px;
  color: var(--el-color-success);
  font-size: 13px;
}
.detail {
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 16px;
  line-height: 1.6;
}
</style>
