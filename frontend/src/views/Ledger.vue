<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">台账</h2>
      </div>
      <div class="head-actions">
        <el-button v-if="hasPerm('ledger:edit')" :icon="Setting" @click="openColumns">字段设置</el-button>
        <el-button v-if="hasPerm('ledger:export')" :icon="Download" :loading="exporting" @click="exportExcel">导出台账</el-button>
        <el-button v-if="hasPerm('case:create')" type="primary" :icon="Plus" @click="$router.push('/cases/create')">新建案件</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card injury">
        <div class="label">台账案件</div>
        <div class="value">{{ summary.total || 0 }}</div>
        <div class="hint">工伤 {{ summary.injuryCount || 0 }} · 仲裁 {{ summary.arbitrationCount || 0 }}</div>
      </div>
      <div class="stat-card warn">
        <div class="label">待认定 / 认定中</div>
        <div class="value">{{ summary.waitRecognize || 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="label">待鉴定</div>
        <div class="value">{{ summary.waitAppraisal || 0 }}</div>
      </div>
      <div class="stat-card ok">
        <div class="label">待遇未结清</div>
        <div class="value">{{ summary.unpaid || 0 }}</div>
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
          v-model="query.plantArea"
          placeholder="全部厂区"
          clearable
          class="f-adv"
          :class="{ 'f-adv--closed': !moreOpen }"
          style="width: 124px"
          @change="reload"
        >
          <el-option v-for="a in areas" :key="a.id" :label="a.dictName" :value="a.dictName" />
        </el-select>
        <el-select
          v-model="query.recognizeResult"
          placeholder="全部认定状态"
          clearable
          class="f-adv"
          :class="{ 'f-adv--closed': !moreOpen }"
          style="width: 138px"
          :disabled="query.caseType === 'ARBITRATION'"
          @change="reload"
        >
          <el-option label="未申报" value="NOT_DECLARED" />
          <el-option label="申报中" value="DECLARING" />
          <el-option label="已认定（工伤）" value="RECOGNIZED" />
          <el-option label="不予认定" value="NOT_RECOGNIZED" />
        </el-select>
        <el-select
          v-model="query.subsidyStatus"
          placeholder="全部申领状态"
          clearable
          class="f-adv"
          :class="{ 'f-adv--closed': !moreOpen }"
          style="width: 138px"
          :disabled="query.caseType === 'ARBITRATION'"
          @change="reload"
        >
          <el-option v-for="s in SUBSIDY_STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="案号 / 仲裁案号 / 姓名 / 班组"
          clearable
          class="f-keyword"
          style="width: 200px"
          :prefix-icon="Search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button class="m-only f-filter" @click="moreOpen = !moreOpen">
          {{ moreOpen ? '收起' : '筛选' }}<em v-if="!moreOpen && advCount">{{ advCount }}</em>
        </el-button>
        <div class="spacer d-only"></div>
        <span class="tip">共 {{ total }} 条　·　{{ scopeLabel }}视图</span>
        <el-button class="d-only" @click="reset">重置</el-button>
        <el-button v-if="moreOpen" class="m-only f-reset" @click="reset">重置筛选</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        class="d-table"
        style="width: 100%"
        :show-overflow-tooltip="false"
        :row-style="{ cursor: 'pointer' }"
        @row-dblclick="openRow"
      >
        <el-table-column
          v-for="col in visibleColumns"
          :key="col.key"
          :label="col.label"
          :width="col.width"
          :min-width="col.minWidth"
        >
          <template #default="{ row, $index }">
            <template v-if="col.key === 'seq'">
              <span class="cell-dim">{{ (query.pageNum - 1) * query.pageSize + $index + 1 }}</span>
            </template>
            <template v-else-if="col.key === 'type'">
              <span class="tag" :class="row.caseType === 'INJURY' ? 'injury' : 'arb'">
                {{ row.caseTypeName }}
              </span>
            </template>
            <template v-else-if="col.key === 'employeeName'">
              <span class="cell-who link" @click="openRow(row)" @dblclick.stop>{{ row.employeeName }}</span>
            </template>
            <template v-else-if="col.key === 'workshopTeam'">
              <span class="team-cell" :title="row.workshopTeam || row.departmentName">
                {{ row.workshopTeam || row.departmentName || '—' }}
              </span>
            </template>
            <template v-else-if="col.key === 'injuryDesc'">
              <div v-if="row.caseType === 'INJURY'" class="cause">
                <div>{{ row.injuryReason || '—' }}</div>
                <div class="cell-dim date-line">{{ row.injuryDate }}</div>
              </div>
              <span v-else class="cell-dim">—</span>
            </template>
            <template v-else-if="col.key === 'disputeReason'">
              <div v-if="row.caseType !== 'INJURY'" class="cause">{{ row.disputeReason || '—' }}</div>
              <span v-else class="cell-dim">—</span>
            </template>
            <template v-else-if="col.key === 'description'">
              <div class="cause desc" :title="row.description || ''">{{ row.description || '—' }}</div>
            </template>
            <template v-else-if="col.key === 'restDays'">
              <template v-if="row.caseType === 'INJURY'">
                <span v-if="row.restDays !== null && row.restDays !== undefined">{{ row.restDays }} 天</span>
                <span v-else class="cell-dim">待登记</span>
              </template>
              <span v-else class="cell-dim">—</span>
            </template>
            <template v-else-if="col.key === 'performAmount'">
              <span v-if="row.caseType === 'INJURY'" class="cell-dim">—</span>
              <span v-else :class="{ 'cell-dim': row.performAmount === null || row.performAmount === undefined }">
                {{ row.performAmount === null || row.performAmount === undefined
                  ? '待登记' : money(row.performAmount, row.amountMasked) }}
              </span>
            </template>
            <template v-else-if="col.key === 'recognize'">
              <span v-if="row.caseType === 'INJURY'" class="tag" :class="recognizeClass(row.recognizeResult)">
                {{ row.recognizeResultName || '未申报' }}
              </span>
              <span v-else class="cell-dim">—</span>
            </template>
            <template v-else-if="col.key === 'appraisal'">
              <template v-if="row.caseType === 'INJURY'">
                <span v-if="row.appraisalLevel && row.appraisalLevel !== '—'">{{ row.appraisalLevel }} 伤残</span>
                <span v-else class="cell-dim">待鉴定</span>
              </template>
              <span v-else class="cell-dim">—</span>
            </template>
            <template v-else-if="SUBSIDY_KEYS.includes(col.key)">
              <template v-if="row.caseType === 'INJURY'">
                <span
                  class="tag stat-chip"
                  :class="subsidyClass(row[subsidyField(col.key, 'status')])"
                  @click="openSubsidy(row, subsidyItem(col.key))"
                  @dblclick.stop
                >
                  {{ subsidyName(row[subsidyField(col.key, 'status')]) }} ↻
                </span>
                <div class="amount">{{ money(row[subsidyField(col.key, 'amount')], row.amountMasked) }}</div>
              </template>
              <span v-else class="cell-dim">—</span>
            </template>
            <template v-else>
              <span :class="{ 'cell-dim': isDash(row, col.key) }">{{ cellText(row, col.key) }}</span>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">暂无台账数据</div>
        </template>
      </el-table>

      <!-- 窄屏：台账列多（默认 13 列）横向放不下，改为卡片：核心信息常显，其余字段按需展开 -->
      <div v-loading="loading" class="m-led">
        <div
          v-for="(row, idx) in list"
          :key="row.id"
          class="m-led-card"
          @click="openRow(row)"
        >
          <div class="m-l1">
            <span class="tag" :class="row.caseType === 'INJURY' ? 'injury' : 'arb'">{{ row.caseTypeName }}</span>
            <span class="cell-no">{{ row.caseNo }}</span>
            <span class="cell-dim m-seq">{{ (query.pageNum - 1) * query.pageSize + idx + 1 }}</span>
          </div>

          <div class="m-l2">
            <b>{{ row.employeeName }}</b>
            <span class="cell-dim m-org">
              {{ row.plantArea || '—' }}<template v-if="row.workshopTeam"> · {{ row.workshopTeam }}</template>
            </span>
          </div>

          <div class="m-l3">
            <span v-if="hasCol('statusName')" class="pill">{{ row.statusName }}</span>
            <template v-if="row.caseType === 'INJURY'">
              <span v-if="hasCol('recognize')" class="tag" :class="recognizeClass(row.recognizeResult)">
                {{ row.recognizeResultName || '未申报' }}
              </span>
              <span v-if="hasCol('appraisal')" class="cell-dim">
                {{ row.appraisalLevel && row.appraisalLevel !== '—' ? `${row.appraisalLevel} 伤残` : '待鉴定' }}
              </span>
            </template>
          </div>

          <div v-if="row.caseType === 'INJURY' && subsidyCols.length" class="m-subsidy">
            <div
              v-for="item in subsidyCols"
              :key="item.column"
              class="m-sub"
              @click.stop="openSubsidy(row, item)"
            >
              <span class="m-sub-label">{{ item.short }}</span>
              <span class="tag stat-chip" :class="subsidyClass(row[item.statusField])">
                {{ subsidyName(row[item.statusField]) }}
              </span>
              <span class="amount">{{ money(row[item.amountField], row.amountMasked) }}</span>
            </div>
          </div>

          <div v-if="extraCols(row).length" class="m-extra">
            <div v-if="expandedIds.includes(row.id)" class="m-kv-list">
              <div v-for="col in extraCols(row)" :key="col.key" class="m-kv">
                <span class="m-k">{{ col.label }}</span>
                <span class="m-v">{{ cellText(row, col.key) }}</span>
              </div>
            </div>
            <button class="m-more" @click.stop="toggleExpand(row.id)">
              {{ expandedIds.includes(row.id) ? '收起' : `展开 ${extraCols(row).length} 项字段` }}
            </button>
          </div>
        </div>
        <div v-if="!loading && !list.length" class="empty">暂无台账数据</div>
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

    <!-- 字段设置：全部 / 工伤 / 劳动仲裁三套配置各自独立，互不覆盖 -->
    <el-dialog v-model="columnsVisible" title="台账显示字段" width="600px">
      <div class="scope-bar">
        <span class="scope-label">配置范围</span>
        <el-radio-group v-model="dialogScope" size="small">
          <el-radio-button v-for="s in SCOPES" :key="s.key" :label="s.key">{{ s.label }}</el-radio-button>
        </el-radio-group>
        <span class="scope-hint">当前编辑：{{ dialogScopeLabel }}</span>
      </div>
      <p class="dlg-tip" style="margin-bottom: 12px">
        三个范围分别保存：修改{{ dialogScopeLabel }}的字段不会影响其它范围；各范围的表格列与「导出台账」列保持一致，序号列固定显示在最前。
      </p>
      <el-checkbox-group v-model="drafts[dialogScope]" class="col-group">
        <el-checkbox
          v-for="col in availableColumns"
          :key="col.key"
          :label="col.key"
          :disabled="col.key === 'seq'"
        >
          {{ col.label }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button link @click="resetColumns">恢复{{ dialogScopeLabel }}默认</el-button>
        <el-button @click="columnsVisible = false">取消</el-button>
        <el-button type="primary" @click="applyColumns">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" title="更新一次性待遇申领" width="420px">
      <div class="dlg-case">
        <b>{{ current?.caseNo }}</b> · {{ current?.employeeName }} · {{ dialogItem?.label }}
      </div>
      <el-form label-position="top">
        <el-form-item label="申领状态">
          <el-radio-group v-model="dialogForm.status">
            <el-radio-button v-for="s in SUBSIDY_STATUS" :key="s.value" :label="s.value">
              {{ s.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额（元）">
          <el-input-number v-model="dialogForm.amount" :min="0" :step="1000" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div class="dlg-tip">金额合计将同步到报表中心的赔付统计；无权限查看金额的账号仅能更新状态。</div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSubsidy">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Download, Setting } from '@element-plus/icons-vue'
import { ledgerApi, systemApi } from '../api/modules'
import { useUserStore } from '../store/user'
import { useNarrow } from '../composables/useNarrow'
import { useCaseNav } from '../composables/useCaseNav'

/** 进案件详情时带上来源，返回时仍回到台账 */
const hasPerm = (code) => useUserStore().hasPerm(code)
const { openCaseDetail } = useCaseNav()

/* 厂区选项来自字典，可在新建案件页自定义新增 */
const areas = ref([])

const SUBSIDY_STATUS = [
  { value: 'NOT_APPLIED', label: '未申领' },
  { value: 'APPLYING', label: '申领中' },
  { value: 'RECEIVED', label: '已到账' },
  { value: 'INELIGIBLE', label: '不符合条件' }
]

const SUBSIDY_ITEMS = [
  {
    key: 'DISABILITY',
    label: '一次性伤残金申领',
    short: '伤残金',
    statusField: 'subsidyDisabilityStatus',
    amountField: 'subsidyDisabilityAmount',
    column: 'subsidyDisability'
  },
  {
    key: 'MEDICAL',
    label: '一次性医疗金申领',
    short: '医疗金',
    statusField: 'subsidyMedicalStatus',
    amountField: 'subsidyMedicalAmount',
    column: 'subsidyMedical'
  },
  {
    key: 'EMPLOYMENT',
    label: '一次性就业金申领',
    short: '就业金',
    statusField: 'subsidyEmploymentStatus',
    amountField: 'subsidyEmploymentAmount',
    column: 'subsidyEmployment'
  }
]

const SUBSIDY_KEYS = SUBSIDY_ITEMS.map((i) => i.column)

/** 台账可选字段：顺序与后端 LedgerColumns 保持一致，导出即按此顺序 */
const COLUMNS = [
  { key: 'seq', label: '序号', width: 48, scope: 'BOTH' },
  { key: 'type', label: '类型', width: 64, scope: 'BOTH' },
  { key: 'caseNo', label: '案号', width: 132, scope: 'BOTH' },
  { key: 'plantArea', label: '厂区', width: 84, scope: 'BOTH' },
  { key: 'workshopTeam', label: '车间班组 / 部门', width: 150, scope: 'BOTH' },
  { key: 'employeeName', label: '姓名', width: 88, scope: 'BOTH' },
  { key: 'occurDate', label: '发生日期', width: 100, scope: 'BOTH' },
  { key: 'injuryDesc', label: '受伤原因及日期', minWidth: 200, scope: 'INJURY' },
  { key: 'disputeReason', label: '争议事由', minWidth: 200, scope: 'ARBITRATION' },
  { key: 'description', label: '案件描述', minWidth: 200, scope: 'BOTH' },
  { key: 'leaderL1', label: '小班长', width: 66, scope: 'INJURY' },
  { key: 'leaderL2', label: '大班长', width: 66, scope: 'INJURY' },
  { key: 'recognize', label: '工伤认定情况', width: 96, scope: 'INJURY' },
  { key: 'appraisal', label: '劳动能力鉴定结论', width: 96, scope: 'INJURY' },
  { key: 'restDays', label: '休假天数', width: 84, scope: 'INJURY' },
  { key: 'subsidyDisability', label: '一次性伤残金申领', width: 104, scope: 'INJURY' },
  { key: 'subsidyMedical', label: '一次性医疗金申领', width: 104, scope: 'INJURY' },
  { key: 'subsidyEmployment', label: '一次性就业金申领', width: 104, scope: 'INJURY' },
  { key: 'arbitrationNo', label: '仲裁案号', width: 120, scope: 'ARBITRATION' },
  { key: 'applyDate', label: '申请日期', width: 96, scope: 'ARBITRATION' },
  { key: 'acceptDate', label: '受理日期', width: 96, scope: 'ARBITRATION' },
  { key: 'hearingDate', label: '开庭时间', width: 124, scope: 'ARBITRATION' },
  { key: 'resultType', label: '结案方式', width: 90, scope: 'BOTH' },
  { key: 'awardAmount', label: '裁决/调解金额', width: 112, scope: 'ARBITRATION' },
  { key: 'performStatus', label: '履行状态', width: 92, scope: 'ARBITRATION' },
  { key: 'performAmount', label: '履行款', width: 104, scope: 'ARBITRATION' },
  { key: 'handlerName', label: '承办人', width: 76, scope: 'BOTH' },
  { key: 'statusName', label: '当前状态', width: 92, scope: 'BOTH' },
  { key: 'closeDate', label: '结案日期', width: 96, scope: 'BOTH' }
]

const query = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
  caseType: '',
  plantArea: '',
  recognizeResult: '',
  subsidyStatus: ''
})

/** 窄屏：次要筛选收进「筛选」开关；字段多则卡片按需展开 */
const isNarrow = useNarrow()
const moreOpen = ref(false)
const expandedIds = ref([])

function toggleExpand(id) {
  const i = expandedIds.value.indexOf(id)
  if (i >= 0) expandedIds.value.splice(i, 1)
  else expandedIds.value.push(id)
}

const advCount = computed(() => {
  let n = 0
  if (query.plantArea) n++
  if (query.recognizeResult) n++
  if (query.subsidyStatus) n++
  return n
})

const pagerLayout = computed(() =>
  isNarrow.value ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'
)

/** 全部 / 工伤 / 劳动仲裁：三套字段配置相互隔离，互不影响 */
const SCOPES = [
  { key: 'ALL', label: '全部' },
  { key: 'INJURY', label: '工伤' },
  { key: 'ARBITRATION', label: '劳动仲裁' }
]

const DEFAULTS = {
  ALL: [
    'seq', 'type', 'caseNo', 'plantArea', 'workshopTeam', 'employeeName',
    'injuryDesc', 'disputeReason', 'recognize', 'appraisal',
    'resultType', 'statusName', 'closeDate'
  ],
  INJURY: [
    'seq', 'type', 'plantArea', 'workshopTeam', 'employeeName', 'injuryDesc',
    'leaderL1', 'leaderL2', 'recognize', 'appraisal', 'restDays',
    'subsidyDisability', 'subsidyMedical', 'subsidyEmployment'
  ],
  ARBITRATION: [
    'seq', 'type', 'caseNo', 'plantArea', 'employeeName', 'disputeReason',
    'arbitrationNo', 'applyDate', 'hearingDate', 'resultType',
    'awardAmount', 'performStatus', 'performAmount', 'closeDate'
  ]
}

const COLUMN_STORE = 'gsms_ledger_columns'

/** 统一按 COLUMNS 顺序排列，保证表格列序与导出列序一致 */
function orderKeys(keys) {
  const picked = new Set(['seq', ...keys])
  return COLUMNS.filter((c) => picked.has(c.key)).map((c) => c.key)
}

function loadKeys(scope) {
  try {
    const saved = JSON.parse(localStorage.getItem(`${COLUMN_STORE}_${scope}`) || '[]')
    const valid = saved.filter((k) => COLUMNS.some((c) => c.key === k))
    if (valid.length) return orderKeys(valid)
  } catch (e) {
    /* 本地存储损坏时回落到该范围的默认列 */
  }
  return [...DEFAULTS[scope]]
}

const prefs = reactive({
  ALL: loadKeys('ALL'),
  INJURY: loadKeys('INJURY'),
  ARBITRATION: loadKeys('ARBITRATION')
})

const activeScope = computed(() => query.caseType || 'ALL')
const scopeLabel = computed(() => SCOPES.find((x) => x.key === activeScope.value)?.label || '全部')
/** 当前视图使用的列（表格与导出共用） */
const visibleKeys = computed(() => prefs[activeScope.value])
const visibleColumns = computed(() => COLUMNS.filter((c) => visibleKeys.value.includes(c.key)))

/* ---------- 窄屏卡片：核心信息常显，其余可见字段按需展开 ---------- */

/** 已在卡片头部/摘要区展示的字段，不再重复进展开区 */
const HEADER_KEYS = [
  'seq', 'type', 'caseNo', 'employeeName', 'plantArea', 'workshopTeam',
  'statusName', 'recognize', 'appraisal', ...SUBSIDY_KEYS
]

function hasCol(key) {
  return visibleKeys.value.includes(key)
}

/** 展开区字段：当前视图可见、且不属于头部摘要、且本行有值的列 */
function extraCols(row) {
  return visibleColumns.value.filter((c) => !HEADER_KEYS.includes(c.key) && !isDash(row, c.key))
}

/** 三项待遇仅在对应列可见时展示，避免绕过「字段设置」 */
const subsidyCols = computed(() => SUBSIDY_ITEMS.filter((i) => visibleKeys.value.includes(i.column)))

const columnsVisible = ref(false)
const dialogScope = ref('INJURY')
const dialogScopeLabel = computed(
  () => SCOPES.find((x) => x.key === dialogScope.value)?.label || '全部'
)
/** 弹窗内每个范围各自一份草稿，切换范围不会互相覆盖 */
const drafts = reactive({ ALL: [], INJURY: [], ARBITRATION: [] })
/** 工伤视图不出现仲裁专属字段，仲裁视图不出现工伤专属字段 */
const availableColumns = computed(() => COLUMNS.filter(
  (c) => dialogScope.value === 'ALL' || c.scope === 'BOTH' || c.scope === dialogScope.value
))

function openColumns() {
  for (const scope of SCOPES) {
    drafts[scope.key] = [...prefs[scope.key]]
  }
  dialogScope.value = activeScope.value
  columnsVisible.value = true
}

function applyColumns() {
  for (const scope of SCOPES) {
    const picked = (drafts[scope.key] || []).filter((k) => k !== 'seq')
    const next = picked.length ? orderKeys(picked) : [...DEFAULTS[scope.key]]
    prefs[scope.key] = next
    localStorage.setItem(`${COLUMN_STORE}_${scope.key}`, JSON.stringify(next))
  }
  columnsVisible.value = false
  const edited = dialogScopeLabel.value
  if (edited !== scopeLabel.value) {
    ElMessage.success(`「${edited}」字段已单独保存；当前为${scopeLabel.value}视图，切换到${edited}即可查看`)
  } else {
    ElMessage.success(`「${edited}」字段已保存，工伤与劳动仲裁互不影响`)
  }
}

function resetColumns() {
  drafts[dialogScope.value] = [...DEFAULTS[dialogScope.value]]
}

const loading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const summary = ref({})

const dialogVisible = ref(false)
const current = ref(null)
const dialogItem = ref(null)
const dialogForm = reactive({ status: 'NOT_APPLIED', amount: 0 })

function recognizeClass(code) {
  if (code === 'RECOGNIZED') return 'ok'
  if (code === 'NOT_RECOGNIZED') return 'danger'
  if (code === 'DECLARING') return 'warn'
  return 'neutral'
}

function subsidyClass(code) {
  if (code === 'RECEIVED') return 'ok'
  if (code === 'APPLYING') return 'warn'
  if (code === 'INELIGIBLE') return 'danger'
  return 'neutral'
}

function subsidyName(code) {
  return SUBSIDY_STATUS.find((s) => s.value === code)?.label || '未申领'
}

function money(value, masked) {
  if (masked) return '无权限'
  const n = Number(value || 0)
  return n ? `￥${n.toLocaleString()}` : '—'
}

function subsidyItem(columnKey) {
  return SUBSIDY_ITEMS.find((i) => i.column === columnKey)
}

function subsidyField(columnKey, part) {
  const item = subsidyItem(columnKey)
  if (!item) return part === 'status' ? 'subsidyDisabilityStatus' : 'subsidyDisabilityAmount'
  return part === 'status' ? item.statusField : item.amountField
}

function isDash(row, key) {
  return cellText(row, key) === '—'
}

/** 普通字段的展示文案，与后端导出取值保持一致 */
function cellText(row, key) {
  const injury = row.caseType === 'INJURY'
  const blank = (v) => (v === null || v === undefined || v === '' ? '—' : v)
  switch (key) {
    case 'caseNo':
      return blank(row.caseNo)
    case 'type':
      return row.caseTypeName
    case 'plantArea':
      return blank(row.plantArea)
    case 'workshopTeam':
      return blank(row.workshopTeam || row.departmentName)
    case 'employeeName':
      return blank(row.employeeName)
    case 'leaderL1':
      return blank(row.leaderL1)
    case 'leaderL2':
      return blank(row.leaderL2)
    case 'arbitrationNo':
      return injury ? '—' : blank(row.arbitrationNo)
    case 'applyDate':
      return injury ? '—' : blank(row.applyDate)
    case 'acceptDate':
      return injury ? '—' : blank(row.acceptDate)
    case 'hearingDate':
      return injury ? '—' : blank(row.hearingDate)
    case 'resultType':
      return blank(row.resultTypeName)
    case 'awardAmount':
      if (injury) return '—'
      return row.amountMasked ? '无权限' : money(row.awardAmount)
    case 'occurDate':
      return blank(row.occurDate)
    case 'description':
      return blank(row.description)
    case 'restDays':
      return injury ? (row.restDays === null || row.restDays === undefined ? '待登记' : `${row.restDays} 天`) : '—'
    case 'performAmount':
      if (injury) return '—'
      return row.performAmount === null || row.performAmount === undefined
        ? '待登记' : money(row.performAmount, row.amountMasked)
    case 'performStatus':
      return injury ? '—' : blank(row.performStatusName)
    case 'handlerName':
      return blank(row.handlerName)
    case 'statusName':
      return blank(row.statusName)
    case 'closeDate':
      return blank(row.closeDate)
    default:
      return '—'
  }
}

async function load() {
  loading.value = true
  try {
    const [page, sum] = await Promise.all([ledgerApi.page({ ...query }), ledgerApi.summary()])
    list.value = page.list || []
    total.value = page.total || 0
    summary.value = sum || {}
  } finally {
    loading.value = false
  }
}

function setType(type) {
  query.caseType = type
  if (type === 'ARBITRATION') {
    query.recognizeResult = ''
    query.subsidyStatus = ''
  }
  reload()
}

/** 双击行直接打开案件详情，与案件列表 / 归档库的整行进详情保持一致 */
function openRow(row) {
  if (row && row.id) {
    openCaseDetail(row.id, '/ledger')
  }
}

function reload() {
  query.pageNum = 1
  load()
}

function reset() {
  query.keyword = ''
  query.caseType = ''
  query.plantArea = ''
  query.recognizeResult = ''
  query.subsidyStatus = ''
  reload()
}

function openSubsidy(row, item) {
  current.value = row
  dialogItem.value = item
  dialogForm.status = row[item.statusField] || 'NOT_APPLIED'
  dialogForm.amount = Number(row[item.amountField] || 0)
  dialogVisible.value = true
}

async function saveSubsidy() {
  saving.value = true
  try {
    await ledgerApi.updateSubsidy(current.value.id, {
      key: dialogItem.value.key,
      status: dialogForm.status,
      amount: dialogForm.amount
    })
    ElMessage.success('申领进度已更新')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

/** 导出台账：字段与顺序取当前表格显示列 */
async function exportExcel() {
  exporting.value = true
  try {
    const response = await ledgerApi.exportLedger({
      ...query,
      pageNum: 1,
      pageSize: 100,
      columns: visibleKeys.value.join(',')
    })
    const disposition = response.headers['content-disposition'] || ''
    let filename = `台账_${new Date().toISOString().slice(0, 10)}.xlsx`
    const match = disposition.match(/filename\*=UTF-8''(.+)$/)
    if (match) {
      filename = decodeURIComponent(match[1])
    }
    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success(`导出成功：共 ${visibleKeys.value.length} 个字段与当前表格一致`)
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  load()
  systemApi
    .dictByType('plant_area')
    .then((list) => {
      areas.value = list || []
    })
    .catch(() => {
      areas.value = []
    })
})
</script>

<style scoped>
.tip {
  font-size: 12px;
  color: var(--gsms-text-3);
}

.scope-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: #f7f9fd;
  border: 1px solid var(--gsms-line-2);
  border-radius: 8px;
}

.scope-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--gsms-text);
}

.scope-hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--gsms-text-3);
}

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

