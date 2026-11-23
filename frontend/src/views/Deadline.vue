<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">期限中心</h2>
      </div>
      <div class="flex gap-8">
        <el-button :icon="AlarmClock" @click="onScan" :loading="scanning">立即扫描</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card accent">
        <div class="label">本页节点</div>
        <div class="value">{{ total }}</div>
      </div>
      <div class="stat-card danger">
        <div class="label">本页超期</div>
        <div class="value">{{ overdueCount }}</div>
      </div>
      <div class="stat-card warn">
        <div class="label">7 日内到期</div>
        <div class="value">{{ soonCount }}</div>
      </div>
    </div>

    <div class="card">
      <!-- 只有三个筛选控件，窄屏不必折叠，排成两行即可全部露出 -->
      <div class="toolbar">
        <el-select
          v-model="query.status"
          placeholder="状态"
          clearable
          class="f-status"
          style="width: 130px"
          @change="reload"
        >
          <el-option label="待提醒" value="PENDING" />
          <el-option label="已提醒" value="REMINDED" />
          <el-option label="已超期" value="OVERDUE" />
          <el-option label="已完成" value="DONE" />
        </el-select>
        <el-select
          v-model="query.deadlineType"
          placeholder="时限类型"
          clearable
          class="f-type"
          style="width: 140px"
          @change="reload"
        >
          <el-option label="法定时限" value="LEGAL" />
          <el-option label="内部时限" value="INTERNAL" />
        </el-select>
        <el-checkbox v-model="query.overdue" label="仅看超期" class="f-overdue" @change="reload" />
        <div class="spacer d-only"></div>
        <el-button class="d-only" @click="reset">重置</el-button>
        <el-button class="m-only m-reset" @click="reset">重置</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        class="d-table"
        :row-class-name="rowClass"
        :row-style="{ cursor: 'pointer' }"
        @row-dblclick="openCase"
      >
        <el-table-column label="案件" min-width="240">
          <template #default="{ row }">
            <el-link type="primary" @click="openCase(row)">
              {{ row.caseNo }}
            </el-link>
            <div class="text-sub" style="font-size: 12px">{{ row.caseName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="nodeName" label="节点" width="150" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.deadlineType === 'LEGAL' ? 'danger' : 'info'" effect="plain">
              {{ row.deadlineTypeName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deadlineDate" label="截止日期" width="120" />
        <el-table-column label="剩余 / 超期" width="130">
          <template #default="{ row }">
            <span v-if="row.status === 'DONE'" class="text-sub">已完成</span>
            <span v-else-if="row.remainDays === null || row.remainDays === undefined" class="text-sub">未开启</span>
            <span v-else-if="row.remainDays < 0" class="text-danger">超期 {{ -row.remainDays }} 天</span>
            <span v-else :class="{ 'text-warning': row.remainDays <= 7 }">{{ row.remainDays }} 天</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handlerName" label="责任人" width="100" />
        <el-table-column prop="overdueReason" label="超期原因" min-width="140" show-overflow-tooltip />
        <el-table-column prop="superviseOpinion" label="督办意见" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="supervise(row)">督办</el-button>
            <el-button link type="primary" size="small" @click="reason(row)">登记原因</el-button>
            <el-button v-if="row.status !== 'DONE'" link type="success" size="small" @click="done(row)">完成</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 窄屏：九列表格放不下，改为卡片，紧迫度用左侧色条直接体现 -->
      <div v-loading="loading" class="m-dl">
        <div
          v-for="row in list"
          :key="row.id"
          class="m-dl-card"
          :class="{ 'm-dl-card--overdue': row.status === 'OVERDUE' }"
          @click="openCase(row)"
        >
          <div class="m-l1">
            <span class="cell-no">{{ row.caseNo }}</span>
            <el-tag size="small" :type="statusTag(row)">{{ row.statusName }}</el-tag>
          </div>

          <div class="m-l2">{{ row.caseName }}</div>

          <div class="m-l3">
            <span class="m-node">{{ row.nodeName }}</span>
            <el-tag size="small" :type="row.deadlineType === 'LEGAL' ? 'danger' : 'info'" effect="plain">
              {{ row.deadlineTypeName }}
            </el-tag>
            <span class="m-days" :class="{ danger: row.remainDays < 0, warn: row.remainDays >= 0 && row.remainDays <= 7 }">
              <template v-if="row.status === 'DONE'">已完成</template>
              <template v-else-if="row.remainDays === null || row.remainDays === undefined">未开启</template>
              <template v-else-if="row.remainDays < 0">超期 {{ -row.remainDays }} 天</template>
              <template v-else>剩 {{ row.remainDays }} 天</template>
            </span>
          </div>

          <div class="m-l4">
            <span class="cell-dim">截止 {{ row.deadlineDate }}</span>
            <span class="cell-dim">责任人 {{ row.handlerName || '—' }}</span>
          </div>

          <div v-if="row.overdueReason || row.superviseOpinion" class="m-note">
            <div v-if="row.overdueReason"><i>超期原因</i>{{ row.overdueReason }}</div>
            <div v-if="row.superviseOpinion"><i>督办意见</i>{{ row.superviseOpinion }}</div>
          </div>

          <div class="m-l5">
            <el-button size="small" @click.stop="supervise(row)">督办</el-button>
            <el-button size="small" @click.stop="reason(row)">登记原因</el-button>
            <el-button v-if="row.status !== 'DONE'" size="small" type="success" plain @click.stop="done(row)">完成</el-button>
          </div>
        </div>
        <div v-if="!loading && !list.length" class="empty">暂无期限节点</div>
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, AlarmClock } from '@element-plus/icons-vue'
import { deadlineApi } from '../api/modules'
import { useNarrow } from '../composables/useNarrow'
import { useCaseNav } from '../composables/useCaseNav'

const router = useRouter()
/** 进案件详情时带上来源，返回时仍回到期限页 */
const { openCaseDetail } = useCaseNav()

const isNarrow = useNarrow()

const loading = ref(false)
const scanning = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 20,
  status: '',
  deadlineType: '',
  overdue: false
})

const overdueCount = computed(() => list.value.filter((i) => i.status === 'OVERDUE').length)
const soonCount = computed(
  () => list.value.filter((i) => i.status !== 'DONE' && i.remainDays >= 0 && i.remainDays <= 7).length
)

const pagerLayout = computed(() => (isNarrow.value ? 'prev, pager, next' : 'total, sizes, prev, pager, next'))

function statusTag(row) {
  if (row.status === 'OVERDUE') return 'danger'
  if (row.status === 'DONE') return 'success'
  // 距截止日不足 7 天（临近超期）与已提醒同一警示色
  if (row.dueSoon || row.status === 'REMINDED') return 'warning'
  return 'info'
}

function rowClass({ row }) {
  return row.overdue ? 'row-overdue' : ''
}

/** 双击行打开所属案件详情 */
function openCase(row) {
  if (row && row.caseId) {
    openCaseDetail(row.caseId, '/deadlines')
  }
}

async function load() {
  loading.value = true
  try {
    const data = await deadlineApi.page({ ...query })
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

function reset() {
  query.status = ''
  query.deadlineType = ''
  query.overdue = false
  reload()
}

async function onScan() {
  scanning.value = true
  try {
    const result = await deadlineApi.scan()
    ElMessage.success(`扫描完成：提醒 ${result.reminded} 条，超期 ${result.overdue} 条`)
    load()
  } finally {
    scanning.value = false
  }
}

async function supervise(row) {
  const { value } = await ElMessageBox.prompt('请输入督办意见', `督办：${row.nodeName}`, {
    inputType: 'textarea'
  })
  await deadlineApi.supervise(row.id, value)
  ElMessage.success('督办已发送')
  load()
}

async function reason(row) {
  const { value } = await ElMessageBox.prompt('请登记超期原因', `登记原因：${row.nodeName}`, {
    inputType: 'textarea'
  })
  await deadlineApi.registerReason(row.id, value)
  ElMessage.success('原因已登记')
  load()
}

async function done(row) {
  await deadlineApi.markDone(row.id)
  ElMessage.success('节点已完成')
  load()
}

onMounted(load)
</script>

<style scoped>

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
}

/* 卡片列表只在窄屏显示（须写在媒体查询之前） */
.m-dl {
  display: none;
}

@media (max-width: 768px) {
  /* 页头两个按钮等分一行 */
  .page-head .flex {
    width: 100%;
  }

  .page-head .flex .el-button {
    flex: 1;
    margin-left: 0;
  }

  /* 三张统计卡横排一行即可放下，不必各占一整行 */
  .stat-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
    margin-bottom: 10px;
  }

  .stat-card {
    padding: 9px 10px;
  }

  .stat-card .label {
    font-size: 10.5px;
  }

  .stat-card .value {
    font-size: 20px;
  }

  .stat-card .hint {
    margin-top: 1px;
    font-size: 9.5px;
    line-height: 1.35;
  }

  /* 状态 / 类型 一行，仅看超期与重置一行 */
  .toolbar {
    display: grid;
    grid-template-columns: 1fr 1fr auto;
    gap: 8px;
    padding: 10px 12px;
  }

  .f-status {
    grid-column: 1;
    grid-row: 1;
    width: 100% !important;
  }

  .f-type {
    grid-column: 2 / 4;
    grid-row: 1;
    width: 100% !important;
  }

  .f-overdue {
    grid-column: 1 / 3;
    grid-row: 2;
    margin-right: 0;
  }

  .m-reset {
    grid-column: 3;
    grid-row: 2;
    width: 100%;
    margin-left: 0;
  }

  .d-table {
    display: none;
  }

  .m-dl {
    display: block;
  }

  .pager {
    justify-content: center;
    padding: 10px 8px;
  }
}

/* ---------------- 期限卡片 ---------------- */
.m-dl-card {
  padding: 12px 14px;
  border-bottom: 1px solid var(--gsms-line-2);
  cursor: pointer;
}

.m-dl-card:last-child {
  border-bottom: 0;
}

/* 超期节点左侧标红条，扫视即可定位 */
.m-dl-card--overdue {
  border-left: 3px solid var(--gsms-danger);
  padding-left: 11px;
}

.m-l1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.m-l1 .cell-no {
  font-size: 13px;
}

.m-l2 {
  margin-top: 6px;
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.4;
}

.m-l3 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 7px;
  flex-wrap: wrap;
}

.m-node {
  font-size: 12.5px;
  color: var(--gsms-text-sub);
}

.m-days {
  margin-left: auto;
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}

.m-days.warn {
  color: var(--gsms-warning);
}

.m-days.danger {
  color: var(--gsms-danger);
  font-weight: 600;
}

.m-l4 {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
}

.m-note {
  margin-top: 8px;
  padding: 7px 9px;
  background: #f8fafd;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.m-note i {
  margin-right: 6px;
  color: var(--gsms-text-3);
  font-style: normal;
}

.m-l5 {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.m-l5 .el-button {
  flex: 1;
  margin-left: 0;
}
</style>
