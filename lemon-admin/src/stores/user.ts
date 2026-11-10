import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { getToken, removeToken, setToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const info = ref<authApi.AdminInfo | null>(null)

  /** 超级管理员：可改模块、管理账号；录题员只能管题库和题目 */
  const isSuperAdmin = computed(() => info.value?.roles.includes('super_admin') ?? false)

  /** 路由 meta.roles 为空表示所有管理员可访问 */
  function canAccess(roles?: string[]) {
    return !roles?.length || roles.some((r) => info.value?.roles.includes(r))
  }

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    token.value = res.token
    setToken(res.token)
  }

  async function fetchInfo() {
    info.value = await authApi.getInfo()
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      token.value = null
      info.value = null
      removeToken()
    }
  }

  return { token, info, isSuperAdmin, canAccess, login, fetchInfo, logout }
})
