<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">我的待办</h2>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="stat-grid">
      <div class="stat-card accent">
        <div class="label">待办任务</div>
        <div class="value">{{ summary.taskCount || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="label">待会签</div>
        <div class="value">{{ summary.countersignCount || 0 }}</div>
      </div>
      <div class="stat-card warn">
        <div class="label">期限提醒</div>
        <div class="value">{{ summary.deadlineCount || 0 }}</div>
      </div>
      <div class="stat-card danger">
        <div class="label">超期合计</div>
        <div class="value">{{ summary.overdueCount || 0 }}</div>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-radio-group v-model="filterType" size="default">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="TASK">待办理</el-radio-button>
          <el-radio-button label="COUNTERSIGN">待会签</el-radio-button>
          <el-radio-button label="DEADLINE">期限</el-radio-button>
        </el-radio-group>
        <div class="spacer"></div>
        <span class="text-sub" style="font-size: 12px">共 {{ filtered.length }} 条</span>
      </div>

      <el-table
        v-loading="loading"
        :data="filtered"
        :row-class-name="rowClass"
        :row-style="{ cursor: 'pointer' }"
        @row-dblclick="handle"
      >
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.type)" effect="plain">{{ row.typeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="待办事项" min-width="200" show-overflow-tooltip />
        <el-table-column label="关联案件" min-width="240">
          <template #default="{ row }">
            <el-link v-if="row.caseId" type="primary" @click="handle(row)">
              {{ row.caseNo }} · {{ row.caseName }}
            </el-link>
            <span v-else class="text-sub">-</span>
          </template>
        </el-table-column>
        <el-table-column label="截止" width="170">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.overdue }">
              {{ row.deadline || row.deadlineDate || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.overdue" size="small" type="danger">超期 {{ row.overdueDays }} 天</el-tag>
            <el-tag v-else size="small" type="warning">待处理</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handle(row)">去处理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { collabApi } from '../api/modules'
import { useCaseNav } from '../composables/useCaseNav'

/** 进案件详情时带上来源，返回时仍回到待办 */
const { openCaseDetail } = useCaseNav()
const loading = ref(false)
const summary = ref({})
const filterType = ref('')

const filtered = computed(() => {
  const list = summary.value.list || []
  return filterType.value ? list.filter((i) => i.type === filterType.value) : list
})

function typeTag(type) {
  if (type === 'TASK') return 'primary'
  if (type === 'COUNTERSIGN') return 'warning'
  return 'danger'
}

function rowClass({ row }) {
  return row.overdue ? 'row-overdue' : ''
}

function handle(row) {
  if (row.caseId) {
    openCaseDetail(row.caseId, '/todos')
  }
}

async function load() {
  loading.value = true
  try {
    summary.value = await collabApi.todos()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
