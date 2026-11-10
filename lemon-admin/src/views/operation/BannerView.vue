<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import * as api from '@/api/operation'
import { uploadImage } from '@/api/file'

const list = ref<api.Banner[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await api.listBanners()
  } finally {
    loading.value = false
  }
}
onMounted(load)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const uploading = ref(false)
const editForm = ref<api.BannerSave>({ image: '', link: '', sort: 0, status: 1 })

function openDialog(row?: api.Banner) {
  editingId.value = row?.id ?? null
  editForm.value = ({ image: row?.image ?? '', link: row?.link ?? '', sort: row?.sort ?? 0, status: row?.status ?? 1 })
  dialogVisible.value = true
}

async function onPick(file: UploadFile) {
  if (!file.raw) return
  uploading.value = true
  try {
    editForm.value.image = await uploadImage(file.raw)
  } finally {
    uploading.value = false
  }
}

async function save() {
  if (!editForm.value.image) {
    ElMessage.warning('请上传图片')
    return
  }
  const data = { ...editForm.value, link: editForm.value.link?.trim() || null }
  if (editingId.value) {
    await api.updateBanner(editingId.value, data)
  } else {
    await api.createBanner(data)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function toggle(row: api.Banner) {
  await api.updateBanner(row.id, { image: row.image, link: row.link, sort: row.sort, status: row.status === 1 ? 0 : 1 })
  load()
}

async function remove(row: api.Banner) {
  await ElMessageBox.confirm('确定删除这张 Banner 吗？', '提示', { type: 'warning' })
  await api.deleteBanner(row.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="muted">上架的 Banner 按排序显示在用户端首页（最多 5 张），建议尺寸 750×280。</span>
      <span class="spacer" />
      <el-button type="primary" icon="Plus" @click="openDialog()">新建 Banner</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="图片" width="220">
        <template #default="{ row }">
          <el-image :src="(row as api.Banner).image" fit="cover" style="width: 190px; height: 72px; border-radius: 4px" />
        </template>
      </el-table-column>
      <el-table-column label="跳转" min-width="220">
        <template #default="{ row }">{{ (row as api.Banner).link || '不跳转' }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as api.Banner).status === 1 ? 'success' : 'info'" size="small">
            {{ (row as api.Banner).status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row as api.Banner)">编辑</el-button>
          <el-button link type="primary" @click="toggle(row as api.Banner)">{{ (row as api.Banner).status === 1 ? '下架' : '上架' }}</el-button>
          <el-button link type="danger" @click="remove(row as api.Banner)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑 Banner' : '新建 Banner'" width="540px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="图片" required>
          <div class="image-field">
            <el-image v-if="editForm.image" :src="editForm.image" fit="cover" class="preview" />
            <el-upload :auto-upload="false" :show-file-list="false" accept="image/png,image/jpeg,image/webp,image/gif" :on-change="onPick">
              <el-button :loading="uploading" icon="Upload">{{ editForm.image ? '更换图片' : '上传图片' }}</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="跳转">
          <el-input v-model="editForm.link" placeholder="选填：/pages/bank/detail?bankId=1 或 https://..." />
          <div class="tip">站内页面以 /pages/ 开头；https 外链仅网页版可打开，小程序里会复制链接。</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
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
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.image-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.preview {
  width: 375px;
  height: 140px;
  border-radius: 6px;
}
.tip {
  width: 100%;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
