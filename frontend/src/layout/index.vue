<template>
  <div class="layout">
    <!-- 移动端：侧栏改为抽屉，点遮罩或选中菜单后收起 -->
    <div v-if="mobileNav" class="nav-mask" @click="mobileNav = false"></div>
    <aside class="sidebar" :class="{ open: mobileNav }">
      <div class="brand">
        <div class="brand-text">
          <div class="t1">案件管理中心</div>
          <div class="t2">LEGAL CASE SYSTEM</div>
        </div>
      </div>

      <nav class="nav">
        <template v-for="group in navGroups" :key="group.title">
          <div v-if="group.items.length" class="nav-group">{{ group.title }}</div>
          <button
            v-for="item in group.items"
            :key="item.menuCode"
            class="nav-item"
            :class="{ on: isActive(item) }"
            @click="go(item)"
          >
            <el-icon class="ni"><component :is="iconOf(item)" /></el-icon>
            <span>{{ item.menuName }}</span>
            <span v-if="item.menuCode === 'P-CASE-01' && overdueCount" class="nav-badge">
              {{ overdueCount }}
            </span>
          </button>
        </template>
      </nav>
    </aside>

    <div class="main">
      <header class="topbar">
        <button class="nav-toggle" type="button" @click="mobileNav = !mobileNav">
          <el-icon :size="18"><Fold /></el-icon>
        </button>
        <div class="crumb">
          <span class="sys">{{ crumbGroup }}</span>
          <span class="sep">/</span>
          <b>{{ route.meta.title || '' }}</b>
        </div>

        <div class="search">
          <el-icon class="s-icon"><Search /></el-icon>
          <input
            v-model="keyword"
            placeholder="搜索案号 / 当事人 / 承办人"
            @keyup.enter="doSearch"
          />
        </div>

        <el-popover placement="bottom-end" :width="360" trigger="click" v-model:visible="noticeVisible">
          <template #reference>
            <div class="icon-btn" @click="loadNotifications">
              <el-badge :value="unread" :hidden="!unread" :max="99">
                <el-icon :size="17"><Bell /></el-icon>
              </el-badge>
            </div>
          </template>
          <div class="notice-head">
            <span>站内消息</span>
            <el-button link type="primary" size="small" @click="readAll">全部已读</el-button>
          </div>
          <div class="notice-list">
            <div v-if="!notifications.length" class="empty">暂无消息</div>
            <div
              v-for="item in notifications"
              :key="item.id"
              class="notice-item"
              :class="{ unread: item.readFlag === 0 }"
              @click="readOne(item)"
            >
              <div class="nt">{{ item.title }}</div>
              <div class="nc">{{ item.content }}</div>
              <div class="nw">{{ item.createTime }}</div>
            </div>
          </div>
        </el-popover>

        <el-dropdown trigger="click" placement="bottom-end" @command="onCommand">
          <div class="user-chip" :title="`${user?.userName || ''}（${user?.userId || ''}）`">
            <div class="uc-avatar">{{ avatarText }}</div>
            <div class="uc-text">
              <div class="uc-name">{{ user?.userName }}</div>
              <div class="uc-role">{{ subText }}</div>
            </div>
            <el-icon class="uc-arrow"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <div class="uc-head">
                <div class="uc-head-name">{{ user?.userName }}</div>
                <div class="uc-head-sub">账号 {{ user?.userId }} · {{ scopeText }}数据</div>
              </div>
              <el-dropdown-item command="mobile" divided>移动端待办</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <div class="content">
        <router-view v-slot="{ Component }">
          <!-- 按路径重挂载：归档卷宗详情与案件详情复用同一组件，避免切换后沿用旧案件数据 -->
          <component :is="Component" :key="route.path" />
        </router-view>
      </div>
    </div>

  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Bell, ArrowDown, Fold } from '@element-plus/icons-vue'
import * as ElIcons from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import { collabApi, caseApi } from '../api/modules'

