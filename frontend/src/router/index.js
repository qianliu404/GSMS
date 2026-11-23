import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/m/todos',
    name: 'mobile-todo',
    component: () => import('../views/MobileTodo.vue'),
    meta: { title: '移动待办' }
  },
  {
    path: '/',
    component: () => import('../layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '流程总览' }
      },
      {
        path: 'cases',
        name: 'case-list',
        component: () => import('../views/CaseList.vue'),
        meta: { title: '案件列表' }
      },
      {
        path: 'cases/create',
        name: 'case-create',
        component: () => import('../views/CaseCreate.vue'),
        meta: { title: '案件新建' }
      },
      {
        path: 'cases/:id',
        name: 'case-detail',
        component: () => import('../views/CaseDetail.vue'),
        meta: { title: '案件详情' }
      },
      {
        path: 'relations',
        name: 'relations',
        component: () => import('../views/Relations.vue'),
        meta: { title: '争议关联' }
      },
      {
        path: 'ledger',
        name: 'ledger',
        component: () => import('../views/Ledger.vue'),
        meta: { title: '台账' }
      },
      {
        path: 'archive',
        name: 'archive',
        component: () => import('../views/Archive.vue'),
        meta: { title: '归档库' }
      },
      {
        // 归档卷宗详情：复用案件详情页，停留在归档库模块（只读查看）
        path: 'archive/:id',
        name: 'archive-detail',
        component: () => import('../views/CaseDetail.vue'),
        meta: { title: '卷宗详情' }
      },
      {
        path: 'todos',
        name: 'todo',
        component: () => import('../views/Todo.vue'),
        meta: { title: '我的待办' }
      },
      {
        path: 'deadlines',
        name: 'deadline',
        component: () => import('../views/Deadline.vue'),
        meta: { title: '期限中心' }
      },
      {
        path: 'reports',
        name: 'report',
        component: () => import('../views/Report.vue'),
        meta: { title: '报表中心' }
      },
      {
        path: 'system/users',
        name: 'system-user',
        component: () => import('../views/SystemUser.vue'),
        meta: { title: '用户与角色' }
      },
      {
        path: 'system/permissions',
        name: 'system-permission',
        component: () => import('../views/SystemPermission.vue'),
        meta: { title: '角色权限' }
      },
      {
        path: 'system/workflow',
        name: 'system-workflow',
        component: () => import('../views/SystemWorkflow.vue'),
        meta: { title: '流程节点配置' }
      },
      {
        path: 'recycle-bin',
        name: 'recycle-bin',
        component: () => import('../views/RecycleBin.vue'),
        meta: { title: '回收站' }
      },
      {
        path: 'system/audit-logs',
        name: 'system-audit',
        component: () => import('../views/SystemAudit.vue'),
        meta: { title: '审计日志' }
      },
      {
        path: 'system/notify',
        name: 'system-notify',
        component: () => import('../views/SystemNotify.vue'),
        meta: { title: '推送设置' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const store = useUserStore()
  document.title = to.meta.title ? `${to.meta.title} - 案件协同管理系统` : '案件协同管理系统'

  if (to.meta.public) {
    return true
  }
  if (!store.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!store.user) {
    try {
      await store.fetchMe()
    } catch (e) {
      store.logout()
      return { path: '/login' }
    }
  }
  return true
})

export default router
