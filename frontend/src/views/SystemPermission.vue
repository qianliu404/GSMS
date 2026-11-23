<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">角色权限</h2>
      </div>
      <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
    </div>

    <div class="card">
      <div class="card-body">
        <div class="perm-tip">
          按「模块 × 操作」勾选角色权限；保存后该角色下所有用户需重新登录才会生效。
          数据范围（全部 / 本部门 / 本人）由用户与角色页面单独设置，与此处互不影响。
        </div>
        <div class="role-bar">
          <span>选择角色</span>
          <el-select v-model="roleId" style="width: 220px" @change="loadRolePerms">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
          <span class="scope">{{ scopeText }}</span>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!roleId"
            style="margin-left: auto"
            @click="save"
          >保存权限</el-button>
          <el-button :icon="Plus" @click="openCreate">新建角色</el-button>
          <el-button
            v-if="canDelete"
            type="danger"
            plain
            :icon="Delete"
            @click="removeRole"
          >删除角色</el-button>
        </div>
      </div>
    </div>

    <div class="card" v-for="group in groups" :key="group.module">
      <div class="card-head">
        <h2>{{ group.moduleName }}</h2>
        <span class="pill">{{ checkedIn(group).length }}/{{ group.items.length }}</span>
        <el-button link type="primary" size="small" style="margin-left: auto" @click="toggleGroup(group, true)">全选</el-button>
        <el-button link size="small" @click="toggleGroup(group, false)">清空</el-button>
      </div>
      <div class="card-body perm-grid">
        <el-checkbox
          v-for="p in group.items"
          :key="p.permCode"
          v-model="checked"
          :value="p.permCode"
          :label="p.permCode"
        >
          {{ p.actionName }}
        </el-checkbox>
      </div>
    </div>

    <!-- 新建自定义角色 -->
    <el-dialog v-model="createDialog" title="新建角色" width="440px">
      <el-form :model="createForm" label-width="96px">
        <el-form-item label="角色名称">
          <el-input v-model="createForm.roleName" maxlength="50" show-word-limit placeholder="如：园区专员" />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="createForm.dataScope" style="width: 100%">
            <el-option label="本人数据" value="SELF" />
            <el-option label="本部门数据" value="DEPT" />
            <el-option label="全部数据" value="ALL" />
          </el-select>
        </el-form-item>
        <el-form-item label="复制权限自">
          <el-select v-model="createForm.copyFromRoleId" clearable placeholder="可选，复制该角色的权限与菜单" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" maxlength="200" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh } from '@element-plus/icons-vue'
import { systemApi } from '../api/modules'

const roles = ref([])
const permissions = ref([])
const roleId = ref(null)
const checked = ref([])
const saving = ref(false)

const currentRole = computed(() => roles.value.find((r) => r.id === roleId.value))
const scopeText = computed(() =>
  currentRole.value?.dataScope === 'ALL' ? '全部数据' : currentRole.value?.dataScope === 'DEPT' ? '本部门数据' : '本人数据'
)
/** 仅系统管理员角色不可删除，其余内置与自定义角色均可删除 */
const canDelete = computed(() => currentRole.value?.roleCode !== 'ROLE_SYS_ADMIN')

const createDialog = ref(false)
const creating = ref(false)
const createForm = reactive({ roleName: '', dataScope: 'SELF', copyFromRoleId: null, remark: '' })

function openCreate() {
  createForm.roleName = ''
  createForm.dataScope = 'SELF'
  createForm.copyFromRoleId = null
  createForm.remark = ''
  createDialog.value = true
}

async function submitCreate() {
  if (!createForm.roleName.trim()) {
    ElMessage.warning('请输入角色名称')
    return
  }
  creating.value = true
  try {
    const role = await systemApi.createRole({ ...createForm, roleName: createForm.roleName.trim() })
    ElMessage.success('角色已创建')
    createDialog.value = false
    roles.value.push(role)
    roleId.value = role.id
    await loadRolePerms()
  } finally {
    creating.value = false
  }
}

async function removeRole() {
  const role = currentRole.value
  try {
    await ElMessageBox.confirm(
      `删除角色「${role.roleName}」将同时清除其权限与菜单授权，并解除该角色下所有用户的关联，此操作不可恢复。`,
      '删除角色',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await systemApi.deleteRole(role.id)
  ElMessage.success('角色已删除')
  roles.value = roles.value.filter((r) => r.id !== role.id)
  roleId.value = roles.value[0]?.id || null
  await loadRolePerms()
}

const groups = computed(() => {
  const map = new Map()
  for (const p of permissions.value) {
    if (!map.has(p.module)) {
      map.set(p.module, { module: p.module, moduleName: p.moduleName, items: [] })
    }
    map.get(p.module).items.push(p)
  }
  return [...map.values()]
})

function checkedIn(group) {
  return group.items.filter((p) => checked.value.includes(p.permCode))
}

function toggleGroup(group, on) {
  const codes = new Set(checked.value)
  for (const p of group.items) {
    if (on) codes.add(p.permCode)
    else codes.delete(p.permCode)
  }
  checked.value = [...codes]
}

async function loadAll() {
  const [roleList, permList] = await Promise.all([systemApi.roles(), systemApi.permissions()])
  roles.value = roleList || []
  permissions.value = permList || []
  if (!roleId.value && roles.value.length) {
    roleId.value = roles.value[0].id
    await loadRolePerms()
  }
}

async function loadRolePerms() {
  if (!roleId.value) return
  checked.value = await systemApi.rolePermissions(roleId.value)
}

async function save() {
  saving.value = true
  try {
    await systemApi.saveRolePermissions(roleId.value, { permCodes: checked.value })
    ElMessage.success('权限已保存，相关用户重新登录后生效')
  } finally {
    saving.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.perm-tip {
  font-size: 12.5px;
  color: var(--gsms-text-sub);
  line-height: 1.7;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--gsms-line-2);
}

.role-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 14px;
  font-size: 12.5px;
}

.scope {
  color: var(--gsms-text-3);
}

.perm-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 28px;
}

.perm-grid :deep(.el-checkbox) {
  margin-right: 0;
  height: auto;
}
</style>