/* 后端菜单里存在 Element Plus 未提供的图标名（如劳动仲裁配置的 Scale），
   统一映射并兜底，避免渲染成空图标只剩缩进 */
const ICON_ALIAS = { Scale: 'Stamp' }

function iconOf(menu) {
  const raw = menu.icon || 'Document'
  const name = ICON_ALIAS[raw] || raw
  return ElIcons[name] ? name : 'Document'
}

/* 菜单分组：与原型一致的三组结构，未登记的菜单归入「支撑」 */
const GROUP_OF = {
  DASHBOARD: '工作台',
  'P-CASE-01': '工作台',
  CASE_INJURY: '业务办理',
  CASE_ARBITRATION: '业务办理',
  INJURY_LEDGER: '业务办理',
  CASE_RELATION: '业务办理',
  'P-TODO-01': '业务办理',
  'P-DEADLINE-01': '业务办理',
  'P-REPORT-01': '支撑',
  ARCHIVE_LIB: '支撑',
  RECYCLE_BIN: '支撑',
  'P-SYS-04': '支撑',
  'P-SYS-02': '支撑',
  'P-SYS-01': '支撑',
  'P-SYS-03': '支撑',
  'P-MOBILE-01': '支撑'
}
const GROUP_ORDER = ['工作台', '业务办理', '支撑', '其他']

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const user = computed(() => store.user)
const notifications = ref([])
const unread = ref(0)
const noticeVisible = ref(false)
const overdueCount = ref(0)

const keyword = ref('')
/** 移动端侧栏抽屉开关，宽屏下不生效 */
const mobileNav = ref(false)

const visibleMenus = computed(() =>
  (store.menus || []).filter((m) => m.path && !m.path.includes(':') && !m.path.startsWith('/m/'))
)

const navGroups = computed(() => {
  const groups = GROUP_ORDER.map((title) => ({ title, items: [] }))
  for (const menu of visibleMenus.value) {
    const title = GROUP_OF[menu.menuCode] || '其他'
    const group = groups.find((g) => g.title === title) || groups[groups.length - 1]
    group.items.push(menu)
  }
  return groups.filter((g) => g.items.length)
})

const crumbGroup = computed(() => {
  const menu = visibleMenus.value.find((m) => isActive(m))
  return menu ? GROUP_OF[menu.menuCode] || '案件管理' : '案件管理'
})

const avatarText = computed(() => (user.value?.userName || '?').slice(0, 1))

const roleText = computed(() => {
  const map = {
    ROLE_SYS_ADMIN: '系统管理员',
    ROLE_CASE_ADMIN: '案件管理员',
    ROLE_DEPT_HEAD: '部门负责人',
    ROLE_HANDLER: '承办人',
    ROLE_CO_HANDLER: '协办人',
    ROLE_COUNTERSIGN: '会签人',
    ROLE_READER: '阅知人',
    ROLE_AUDITOR: '审计员'
  }
  return (store.roles || []).map((r) => map[r] || r).join('、') || '-'
})

/** 副标题：角色名与用户名重复时（如 admin 用户名就是「系统管理员」）改显账号，避免两行同字 */
const subText = computed(() => {
  const role = roleText.value
  const name = user.value?.userName || ''
  return role && role !== name ? role : `账号 ${user.value?.userId || '-'}`
})

const scopeText = computed(() => {
  const scope = user.value?.dataScope
  if (scope === 'ALL') return '全部'
  if (scope === 'DEPT') return '本部门'
  return '本人'
})

/** 详情页从哪个页面点进来：列表页跳转时写进 query.from，用于返回与侧栏高亮 */
const originPath = computed(() => {
  const from = route.query.from
  return typeof from === 'string' && from.startsWith('/') ? from.split('?')[0] : ''
})

/** 来源列表的类型筛选（工伤 / 仲裁），区分「全部案件」与两个分类型菜单 */
const originType = computed(() => {
  const from = route.query.from
  if (typeof from !== 'string' || !from.startsWith('/')) return ''
  return new URLSearchParams(from.split('?')[1] || '').get('type') || ''
})