.team-cell {
  display: block;
  line-height: 1.45;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.date-line {
  font-size: 11.5px;
  white-space: nowrap;
}

.cause {
  line-height: 1.5;
  white-space: normal;
}

/* 案件描述可能较长：最多两行，超出省略，悬停看全文 */
.cause.desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.amount {
  font-size: 11.5px;
  color: var(--gsms-text-3);
  margin-top: 2px;
  font-variant-numeric: tabular-nums;
}

.stat-chip {
  cursor: pointer;
  user-select: none;
}

.stat-chip:hover {
  filter: brightness(0.96);
}

.link {
  color: var(--gsms-primary);
  cursor: pointer;
  white-space: nowrap;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
}

.dlg-case {
  font-size: 12.8px;
  color: var(--gsms-text-sub);
  background: #f7f9fd;
  border: 1px solid var(--gsms-line-2);
  border-radius: 9px;
  padding: 10px 12px;
  margin-bottom: 14px;
}

.dlg-tip {
  font-size: 11.8px;
  color: var(--gsms-text-3);
  line-height: 1.6;
}

.col-group {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px 10px;
  max-height: 320px;
  overflow-y: auto;
}

.col-group :deep(.el-checkbox) {
  margin-right: 0;
  height: 26px;
}

/* ---------------- 窄屏（工具栏折叠样式见全局 index.css） ---------------- */

/* 卡片列表只在窄屏显示（须写在媒体查询之前，否则会覆盖其中的 display:block） */
.m-led {
  display: none;
}

@media (max-width: 768px) {
  /* 四张统计卡各占一行太占屏，改为 2×2 */
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
    margin-bottom: 10px;
  }

  .stat-card {
    padding: 10px 12px;
  }

  .stat-card .label {
    font-size: 11px;
  }

  .stat-card .value {
    font-size: 21px;
  }

  .stat-card .hint {
    margin-top: 1px;
    font-size: 10px;
    line-height: 1.4;
  }

  .toolbar {
    gap: 8px;
    padding: 10px 12px;
  }

  .d-table {
    display: none;
  }

  .m-led {
    display: block;
  }

  .pager {
    justify-content: center;
    padding: 10px 8px;
  }

  .col-group {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* ---------------- 台账卡片 ---------------- */
.m-led-card {
  padding: 12px 14px;
  border-bottom: 1px solid var(--gsms-line-2);
  cursor: pointer;
}

.m-led-card:last-child {
  border-bottom: 0;
}

.m-l1 {
  display: flex;
  align-items: center;
  gap: 6px;
}

.m-seq {
  margin-left: auto;
  font-size: 11px;
}

.m-l2 {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 7px;
  font-size: 14px;
}

.m-org {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: right;
}

.m-l3 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 7px;
  flex-wrap: wrap;
}

/* 三项待遇各占一行：名称 → 状态 → 金额，点标签可更新 */
.m-subsidy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
}

.m-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border: 1px solid var(--gsms-line-2);
  border-radius: 8px;
  background: #fbfcff;
}

.m-sub-label {
  flex: 0 0 44px;
  font-size: 11.5px;
  color: var(--gsms-text-3);
}

.m-sub .amount {
  margin-top: 0;
  margin-left: auto;
}

.m-extra {
  margin-top: 8px;
}

.m-kv-list {
  display: grid;
  gap: 6px;
  padding: 8px 10px;
  background: #f8fafd;
  border-radius: 8px;
}

.m-kv {
  display: flex;
  gap: 10px;
  font-size: 12.5px;
  line-height: 1.5;
}

.m-k {
  flex: 0 0 92px;
  color: var(--gsms-text-3);
}

.m-v {
  flex: 1;
  min-width: 0;
  word-break: break-all;
}

.m-more {
  width: 100%;
  margin-top: 6px;
  padding: 6px;
  border: 1px dashed var(--gsms-line-2);
  border-radius: 8px;
  background: #fff;
  color: var(--gsms-primary);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
}
</style>
