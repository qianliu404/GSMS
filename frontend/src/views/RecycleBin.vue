<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">回收站</h2>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <div class="seg">
          <button :class="{ on: target === 'CASE' }" @click="switchTarget('CASE')">案件</button>
          <button v-if="isAdmin" :class="{ on: target === 'USER' }" @click="switchTarget('USER')">用户</button>
        </div>
        <el-input
          v-model="keyword"
          :placeholder="target === 'CASE' ? '案号 / 案件名称 / 当事人' : '登录名 / 姓名 / 手机号'"
          clearable
          style="width: 250px"
          :prefix-icon="Search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button @click="reload">查询</el-button>
        <div class="spacer"></div>
        <el-button :disabled="!selection.length" @click="batchRestore">批量恢复</el-button>
        <el-button v-if="hasPerm('recycle:delete')" type="danger" plain :disabled="!selection.length" @click="batchPurge">批量删除</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        row-key="id"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column :label="target === 'CASE' ? '案号' : '登录名'" width="150">
          <template #default="{ row }">
            <span class="mono">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="target === 'CASE' ? '案件名称' : '姓名'" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title }}</template>
        </el-table-column>
        <el-table-column :label="target === 'CASE' ? '当事人' : '所属部门'" width="120">
          <template #default="{ row }">{{ row.subtitle || '—' }}</template>
        </el-table-column>
        <el-table-column :label="target === 'CASE' ? '类型' : '岗位'" width="110">
          <template #default="{ row }">
            <span v-if="target === 'CASE'" class="tag" :class="row.tag === 'INJURY' ? 'injury' : (row.tag === 'ARBITRATION' ? 'arb' : 'neutral')">
              {{ typeName(row.tag) }}
            </span>
            <span v-else>{{ row.tag || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="target === 'CASE' ? '原承办人' : '手机号'" width="120">
          <template #default="{ row }">{{ row.ownerName || '—' }}</template>
        </el-table-column>
        <el-table-column label="删除时间" width="170">
          <template #default="{ row }">{{ row.deletedTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="restoreRows([row])">恢复</el-button>
            <el-button v-if="hasPerm('recycle:delete')" link type="danger" size="small" @click="purgeRows([row])">永久删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">回收站是空的</div>
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="reload"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { recycleApi } from '../api/modules'
import { useUserStore } from '../store/user'

const hasPerm = (code) => useUserStore().hasPerm(code)
const store = useUserStore()
const isAdmin = computed(() => (store.roles || []).includes('ROLE_SYS_ADMIN'))

const target = ref('CASE')
const keyword = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const selection = ref([])

const label = computed(() => (target.value === 'CASE' ? '案件' : '账号'))

function typeName(code) {
  if (code === 'INJURY') return '工伤'
  if (code === 'ARBITRATION') return '仲裁'
  return '关联'
}

async function load() {
  loading.value = true
  try {
    const data = await recycleApi.page({
      target: target.value,
      keyword: keyword.value.trim(),
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = data.list || []
    total.value = data.total || 0
    selection.value = []
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  load()
}

function switchTarget(value) {
  target.value = value
  keyword.value = ''
  selection.value = []
  reload()
}

function onSelectionChange(rows) {
  selection.value = rows
}

async function restoreRows(rows) {
  const ids = rows.map((r) => r.id)
  await ElMessageBox.confirm(
    `确认恢复选中的 ${ids.length} 条${label.value}？恢复后将重新出现在业务列表中。`,
    '恢复确认',
    { type: 'info', confirmButtonText: '确认恢复' }
  )
  const count = await recycleApi.restore(target.value, ids)
  ElMessage.success(`已恢复 ${count} 条${label.value}`)
  load()
}

async function purgeRows(rows) {
  const ids = rows.map((r) => r.id)
  await ElMessageBox.confirm(
    `将永久删除 ${ids.length} 条${label.value}及其全部关联数据（${target.value === 'CASE' ? '扩展信息、成员、流转日志、文书、期限、协同与流程记录' : '角色关系'}），数据库中的数据会一并清除，且无法恢复。`,
    '永久删除',
    { type: 'error', confirmButtonText: '确认永久删除', confirmButtonClass: 'el-button--danger' }
  )
  const count = await recycleApi.purge(target.value, ids)
  ElMessage.success(`已永久删除 ${count} 条${label.value}`)
  load()
}

function batchRestore() {
  restoreRows(selection.value)
}

function batchPurge() {
  purgeRows(selection.value)
}

onMounted(load)
</script>

<style scoped>
.mono {
  font-variant-numeric: tabular-nums;
  color: var(--gsms-text-sub);
  font-size: 12.5px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
}
</style>
