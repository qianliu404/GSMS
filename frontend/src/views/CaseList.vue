<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">{{ pageTitle }}</h2>
        <p class="page-sub">共 {{ total }} 件</p>
      </div>
      <div class="head-actions">
        <el-button v-if="canImport && hasPerm('case:import')" :icon="Upload" @click="openImport">批量导入</el-button>
        <el-button v-if="hasPerm('case:create')" type="primary" :icon="Plus" @click="$router.push('/cases/create')">新建案件</el-button>
      </div>
    </div>

    <div class="card" v-if="currentFlow">
      <div class="card-head">
        <h2>{{ currentFlow.name }}流程</h2>
      </div>
      <div class="card-body">
        <FlowStrip :stages="currentFlow.stages" :picked="query.status" @pick="pickStage" />
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <div class="seg">
          <button :class="{ on: !query.caseType }" @click="setType('')">全部</button>
          <button :class="{ on: query.caseType === 'INJURY' }" @click="setType('INJURY')">工伤</button>
          <button :class="{ on: query.caseType === 'ARBITRATION' }" @click="setType('ARBITRATION')">劳动仲裁</button>
        </div>

        <el-select
          v-model="query.status"
          placeholder="全部阶段"
          clearable
          class="f-adv"
          :class="{ 'f-adv--closed': !moreOpen }"
          style="width: 112px"
          @change="reload"
        >
          <el-option
            v-for="(stage, i) in stageOptions"
            :key="stage.code"
            :label="`${i + 1}. ${stage.name}`"
            :value="stage.code"
          />
        </el-select>

        <el-select
          v-model="query.handlerId"
          placeholder="全部承办人"
          clearable
          filterable
          class="f-adv"
          :class="{ 'f-adv--closed': !moreOpen }"
          style="width: 116px"
          @change="reload"
        >
          <el-option v-for="u in users" :key="u.userId" :label="u.userName" :value="u.userId" />
        </el-select>

        <el-select
          v-model="query.departmentId"
          placeholder="全部部门"
          clearable
          class="f-adv"
          :class="{ 'f-adv--closed': !moreOpen }"
          style="width: 116px"
          @change="reload"
        >
          <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="String(d.id)" />
        </el-select>

        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="发生日期"
          end-placeholder="止"
          class="f-adv"
          :class="{ 'f-adv--closed': !moreOpen }"
          style="width: 188px"
          @change="onDateChange"
        />

        <el-input
          v-model="query.keyword"
          placeholder="案号 / 当事人"
          clearable
          class="f-keyword"
          style="width: 142px"
          :prefix-icon="Search"
          @keyup.enter="reload"
          @clear="reload"
        />

        <el-select
          v-model="query.deadlineState"
          placeholder="全部期限"
          class="f-adv"
          :class="{ 'f-adv--closed': !moreOpen }"
          style="width: 104px"
          @change="onDeadlineFilter"
        >
          <el-option label="全部期限" value="" />
          <el-option label="已超期" value="OVERDUE" />
          <el-option label="临近超期" value="DUE_SOON" />
        </el-select>

        <!-- 窄屏：除类型与搜索外收进「筛选」，避免生成五行控件 -->
        <el-button class="m-only f-filter" @click="moreOpen = !moreOpen">
          {{ moreOpen ? '收起' : '筛选' }}<em v-if="!moreOpen && advCount">{{ advCount }}</em>
        </el-button>

        <div class="spacer d-only"></div>
        <el-button class="d-only" @click="reset">重置</el-button>
        <el-button v-if="moreOpen" class="m-only f-reset" @click="reset">重置筛选</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        :row-class-name="rowClass"
        class="d-table"
        style="width: 100%"
        @row-click="openDetail"
      >
        <el-table-column label="案号" width="134">
          <template #default="{ row }">
            <span class="cell-no">{{ row.caseNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="72" label-class-name="m-hide" class-name="m-hide">
          <template #default="{ row }">
            <span class="tag" :class="row.caseType === 'INJURY' ? 'injury' : (row.caseType === 'ARBITRATION' ? 'arb' : 'neutral')">
              {{ row.caseType === 'INJURY' ? '工伤' : (row.caseType === 'ARBITRATION' ? '仲裁' : '关联') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="当事人 / 部门" min-width="150">
          <template #default="{ row }">
            <div class="cell-who">{{ row.employeeName }}</div>
            <div class="cell-dim">
              {{ row.plantArea || row.departmentName || '-' }}
              <template v-if="row.workshopTeam"> · {{ deptOf(row.workshopTeam) }}</template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="当前阶段" min-width="132">
          <template #default="{ row }">
            <span class="tag neutral">{{ stageOrder(row) }}</span>
            <span style="margin-left: 6px">{{ row.statusName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="流程进度" width="118" label-class-name="m-hide" class-name="m-hide">
          <template #default="{ row }">
            <div class="progress">
              <div class="pbar">
                <i
                  :class="row.caseType === 'INJURY' ? 'inj' : 'arb'"
                  :style="{ width: row.progress + '%' }"
                ></i>
              </div>
              <span class="pnum">{{ row.progress }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="承办人" width="88" label-class-name="m-hide" class-name="m-hide">
          <template #default="{ row }">{{ row.handlerName || row.handlerId }}</template>
        </el-table-column>
        <el-table-column label="发生日期" width="100" label-class-name="m-hide" class-name="m-hide">
          <template #default="{ row }">
            <span class="cell-dim">{{ row.occurDate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="截止日期" width="150">
          <template #default="{ row }">
            <span v-if="!row.currentDeadline" class="cell-dim">—</span>
            <span v-else-if="!row.deadlineStarted" class="due-cell idle">
              {{ row.currentDeadline }}
              <i class="due-tip">未开启</i>
            </span>
            <span v-else class="due-cell" :class="dueClass(row)">{{ row.currentDeadline }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span v-if="row.archiveStatus === 1" class="tag ok">已归档</span>
            <span v-else-if="row.status === 'CLOSED'" class="tag ok">已结案</span>
            <el-tooltip
              v-else-if="row.overdue"
              :content="`已超期 ${row.overdueDays} 天`"
              placement="top"
            >
              <span class="tag danger"><i class="dot"></i>已超期</span>
            </el-tooltip>
            <el-tooltip
              v-else-if="row.dueSoon"
              :content="`最近期限 ${row.nearestDeadline}，${row.daysToDeadline === 0 ? '今天到期' : `剩 ${row.daysToDeadline} 天`}`"
              placement="top"
            >
              <span class="tag warn"><i class="dot"></i>临近超期</span>
            </el-tooltip>
            <span v-else class="tag neutral"><i class="dot" style="color: #9aa7ba"></i>正常办理</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="118" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
            <el-button link type="danger" size="small" @click.stop="removeCase(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">没有符合条件的案件</div>
        </template>
      </el-table>

      <!-- 窄屏：表格横向挤不下，改为按案件分组的卡片，一屏可读全关键信息 -->
      <div v-loading="loading" class="m-list">
        <div
          v-for="row in list"
          :key="row.id"
          class="m-case"
          :class="{ 'm-case--overdue': row.overdue }"
          @click="openDetail(row)"
        >
          <div class="m-line1">
            <span
              class="tag"
              :class="row.caseType === 'INJURY' ? 'injury' : (row.caseType === 'ARBITRATION' ? 'arb' : 'neutral')"
            >{{ row.caseType === 'INJURY' ? '工伤' : (row.caseType === 'ARBITRATION' ? '仲裁' : '关联') }}</span>
            <span class="cell-no">{{ row.caseNo }}</span>
            <span v-if="row.archiveStatus === 1" class="tag ok">已归档</span>
            <span v-else-if="row.status === 'CLOSED'" class="tag ok">已结案</span>
            <span v-else-if="row.overdue" class="tag danger"><i class="dot"></i>超期 {{ row.overdueDays }} 天</span>
            <span v-else-if="row.dueSoon" class="tag warn"><i class="dot"></i>临近超期</span>
            <span v-else class="tag neutral">正常办理</span>
          </div>

          <div class="m-line2">
            <b>{{ row.employeeName }}</b>
            <span class="cell-dim">
              {{ row.plantArea || row.departmentName || '-' }}<template v-if="row.workshopTeam"> · {{ deptOf(row.workshopTeam) }}</template>
            </span>
          </div>

          <div class="m-line3">
            <span class="pill">{{ stageOrder(row) }} {{ row.statusName }}</span>
            <span class="cell-dim">{{ row.handlerName || row.handlerId }}</span>
            <span class="cell-dim">{{ row.occurDate }}</span>
          </div>

          <div v-if="row.currentDeadline" class="m-line3">
            <span class="cell-dim">截止</span>
            <span class="due-cell" :class="row.deadlineStarted ? dueClass(row) : 'idle'">
              {{ row.currentDeadline }}
              <i v-if="!row.deadlineStarted" class="due-tip">未开启</i>
            </span>
          </div>

          <div class="progress">
            <div class="pbar">
              <i
                :class="row.caseType === 'INJURY' ? 'inj' : 'arb'"
                :style="{ width: row.progress + '%' }"
              ></i>
            </div>
            <span class="pnum">{{ row.progress }}%</span>
          </div>

          <div class="m-line4">
            <el-button size="small" @click.stop="openDetail(row)">详情</el-button>
            <el-button size="small" type="danger" plain @click.stop="removeCase(row)">删除</el-button>
          </div>
        </div>
        <div v-if="!loading && !list.length" class="empty">没有符合条件的案件</div>
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          :layout="pagerLayout"
          :pager-count="isNarrow ? 5 : 7"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>

    <!-- 历史案件批量导入：先预检、全部通过才写入 -->
    <el-dialog v-model="importVisible" title="历史案件批量导入" :width="isNarrow ? '92%' : '760px'">
      <ol class="imp-guide">
        <li>必填列：<span class="imp-em">案件类型、案号、当事人姓名、发生日期、当前节点、承办人</span>；有问题的行不写入；导入后期限为「未开启」，需在详情点「提交」开始计时。</li>
      </ol>

      <div class="imp-actions">
        <el-button :icon="Download" @click="downloadTemplate">下载导入模板</el-button>
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept=".xlsx,.xls"
          :on-change="onPickFile"
        >
          <el-button type="primary" :loading="importing">{{ importResult ? '重新选择文件' : '选择文件并预检' }}</el-button>
        </el-upload>
        <span v-if="importFileName" class="imp-file">{{ importFileName }}</span>
      </div>

      <div v-if="importResult" class="imp-result">
        <div class="imp-summary">
          <span>共 <b>{{ importResult.total }}</b> 行</span>
          <span class="ok">可导入 <b>{{ importResult.importable }}</b> 行</span>
          <span v-if="importResult.failed" class="bad">有问题 <b>{{ importResult.failed }}</b> 行</span>
        </div>

        <el-table v-if="importResult.errors.length" :data="importResult.errors" height="260" size="small">
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column prop="caseNo" label="案号" width="150" />
          <el-table-column prop="message" label="问题" min-width="240" />
        </el-table>
        <el-table v-else :data="importResult.rows" height="260" size="small">
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column prop="caseType" label="类型" width="110" />
          <el-table-column prop="caseNo" label="案号" width="150" />
          <el-table-column prop="employeeName" label="当事人" width="90" />
          <el-table-column prop="statusName" label="当前节点" width="110" />
          <el-table-column prop="occurDate" label="发生日期" width="110" />
          <el-table-column prop="handlerId" label="承办人" min-width="90" />
        </el-table>

        <p v-if="importResult.failed" class="imp-tip bad">
          存在问题的行不会写入，请按「问题」列修改后重新上传。
        </p>
        <p v-else class="imp-tip">校验通过，确认后一次性写入 {{ importResult.importable }} 件案件。</p>
      </div>

      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button
          v-if="importResult"
          @click="resetImport"
        >重新选择</el-button>
        <el-button
          type="primary"
          :disabled="!importResult || importResult.failed > 0 || !importFile"
          :loading="importing"
          @click="doImport"
        >确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus, Search, Upload } from '@element-plus/icons-vue'
import { caseApi, caseImportApi, dashboardApi, systemApi, workflowApi } from '../api/modules'
import { useUserStore } from '../store/user'
import FlowStrip from '../components/FlowStrip.vue'
import { useNarrow } from '../composables/useNarrow'
import { useCaseNav } from '../composables/useCaseNav'

const route = useRoute()
const router = useRouter()
const { openCaseDetail } = useCaseNav()

const hasPerm = (code) => useUserStore().hasPerm(code)
const userStore = useUserStore()
/** 批量导入只对管理员开放 */
const canImport = computed(() =>
  (userStore.roles || []).some((r) => r === 'ROLE_SYS_ADMIN' || r === 'ROLE_CASE_ADMIN'))

const loading = ref(false)
const list = ref([])
const total = ref(0)
const dateRange = ref([])
const depts = ref([])
const users = ref([])
const nodes = ref([])
const flows = ref([])

/** 窄屏把次要筛选收进「筛选」开关，避免工具栏堆成五行 */
const isNarrow = useNarrow()
const moreOpen = ref(false)

const query = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
  caseType: '',
  status: '',
  handlerId: '',
  departmentId: '',
  occurDateFrom: '',
  occurDateTo: '',
  deadlineState: '',
  overdue: false,
  dueSoon: false
})

const pageTitle = computed(() => {
  if (query.caseType === 'INJURY') return '工伤案件'
  if (query.caseType === 'ARBITRATION') return '劳动仲裁案件'
  return '全部案件'
})

const isSingleType = computed(() => query.caseType === 'INJURY' || query.caseType === 'ARBITRATION')

/** 已生效的次要筛选数量，折叠时用角标提示，避免「筛选了但看不到」 */
const advCount = computed(() => {
  let n = 0
  if (query.status) n++
  if (query.handlerId) n++
  if (query.departmentId) n++
  if (query.occurDateFrom || query.occurDateTo) n++
  if (query.deadlineState) n++
  return n
})

/** 窄屏分页去掉每页条数与跳页，横向才放得下 */
const pagerLayout = computed(() =>
  isNarrow.value ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'
)

const currentFlow = computed(() => {
  if (!isSingleType.value) return null
  return flows.value.find((f) => f.key === query.caseType) || null
})

/** 阶段下拉：选了类型时用该类型的节点定义，否则用全部节点去重 */
const stageOptions = computed(() => {
  const source = isSingleType.value
    ? nodes.value.filter((n) => n.flowKey === query.caseType)
    : nodes.value.filter((n) => n.flowKey === 'INJURY')
  return source.sort((a, b) => a.sortNo - b.sortNo).map((n) => ({ code: n.statusCode, name: n.nodeName }))
})

const orderMap = computed(() => {
  const map = {}
  for (const n of nodes.value) {
    map[`${n.flowKey}:${n.statusCode}`] = n.sortNo
  }
  return map
})

const nodeCountMap = computed(() => {
  const map = {}
  for (const f of flows.value) {
    map[f.key] = f.stages.length
  }
  return map
})

/** 当前阶段展示为「3/10」形式，序号取自节点配置 */
function stageOrder(row) {
  const sort = orderMap.value[`${row.caseType}:${row.status}`]
  const totalCount = nodeCountMap.value[row.caseType] || 0
  if (!sort || !totalCount) return '-'
  return `${sort}/${totalCount}`
}

function deptOf(team) {
  return (team || '').split(' · ')[0] || team
}

function rowClass({ row }) {
  return row.overdue ? 'row-overdue' : ''
}

/** 截止日期着色：已超期红、7 天内到期黄，未开启由调用方置灰 */
function dueClass(row) {
  if (!row.currentDeadline) return ''
  const target = new Date(`${row.currentDeadline}T00:00:00`)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const days = Math.round((target - today) / 86400000)
  if (days < 0) return 'danger'
  if (days < 7) return 'warn'
  return ''
}

function onDateChange(val) {
  query.occurDateFrom = val ? val[0] : ''
  query.occurDateTo = val ? val[1] : ''
  reload()
}

/**
 * 类型分段：普通「案件列表」只做本页筛选，不改路由；
 * 但若当前是从侧栏「工伤案件 / 劳动仲裁」模块进入（URL 带 type），切换类型仍同步到对应模块，避免标题与侧栏错位。
 */
function setType(type) {
  query.caseType = type
  query.status = ''
  reload()
  if (route.query.type && route.query.type !== type) {
    localTypeChange = true
    router.replace({ path: '/cases', query: type ? { type } : {} })
  }
}

function pickStage(status) {
  query.status = query.status === status ? '' : status
  reload()
}

/** 期限状态是一个下拉，转换成后端两个互斥的布尔参数 */
function onDeadlineFilter(value) {
  query.overdue = value === 'OVERDUE'
  query.dueSoon = value === 'DUE_SOON'
  reload()
}

/* 请求序号：连续切换筛选时，丢弃先发出但后返回的旧响应，避免列表被旧结果覆盖 */
let loadSeq = 0

async function load() {
  const seq = ++loadSeq
  loading.value = true
  try {
    const data = await caseApi.page({ ...query })
    if (seq !== loadSeq) return
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function reload() {
  query.pageNum = 1
  load()
}

function reset() {
  query.keyword = ''
  query.status = ''
  query.handlerId = ''
  query.departmentId = ''
  query.occurDateFrom = ''
  query.occurDateTo = ''
  query.overdue = false
  query.dueSoon = false
  query.deadlineState = ''
  dateRange.value = []
  reload()
}

/**
 * 当前筛选状态对应的列表地址。
 * 打开详情时把它写进 query.from，返回列表时按它复原类型、筛选、分页与滚动位置，
 * 这样从「工伤案件」列表进去、返回后仍是「工伤案件」列表。
 */
function buildListUrl() {
  const p = new URLSearchParams()
  if (query.caseType) p.set('type', query.caseType)
  if (query.status) p.set('status', query.status)
  if (query.keyword) p.set('keyword', query.keyword)
  if (query.handlerId) p.set('handlerId', query.handlerId)
  if (query.departmentId) p.set('departmentId', query.departmentId)
  if (query.occurDateFrom) p.set('dateFrom', query.occurDateFrom)
  if (query.occurDateTo) p.set('dateTo', query.occurDateTo)
  if (query.deadlineState) p.set('deadlineState', query.deadlineState)
  if (query.pageNum > 1) p.set('page', String(query.pageNum))
  if (query.pageSize !== 20) p.set('size', String(query.pageSize))
  const qs = p.toString()
  return qs ? `/cases?${qs}` : '/cases'
}

/** 详情页返回时滚回原处：只在本页内存里记一条，10 分钟内且地址一致才复原 */
const LIST_VIEW_KEY = 'gsms:case-list-view'

function rememberListPosition() {
  const box = document.querySelector('.content')
  if (!box) return
  try {
    sessionStorage.setItem(
      LIST_VIEW_KEY,
      JSON.stringify({ url: buildListUrl(), top: box.scrollTop, at: Date.now() })
    )
  } catch (e) {
    /* 隐私模式禁用 storage 时忽略，不影响跳转 */
  }
}

function restoreListPosition() {
  const box = document.querySelector('.content')
  if (!box) return
  let saved = null
  try {
    saved = JSON.parse(sessionStorage.getItem(LIST_VIEW_KEY) || 'null')
  } catch (e) {
    saved = null
  }
  if (!saved || saved.url !== buildListUrl() || Date.now() - (saved.at || 0) > 600000) return
  sessionStorage.removeItem(LIST_VIEW_KEY)
  if (saved.top > 0) box.scrollTop = saved.top
}

function openDetail(row) {
  if (!row || !row.id) return
  const from = buildListUrl()
  rememberListPosition()
  openCaseDetail(row.id, from)
}

/** 删除案件：软删除进入回收站，可在「回收站」恢复 */
async function removeCase(row) {
  await ElMessageBox.confirm(
    `确认删除案件「${row.caseNo}」？删除后进入回收站，可在回收站恢复。`,
    '删除案件',
    { type: 'warning', confirmButtonText: '确认删除' }
  )
  await caseApi.remove(row.id)
  ElMessage.success('案件已移入回收站')
  load()
}

/* ------------------ 历史案件批量导入 ------------------ */
const importVisible = ref(false)
const importing = ref(false)
const importFile = ref(null)
const importFileName = ref('')
const importResult = ref(null)

function openImport() {
  resetImport()
  importVisible.value = true
}

function resetImport() {
  importFile.value = null
  importFileName.value = ''
  importResult.value = null
}

async function downloadTemplate() {
  const res = await caseImportApi.template()
  const url = URL.createObjectURL(new Blob([res.data]))
  const link = document.createElement('a')
  link.href = url
  link.download = '历史案件导入模板.xlsx'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

/** 选文件即预检：只解析校验，不写库 */
async function onPickFile(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  importFile.value = file
  importFileName.value = file.name
  importResult.value = null
  importing.value = true
  try {
    importResult.value = await caseImportApi.preview(file)
  } catch (e) {
    importFile.value = null
    importFileName.value = ''
  } finally {
    importing.value = false
  }
}

async function doImport() {
  if (!importFile.value) return
  importing.value = true
  try {
    const res = await caseImportApi.confirm(importFile.value)
    ElMessage.success(`已导入 ${res.imported} 件历史案件`)
    importVisible.value = false
    resetImport()
    load()
  } finally {
    importing.value = false
  }
}

/* 标记「本页分段刚改过类型」，避免随后清理 URL 触发的 watch 把选中项重置 */
let localTypeChange = false

/** query 取值：数组取首个，缺省空串 */
function q(name) {
  const v = route.query[name]
  return Array.isArray(v) ? v[0] || '' : v || ''
}

/**
 * 路由 query 变化（侧栏切类型、总览下钻、从详情返回）时同步筛选条件。
 * 筛选、分页都认 URL，所以详情页返回时能把原来那一页、那套筛选原样恢复。
 */
function syncFromRoute() {
  if (localTypeChange) {
    localTypeChange = false
  } else {
    const type = q('type')
    query.caseType = type === 'INJURY' || type === 'ARBITRATION' ? type : ''
  }
  query.status = q('status')
  query.keyword = q('keyword')
  query.handlerId = q('handlerId')
  query.departmentId = q('departmentId')
  query.occurDateFrom = q('dateFrom')
  query.occurDateTo = q('dateTo')
  dateRange.value =
    query.occurDateFrom || query.occurDateTo ? [query.occurDateFrom, query.occurDateTo] : []
  query.deadlineState = q('deadlineState')
  query.overdue = query.deadlineState === 'OVERDUE'
  query.dueSoon = query.deadlineState === 'DUE_SOON'
  const size = Number(q('size'))
  query.pageSize = [10, 20, 50, 100].includes(size) ? size : 20
  query.pageNum = Math.max(1, Number(q('page')) || 1)
  return load()
}

watch(() => route.query, syncFromRoute)

onMounted(async () => {
  const firstLoad = syncFromRoute()
  try {
    const [deptList, userList, nodeList, overview] = await Promise.all([
      systemApi.depts(),
      systemApi.userOptions(),
      workflowApi.nodes(),
      dashboardApi.overview()
    ])
    depts.value = deptList || []
    users.value = userList || []
    nodes.value = nodeList || []
    flows.value = overview.flows || []
  } catch (e) {
    /* 选项加载失败不影响列表 */
  }
  await firstLoad
  await nextTick()
  restoreListPosition()
})

</script>

<style scoped>
.seg {
  display: flex;
  background: #f4f7fb;
  border-radius: 8px;
  padding: 2px;
  border: 1px solid var(--gsms-line-2);
}

.seg button {
  border: 0;
  background: transparent;
  padding: 5px 12px;
  border-radius: 6px;
  color: var(--gsms-text-sub);
  font-size: 12.5px;
  font-family: inherit;
  cursor: pointer;
}

.seg button.on {
  background: #fff;
  color: var(--gsms-text);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(20, 32, 50, 0.08);
}

.picked-tip {
  font-size: 12px;
  color: var(--gsms-text-3);
}


.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
}

/* 卡片列表只在窄屏显示（媒体查询内的 display:block 需排在这条之后才生效） */
.m-list {
  display: none;
}

/* ---------------- 窄屏：卡片列表（工具栏折叠样式见全局 index.css） ---------------- */
@media (max-width: 768px) {
  .toolbar {
    gap: 8px;
    padding: 10px 12px;
  }

  .d-table {
    display: none;
  }

  .m-list {
    display: block;
  }

  .pager {
    justify-content: center;
    padding: 10px 8px;
  }
}

/* 卡片列表本身按桌面隐藏，窄屏才显示 */
.m-case {
  padding: 12px 14px;
  border-bottom: 1px solid var(--gsms-line-2);
  cursor: pointer;
}

.m-case:last-child {
  border-bottom: 0;
}

/* 超期案件左侧留一道红条，扫视时能立刻定位 */
.m-case--overdue {
  border-left: 3px solid var(--gsms-danger);
  padding-left: 11px;
}

.m-line1 {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 状态标签始终靠右，案号与类型靠左 */
.m-line1 > .tag:last-child {
  margin-left: auto;
}

.m-line1 .cell-no {
  font-size: 13px;
}

.m-line2 {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 7px;
  font-size: 14px;
}

/* 截止日期：未开启置灰，超期红、临近黄 */
.due-cell {
  white-space: nowrap;
  font-size: 12.5px;
  color: var(--gsms-text);
  font-variant-numeric: tabular-nums;
}

.due-cell.danger {
  color: var(--gsms-danger);
  font-weight: 600;
}

.due-cell.warn {
  color: var(--gsms-warning);
  font-weight: 600;
}

.due-cell.idle {
  color: var(--gsms-text-3);
}

.due-tip {
  margin-left: 4px;
  padding: 0 4px;
  font-size: 10.5px;
  font-style: normal;
  color: var(--gsms-text-3);
  background: #f1f4f9;
  border-radius: 6px;
}

.m-line2 .cell-dim {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: right;
}

.m-line3 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 7px;
}

.m-line3 .pill {
  flex: 0 0 auto;
}

.m-line3 .cell-dim:last-child {
  margin-left: auto;
}

.m-case .progress {
  margin-top: 9px;
  min-width: 0;
}

.m-line4 {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}

.m-line4 .el-button {
  margin-left: 0;
  min-width: 68px;
}

/* ---------------- 批量导入弹窗 ---------------- */
.imp-guide {
  margin: 0 0 14px;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--gsms-text-sub);
}

.imp-em {
  color: var(--gsms-text);
  font-weight: 600;
}

.imp-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.imp-file {
  font-size: 12px;
  color: var(--gsms-text-3);
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.imp-result {
  margin-top: 14px;
}

.imp-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
  font-size: 12.5px;
  color: var(--gsms-text-sub);
}

.imp-summary .ok b {
  color: var(--gsms-success);
}

.imp-summary .bad b {
  color: var(--gsms-danger);
}

.imp-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--gsms-text-3);
}

.imp-tip.bad {
  color: var(--gsms-danger);
}

@media (max-width: 768px) {
  .imp-actions {
    gap: 8px;
  }

  .imp-actions .el-button {
    width: 100%;
  }
}
</style>
