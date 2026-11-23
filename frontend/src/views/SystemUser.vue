<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">用户与角色</h2>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建用户</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="账号 / 姓名 / 手机号"
          clearable
          class="f-keyword"
          style="width: 220px"
          :prefix-icon="Search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-select
          v-model="query.deptId"
          placeholder="部门"
          clearable
          class="f-adv"
          :class="{ 'f-adv--closed': !moreOpen }"
          style="width: 150px"
          @change="reload"
        >
          <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-select
          v-model="query.status"
          placeholder="状态"
          clearable
          class="f-adv"
          :class="{ 'f-adv--closed': !moreOpen }"
          style="width: 120px"
          @change="reload"
        >
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button class="m-only f-filter" @click="moreOpen = !moreOpen">
          {{ moreOpen ? '收起' : '筛选' }}<em v-if="!moreOpen && advCount">{{ advCount }}</em>
        </el-button>
        <div class="spacer d-only"></div>
        <el-button type="primary" plain class="d-only" @click="reload">查询</el-button>
        <el-button type="primary" class="m-only f-query" @click="reload">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="list" class="d-table">
        <el-table-column prop="userId" label="登录名" width="130">
          <template #default="{ row }">
            <span class="text-mono">{{ row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userName" label="姓名" width="110" />
        <el-table-column prop="deptName" label="部门" width="130" />
        <el-table-column prop="position" label="岗位" width="140" show-overflow-tooltip />
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="name in row.roleNames" :key="name" size="small" effect="plain" style="margin-right: 4px">
              {{ name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机号" width="130" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" width="160" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="resetPwd(row)">重置密码</el-button>
            <el-button
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              size="small"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              :disabled="row.userId === 'admin' || row.userId === currentUserId"
              @click="removeUser(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 窄屏：表格九列放不下，改为卡片，状态与操作一目了然 -->
      <div v-loading="loading" class="m-user">
        <div v-for="row in list" :key="row.userId" class="m-user-card">
          <div class="m-l1">
            <span class="text-mono m-uid">{{ row.userId }}</span>
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </div>

          <div class="m-l2">
            <b>{{ row.userName }}</b>
            <span class="cell-dim">{{ row.position || '—' }}</span>
          </div>

          <div class="m-l3">
            <span class="cell-dim">{{ row.deptName || '—' }}</span>
            <span class="cell-dim">{{ row.mobile || '—' }}</span>
          </div>

          <div class="m-l4">
            <el-tag v-for="name in row.roleNames" :key="name" size="small" effect="plain">{{ name }}</el-tag>
            <span v-if="!row.roleNames || !row.roleNames.length" class="cell-dim">未分配角色</span>
          </div>

          <div class="m-l5">
            <span class="cell-dim">最后登录 {{ row.lastLoginTime || '—' }}</span>
          </div>

          <div class="m-l6">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" @click="resetPwd(row)">重置密码</el-button>
            <el-button size="small" :type="row.status === 1 ? 'danger' : 'success'" plain @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="row.userId === 'admin' || row.userId === currentUserId"
              @click="removeUser(row)"
            >删除</el-button>
          </div>
        </div>
        <div v-if="!loading && !list.length" class="empty">暂无用户</div>
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          :layout="pagerLayout"
          :pager-count="isNarrow ? 5 : 7"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新建用户'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="登录名" prop="userId">
          <el-input v-model="form.userId" :disabled="!!form.id" placeholder="登录用账号，创建后不可修改" />
        </el-form-item>
        <el-form-item label="姓名" prop="userName">
          <el-input v-model="form.userName" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.deptId" placeholder="选择部门" filterable style="width: 100%">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id">
              <div class="dept-opt">
                <span>{{ d.deptName }}</span>
                <el-icon
                  v-if="canManageDept"
                  class="dept-del"
                  title="删除部门"
                  @click.stop.prevent="removeDept(d)"
                >
                  <Delete />
                </el-icon>
              </div>
            </el-option>
            <template v-if="canManageDept" #footer>
              <el-button link type="primary" size="small" :icon="Plus" @click="addDept">
                新增部门
              </el-button>
            </template>
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-input v-model="form.position" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.mobile" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="可多选" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="`${r.roleName}（${scopeText(r.dataScope)}）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="form.id ? '重置密码' : '初始密码'" :prop="form.id ? '' : 'password'">
          <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? '留空表示不修改' : '至少 6 位'" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Delete } from '@element-plus/icons-vue'
import { systemApi } from '../api/modules'
import { useUserStore } from '../store/user'
import { useNarrow } from '../composables/useNarrow'

const store = useUserStore()
const currentUserId = computed(() => store.user?.userId)
const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const depts = ref([])
const roles = ref([])
const dialogVisible = ref(false)
const formRef = ref()

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', deptId: null, status: null })

/** 窄屏：部门/状态收进「筛选」，分页精简 */
const isNarrow = useNarrow()
const moreOpen = ref(false)
const advCount = computed(() => (query.deptId ? 1 : 0) + (query.status === 0 || query.status === 1 ? 1 : 0))
const pagerLayout = computed(() => (isNarrow.value ? 'prev, pager, next' : 'total, sizes, prev, pager, next'))

const form = reactive({
  id: null,
  userId: '',
  userName: '',
  deptId: null,
  mobile: '',
  email: '',
  position: '',
  status: 1,
  password: '',
  roleIds: []
})

const formRules = {
  userId: [{ required: true, message: '请输入登录名', trigger: 'blur' }],
  userName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [
    {
      validator: (rule, value, callback) => {
        if (!form.id && (!value || value.length < 6)) {
          callback(new Error('初始密码至少 6 位'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function scopeText(scope) {
  if (scope === 'ALL') return '全部数据'
  if (scope === 'DEPT') return '本部门'
  return '本人'
}

async function load() {
  loading.value = true
  try {
    const data = await systemApi.users({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  query.pageNum = 1
  load()
}

function openCreate() {
  Object.assign(form, {
    id: null,
    userId: '',
    userName: '',
    deptId: null,
    mobile: '',
    email: '',
    position: '',
    status: 1,
    password: '',
    roleIds: []
  })
  dialogVisible.value = true
}

function openEdit(row) {
  const roleIds = roles.value
    .filter((r) => (row.roleCodes || []).includes(r.roleCode))
    .map((r) => r.id)
  Object.assign(form, {
    id: row.id,
    userId: row.userId,
    userName: row.userName,
    deptId: row.deptId,
    mobile: row.mobile,
    email: row.email,
    position: row.position,
    status: row.status,
    password: '',
    roleIds
  })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...form }
    if (form.id) {
      await systemApi.updateUser(form.id, payload)
      ElMessage.success('已保存')
    } else {
      await systemApi.createUser(payload)
      ElMessage.success('用户已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

/** 删除用户：软删除，后端会拦截当前账号、内置 admin、最后一个管理员与在办案件承办人 */
async function removeUser(row) {
  await ElMessageBox.confirm(
    `确认删除账号「${row.userName}（${row.userId}）」？\n删除为软删除：账号不再出现在列表与人员选择中，历史案件、审计记录仍保留。`,
    '删除用户',
    { type: 'warning', confirmButtonText: '确认删除' }
  )
  await systemApi.deleteUser(row.id)
  ElMessage.success('用户已删除')
  load()
}

async function toggleStatus(row) {
  const target = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(
    `确认${target === 1 ? '启用' : '停用'}账号「${row.userName}」？`,
    '状态变更',
    { type: 'warning' }
  )
  await systemApi.changeStatus(row.id, target)
  ElMessage.success('状态已更新')
  load()
}

async function resetPwd(row) {
  const { value } = await ElMessageBox.prompt(`为「${row.userName}」设置新密码`, '重置密码', {
    inputType: 'password',
    inputValidator: (v) => (v && v.length >= 6 ? true : '密码至少 6 位')
  })
  await systemApi.resetPassword(row.id, value)
  ElMessage.success('密码已重置')
}

/** 仅系统管理员可维护部门字典 */
const canManageDept = computed(() => (store.roles || []).includes('ROLE_SYS_ADMIN'))

async function refreshDepts() {
  depts.value = (await systemApi.depts()) || []
}

/** 新增部门：创建成功后直接选中，省去再翻一次下拉 */
async function addDept() {
  let name = ''
  try {
    const { value } = await ElMessageBox.prompt('请输入部门名称', '新增部门', {
      inputPlaceholder: '如：安全环保部',
      inputValidator: (v) => (v && v.trim() ? true : '部门名称不能为空'),
      confirmButtonText: '创建',
      cancelButtonText: '取消'
    })
    name = value.trim()
  } catch (e) {
    return
  }
  try {
    const id = await systemApi.createDept(name)
    await refreshDepts()
    form.deptId = id
    ElMessage.success('部门已添加')
  } catch (e) {
    /* 拦截器已提示错误 */
  }
}

async function removeDept(dept) {
  try {
    await ElMessageBox.confirm(`确认删除部门「${dept.deptName}」？`, '删除部门', {
      type: 'warning',
      confirmButtonText: '删除'
    })
  } catch (e) {
    return
  }
  try {
    await systemApi.removeDept(dept.id)
    if (form.deptId === dept.id) form.deptId = null
    if (query.deptId === dept.id) query.deptId = null
    await refreshDepts()
    ElMessage.success('部门已删除')
  } catch (e) {
    /* 拦截器已提示错误 */
  }
}

onMounted(async () => {
  load()
  try {
    const [deptList, roleList] = await Promise.all([systemApi.depts(), systemApi.roles()])
    depts.value = deptList || []
    roles.value = roleList || []
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
}

/* 卡片列表只在窄屏显示（须写在媒体查询之前） */
.m-user {
  display: none;
}

@media (max-width: 768px) {
  .toolbar {
    gap: 8px;
    padding: 10px 12px;
  }

  .d-table {
    display: none;
  }

  .m-user {
    display: block;
  }

  .pager {
    justify-content: center;
    padding: 10px 8px;
  }
}

/* ---------------- 用户卡片 ---------------- */
.m-user-card {
  padding: 12px 14px;
  border-bottom: 1px solid var(--gsms-line-2);
}

.m-user-card:last-child {
  border-bottom: 0;
}

.m-l1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.m-uid {
  font-size: 13px;
  font-weight: 600;
}

.m-l2 {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 6px;
  font-size: 15px;
}

.m-l3 {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 5px;
}

.m-l4 {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 7px;
}

.m-l5 {
  margin-top: 6px;
}

.m-l6 {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-top: 10px;
}

.m-l6 .el-button {
  margin-left: 0;
  width: 100%;
}

/* 部门下拉：名称 + 悬浮显示的删除按钮 */
.dept-opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dept-del {
  color: var(--gsms-text-3);
  opacity: 0;
  transition: 0.15s;
}

.dept-opt:hover .dept-del {
  opacity: 1;
  color: var(--gsms-danger);
}
</style>
