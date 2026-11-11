import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/user'
import AdminLayout from '@/layout/AdminLayout.vue'

/**
 * meta.title 菜单与标题；meta.icon Element Plus 图标名；meta.group 菜单分组；
 * meta.hidden 不出现在菜单；meta.roles 允许访问的角色（不填表示所有管理员）。
 * 角色只控制前端显示，真正的权限由后端按路由校验。
 */
const SUPER = ['super_admin']

export const menuRoutes: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: { title: '工作台', icon: 'Odometer' },
  },
  {
    path: 'content/modules',
    name: 'modules',
    component: () => import('@/views/content/ModuleView.vue'),
    meta: { title: '模块管理', icon: 'Grid', group: '内容' },
  },
  {
    path: 'content/banks',
    name: 'banks',
    component: () => import('@/views/content/BankView.vue'),
    meta: { title: '题库管理', icon: 'Collection', group: '内容' },
  },
  {
    path: 'content/banks/:id',
    name: 'bankDetail',
    component: () => import('@/views/content/BankDetailView.vue'),
    meta: { title: '题库', hidden: true },
  },
  {
    path: 'content/paper-rules',
    name: 'paperRules',
    component: () => import('@/views/content/PaperRuleView.vue'),
    meta: { title: '组卷规则', icon: 'Tickets', group: '内容' },
  },
  {
    path: 'content/questions',
    name: 'questions',
    component: () => import('@/views/content/QuestionView.vue'),
    meta: { title: '题目搜索', icon: 'Search', group: '内容' },
  },
  {
    path: 'operation/punch',
    name: 'punch',
    component: () => import('@/views/operation/PunchView.vue'),
    meta: { title: '打卡活动', icon: 'Calendar', group: '运营' },
  },
  {
    path: 'operation/punch/:id',
    name: 'punchDetail',
    component: () => import('@/views/operation/PunchDetailView.vue'),
    meta: { title: '打卡活动', hidden: true },
  },
  {
    path: 'operation/users',
    name: 'appUsers',
    component: () => import('@/views/operation/UserView.vue'),
    meta: { title: '用户管理', icon: 'User', group: '运营', roles: SUPER },
  },
  {
    path: 'operation/activation-codes',
    name: 'activationCodes',
    component: () => import('@/views/operation/ActivationCodeView.vue'),
    meta: { title: '激活码', icon: 'Key', group: '运营', roles: SUPER },
  },
  {
    path: 'operation/sms-logs',
    name: 'smsLogs',
    component: () => import('@/views/operation/SmsLogView.vue'),
    meta: { title: '短信记录', icon: 'Message', group: '运营', roles: SUPER },
  },
  {
    path: 'operation/feedback',
    name: 'feedback',
    component: () => import('@/views/operation/FeedbackView.vue'),
    meta: { title: '反馈处理', icon: 'ChatLineSquare', group: '运营' },
  },
  {
    path: 'operation/banners',
    name: 'banners',
    component: () => import('@/views/operation/BannerView.vue'),
    meta: { title: 'Banner', icon: 'Picture', group: '运营', roles: SUPER },
  },
  {
    path: 'operation/notices',
    name: 'notices',
    component: () => import('@/views/operation/NoticeView.vue'),
    meta: { title: '公告管理', icon: 'Bell', group: '运营', roles: SUPER },
  },
  {
    path: 'operation/configs',
    name: 'configs',
    component: () => import('@/views/operation/ConfigView.vue'),
    meta: { title: '系统参数', icon: 'Setting', group: '运营', roles: SUPER },
  },
  {
    path: 'system/admins',
    name: 'admins',
    component: () => import('@/views/system/AdminView.vue'),
    meta: { title: '管理员', icon: 'Avatar', group: '系统', roles: SUPER },
  },
]

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: AdminLayout,
    redirect: '/dashboard',
    children: menuRoutes,
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  document.title = `${to.meta.title ?? ''} - Lemon 刷题管理系统`
  if (to.meta.public) {
    return
  }
  if (!getToken()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  const userStore = useUserStore()
  if (!userStore.info) {
    try {
      await userStore.fetchInfo()
    } catch {
      // token 失效：回到登录页，登录后回到本来要去的页面
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  if (!userStore.canAccess(to.meta.roles)) {
    return { name: 'dashboard' }
  }
})

export default router
