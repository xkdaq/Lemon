<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api/operation'

/** 用户协议、隐私政策：纯文本，按换行分段；用户端注册页和「设置」里展示，保存后立即生效 */
type Doc = api.AppDocument & { draftTitle: string; draft: string }
const docs = ref<Doc[]>([])
const tab = ref('agreement')
const saving = ref(false)

async function load() {
  docs.value = (await api.listDocuments()).map((d) => ({ ...d, draftTitle: d.title, draft: d.content }))
}
onMounted(load)

async function save(d: Doc) {
  saving.value = true
  try {
    await api.updateDocument(d.docKey, d.draftTitle, d.draft)
    ElMessage.success('已保存')
    await load()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="doc-card">
    <div class="title">用户协议与隐私政策</div>
    <el-tabs v-model="tab">
      <el-tab-pane v-for="d in docs" :key="d.docKey" :name="d.docKey" :label="d.title">
        <el-form label-width="60px">
          <el-form-item label="标题">
            <el-input v-model="d.draftTitle" maxlength="64" style="width: 320px" />
          </el-form-item>
          <el-form-item label="内容">
            <el-input v-model="d.draft" type="textarea" :autosize="{ minRows: 12, maxRows: 30 }" maxlength="50000" show-word-limit />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" :disabled="d.draft === d.content && d.draftTitle === d.title" @click="save(d)">保存</el-button>
            <span class="sub">最近修改：{{ d.updatedAt }}</span>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.doc-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px 20px;
  margin-top: 16px;
}
.title {
  font-weight: 600;
  margin-bottom: 4px;
}
.sub {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