/** 菜单高亮：类型筛选菜单按 query.type 区分，详情页按来源列表停留在原模块 */
function isActive(menu) {
  const [path, search] = menu.path.split('?')
  const type = new URLSearchParams(search || '').get('type')
  const onCaseDetail = route.path.startsWith('/cases/') && route.path !== '/cases/create'

  if (path === '/cases') {
    // 先确认当前确实在案件相关路由，否则所有 /cases 菜单都会命中
    if (route.path !== '/cases' && !onCaseDetail) return false
    if (onCaseDetail) {
      // 来自台账 / 待办等其他模块时，案件菜单都不亮
      if (!originPath.value) return !type
      if (originPath.value !== '/cases') return false
      return originType.value ? type === originType.value : !type
    }
    if (type) return route.query.type === type
    return !route.query.type
  }

  // 案件详情页：停留在来源模块（台账、待办、关联案件、期限…）
  if (onCaseDetail) return originPath.value === path

  // 归档库包含卷宗详情子路由 /archive/:id
  return route.path === path || route.path.startsWith(path + '/')
}

function go(menu) {
  router.push(menu.path)
  mobileNav.value = false
}

function doSearch() {
  const kw = keyword.value.trim()
  router.push({ path: '/cases', query: kw ? { keyword: kw } : {} })
}

async function loadNotifications() {
  try {
    const data = await collabApi.notifications(20)
    notifications.value = data.list || []
    unread.value = data.unread || 0
  } catch (e) {
    /* 通知失败不打断主流程 */
  }
}

async function readOne(item) {
  if (item.readFlag === 0) {
    await collabApi.readNotification(item.id)
    await loadNotifications()
  }
}

async function readAll() {
  await collabApi.readAllNotifications()
  await loadNotifications()
}

async function loadOverdue() {
  try {
    // 复用列表接口的轻量统计，避免新增专用接口
    const data = await caseApi.page({ pageNum: 1, pageSize: 1, overdue: true })
    overdueCount.value = data.total || 0
  } catch (e) {
    overdueCount.value = 0
  }
}

function onCommand(command) {
  if (command === 'logout') {
    store.logout()
    ElMessage.success('已退出登录')
    router.replace('/login')
  } else if (command === 'mobile') {
    window.open('/m/todos', '_blank')
  }
}

onMounted(() => {
  loadNotifications()
  loadOverdue()
})
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 228px;
  flex: 0 0 228px;
  background: var(--gsms-sidebar);
  border-right: 1px solid var(--gsms-border);
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px 16px;
}

.brand-text .t1 {
  color: var(--gsms-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.25;
}

.brand-text .t2 {
  color: var(--gsms-text-3);
  font-size: 11px;
  letter-spacing: 0.4px;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 6px 10px;
}

.nav-group {
  margin: 14px 0 6px;
  padding: 0 8px;
  font-size: 11px;
  color: var(--gsms-text-3);
  letter-spacing: 1px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  border: 0;
  background: transparent;
  border-radius: 8px;
  color: var(--gsms-text-sub);
  font-size: 13.5px;
  font-family: inherit;
  cursor: pointer;
  transition: 0.15s;
  margin-bottom: 2px;
}

.nav-item .ni {
  font-size: 15px;
  opacity: 0.9;
}

.nav-item:hover {
  background: var(--gsms-sidebar-hover);
  color: var(--gsms-text);
}

.nav-item.on {
  background: var(--gsms-primary);
  color: #fff;
  font-weight: 600;
}

.nav-item.on .ni {
  opacity: 1;
}

.nav-badge {
  margin-left: auto;
  font-size: 11px;
  background: rgba(31, 79, 216, 0.1);
  color: var(--gsms-primary);
  padding: 0 6px;
  border-radius: 20px;
  font-weight: 600;
}

/* 顶栏用户信息（原侧栏底部账号区上移） */
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border-radius: 9px;
  cursor: pointer;
  outline: none;
  transition: background 0.15s;
}

