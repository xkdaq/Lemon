<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter, type RouteRecordRaw } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { menuRoutes } from '@/router'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 按 meta.group 分组，过滤掉隐藏和无权限的菜单 */
const menuGroups = computed(() => {
  const groups: { title: string; items: RouteRecordRaw[] }[] = []
  for (const r of menuRoutes) {
    if (r.meta?.hidden || !userStore.canAccess(r.meta?.roles)) continue
    const title = r.meta?.group ?? ''
    let g = groups.find((x) => x.title === title)
    if (!g) groups.push((g = { title, items: [] }))
    g.items.push(r)
  }
  return groups
})
const activeMenu = computed(() =>
  route.name === 'bankDetail' ? '/content/banks' : route.name === 'punchDetail' ? '/operation/punch' : route.path,
)
const roleText = computed(() => (userStore.isSuperAdmin ? '超级管理员' : '录题员'))

async function handleLogout() {
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  await userStore.logout()
  router.replace({ name: 'login' })
}

// ---- 修改密码 ----
const pwdVisible = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码' }],
  newPassword: [{ required: true, min: 6, max: 32, message: '6-32 位' }],
  confirm: [{
    validator: (_r, v, cb) => (v === pwdForm.newPassword ? cb() : cb(new Error('两次输入不一致'))),
  }],
}

function openPwd() {
  Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirm: '' })
  pwdVisible.value = true
}

async function savePwd() {
  await pwdFormRef.value?.validate()
  await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
  ElMessage.success('密码已修改')
  pwdVisible.value = false
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">🍋 Lemon 刷题</div>
      <el-menu :default-active="activeMenu" router background-color="#1f2d3d" text-color="#bfcbd9" active-text-color="#ffd04b">
        <template v-for="g in menuGroups" :key="g.title">
          <div v-if="g.title" class="menu-group">{{ g.title }}</div>
          <el-menu-item v-for="m in g.items" :key="m.path" :index="`/${m.path}`">
            <el-icon><component :is="m.meta?.icon" /></el-icon>
            <span>{{ m.meta?.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ route.meta.title }}</span>
        <el-dropdown>
          <span class="user">
            {{ userStore.info?.nickname ?? '管理员' }}
            <el-tag size="small" type="warning" effect="plain">{{ roleText }}</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="openPwd">修改密码</el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>

    <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password maxlength="32" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="pwdForm.confirm" type="password" show-password maxlength="32" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="savePwd">保存</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: #1f2d3d;
  overflow-y: auto;
}
.aside .el-menu {
  border-right: none;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}
.menu-group {
  padding: 14px 20px 6px;
  color: #6b7a8c;
  font-size: 12px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
}
.title {
  font-size: 16px;
  font-weight: 500;
}
.user {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
</style>