.user-chip:hover {
  background: #f2f5fb;
}

.uc-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(140deg, #2a5ae0, #17359c);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 11.5px;
  font-weight: 700;
  flex: 0 0 28px;
}

.uc-text {
  line-height: 1.25;
}

.uc-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--gsms-text);
}

.uc-role {
  font-size: 11px;
  color: var(--gsms-text-3);
  max-width: 148px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.uc-arrow {
  font-size: 12px;
  color: var(--gsms-text-3);
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: 56px;
  flex: 0 0 56px;
  background: #fff;
  border-bottom: 1px solid var(--gsms-border);
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 22px;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--gsms-text-3);
}

.crumb .sys {
  color: var(--gsms-text-3);
}

.crumb b {
  color: var(--gsms-text);
  font-weight: 600;
}

.search {
  margin-left: auto;
  position: relative;
}

.search input {
  width: 250px;
  height: 32px;
  border: 1px solid var(--gsms-border);
  background: #f8fafd;
  border-radius: 8px;
  padding: 0 10px 0 32px;
  font-size: 12.5px;
  color: var(--gsms-text);
  outline: none;
  font-family: inherit;
}

.search input:focus {
  border-color: #b9c8ee;
  background: #fff;
  box-shadow: 0 0 0 3px var(--gsms-primary-soft);
}

.search .s-icon {
  position: absolute;
  left: 10px;
  top: 8px;
  font-size: 14px;
  color: var(--gsms-text-3);
}

.icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--gsms-border);
  background: #fff;
  display: grid;
  place-items: center;
  color: var(--gsms-text-sub);
  cursor: pointer;
}

.icon-btn:hover {
  background: var(--gsms-line-2);
}

.content {
  flex: 1;
  overflow-y: auto;
  background: var(--gsms-bg);
}

/* ---------------- 移动端适配 ---------------- */
.nav-toggle {
  display: none;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--gsms-border);
  border-radius: 8px;
  background: #fff;
  color: var(--gsms-text-2);
  cursor: pointer;
}

.nav-mask {
  position: fixed;
  inset: 0;
  background: rgba(16, 24, 40, 0.35);
  z-index: 1000;
}

@media (max-width: 992px) {
  .nav-toggle {
    display: flex;
  }

  /* 侧栏改为抽屉：默认移出屏幕，展开时滑入并压住内容 */
  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: 1001;
    transform: translateX(-100%);
    transition: transform 0.22s ease;
    box-shadow: 0 10px 30px rgba(16, 24, 40, 0.14);
  }

  .sidebar.open {
    transform: translateX(0);
  }

  .topbar {
    padding: 0 12px;
    gap: 10px;
  }

  .topbar .search {
    display: none;
  }

  .crumb .sys,
  .crumb .sep {
    display: none;
  }

  .user-chip .uc-text {
    display: none;
  }

  .content {
    padding: 10px 10px 16px;
    /* 栅格负 margin 等零星溢出不再产生页面级横向滚动条，表格自身的横向滚动不受影响 */
    overflow-x: hidden;
  }
}



.notice-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--gsms-border);
  font-weight: 600;
}

.notice-list {
  max-height: 360px;
  overflow-y: auto;
}

.notice-item {
  padding: 10px 6px;
  border-bottom: 1px dashed var(--gsms-line-2);
  cursor: pointer;
}

.notice-item:hover {
  background: #f7f9fd;
}

.notice-item .nt {
  font-size: 13px;
  font-weight: 600;
}

.notice-item.unread .nt::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--gsms-danger);
  margin-right: 6px;
  vertical-align: middle;
}

.notice-item .nc {
  font-size: 12px;
  color: var(--gsms-text-sub);
  margin: 4px 0;
  line-height: 1.5;
}

.notice-item .nw {
  font-size: 11px;
  color: #9aa8bd;
}
</style>
