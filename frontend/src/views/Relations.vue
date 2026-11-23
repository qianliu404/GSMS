<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">争议关联</h2>
      </div>
      <div class="head-actions">
        <el-button v-if="hasPerm('relation:export')" :icon="Download" :loading="exporting" @click="onExport">导出清单</el-button>
        <el-button v-if="hasPerm('relation:create')" type="primary" :icon="Plus" @click="openAdd">从案件添加关联</el-button>
      </div>
    </div>

    <div class="stat-grid three">
      <div class="stat-card accent">
        <div class="label">关联关系</div>
        <div class="value">{{ stats.relationCount }}</div>
      </div>
      <div class="stat-card warn">
        <div class="label">进行中关联</div>
        <div class="value">{{ stats.activeCount }}</div>
      </div>
      <div class="stat-card ok">
        <div class="label">关联当事人</div>
        <div class="value">{{ stats.employeeCount }}</div>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-select
          v-model="query.caseType"
          placeholder="全部案件类型"
          clearable
          style="width: 156px"
          @change="reload"
        >
          <el-option label="工伤案件" value="INJURY" />
          <el-option label="劳动仲裁案件" value="ARBITRATION" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索案号 / 当事人"
          clearable
          :prefix-icon="Search"
          style="width: 240px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button @click="reload">查询</el-button>
        <div class="spacer"></div>
        <span class="total-tip">共 {{ total }} 组关联</span>
      </div>

      <div class="table-wrap">
        <el-table v-loading="loading" :data="rows" class="rel-table">
          <el-table-column label="主案案号" width="150">
            <template #default="{ row }">
              <div class="no-cell">
                <a class="case-link" @click="openCase(row.mainCaseId)">{{ row.mainCaseNo }}</a>
                <span class="tag" :class="typeClass(row.mainCaseType)">{{ typeText(row.mainCaseType) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="mainEmployee" label="主案当事人" width="104" />
          <el-table-column prop="mainStatusName" label="主案阶段" width="110" />
          <el-table-column label="关联案案号" width="150">
            <template #default="{ row }">
              <div class="no-cell">
                <a class="case-link" @click="openCase(row.subCaseId)">{{ row.subCaseNo }}</a>
                <span class="tag" :class="typeClass(row.subCaseType)">{{ typeText(row.subCaseType) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="subEmployee" label="关联案当事人" width="112" />
          <el-table-column prop="subStatusName" label="关联案阶段" width="110" />
          <el-table-column label="关联说明" min-width="200">
            <template #default="{ row }">
              <div class="desc" :title="row.relationDesc || ''">{{ row.relationDesc || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="建立日期" width="120">
            <template #default="{ row }">{{ (row.createTime || '').slice(0, 10) || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="116" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCase(row.mainCaseId)">查看</el-button>
              <el-button v-if="hasPerm('relation:delete')" link type="danger" @click="onRemove(row)">解除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="rel-cards">
        <div v-for="row in rows" :key="row.id" class="rel-card">
          <div class="rc-line">
            <span class="rc-role">主案</span>
            <span class="tag" :class="typeClass(row.mainCaseType)">{{ typeText(row.mainCaseType) }}</span>
            <a class="case-link" @click="openCase(row.mainCaseId)">{{ row.mainCaseNo }}</a>
            <span class="rc-name">{{ row.mainEmployee || '—' }}</span>
            <span class="rc-status">{{ row.mainStatusName }}</span>
          </div>
          <div class="rc-line">
            <span class="rc-role">关联</span>
            <span class="tag" :class="typeClass(row.subCaseType)">{{ typeText(row.subCaseType) }}</span>
            <a class="case-link" @click="openCase(row.subCaseId)">{{ row.subCaseNo }}</a>
            <span class="rc-name">{{ row.subEmployee || '—' }}</span>
            <span class="rc-status">{{ row.subStatusName }}</span>
          </div>
          <div class="rc-desc">{{ row.relationDesc || '—' }}</div>
          <div class="rc-foot">
            <span class="rc-date">{{ (row.createTime || '').slice(0, 10) }}</span>
            <div>
              <el-button link type="primary" @click="openCase(row.mainCaseId)">查看</el-button>
              <el-button v-if="hasPerm('relation:delete')" link type="danger" @click="onRemove(row)">解除</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          :layout="isNarrow ? 'prev, pager, next' : 'total, sizes, prev, pager, next'"
          :pager-count="isNarrow ? 5 : 7"
          @size-change="reload"
          @current-change="load"
        />
      </div>
    </div>

    <!-- 从案件添加关联 -->
    <el-dialog v-model="addVisible" title="从案件添加关联" :width="isNarrow ? '92%' : '520px'">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="92px">
        <el-form-item label="主案" prop="mainCaseNo">
          <el-select
            v-model="addForm.mainCaseNo"
            filterable
            remote
            clearable
            :remote-method="(kw) => searchCases('main', kw)"
            :loading="mainLoading"
            placeholder="输入案号 / 当事人搜索"
            style="width: 100%"
          >
            <el-option v-for="c in mainOptions" :key="c.caseNo" :value="c.caseNo" :label="optionLabel(c)" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联案" prop="relatedCaseNo">
          <el-select
            v-model="addForm.relatedCaseNo"
            filterable
            remote
            clearable
            :remote-method="(kw) => searchCases('sub', kw)"
            :loading="subLoading"
            placeholder="输入案号 / 当事人搜索"
            style="width: 100%"
          >
            <el-option v-for="c in subOptions" :key="c.caseNo" :value="c.caseNo" :label="optionLabel(c)" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联说明">
          <el-input
            v-model="addForm.relationDesc"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="如：员工不服工伤认定结论提起仲裁，两案合并取证"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAdd">建立关联</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus, Search } from '@element-plus/icons-vue'
import { caseApi, relationApi } from '../api/modules'
import { useUserStore } from '../store/user'
import { useCaseNav } from '../composables/useCaseNav'

/** 进案件详情时带上来源，返回时仍回到关联案件列表 */
const hasPerm = (code) => useUserStore().hasPerm(code)
const { openCaseDetail } = useCaseNav()

const loading = ref(false)
const exporting = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const stats = reactive({ relationCount: 0, activeCount: 0, employeeCount: 0 })
const query = reactive({ caseType: '', keyword: '', pageNum: 1, pageSize: 20 })

const narrowQuery = window.matchMedia('(max-width: 768px)')
const isNarrow = ref(narrowQuery.matches)
narrowQuery.addEventListener('change', (e) => (isNarrow.value = e.matches))

const addVisible = ref(false)
const addFormRef = ref()
const addForm = reactive({ mainCaseNo: '', relatedCaseNo: '', relationDesc: '' })
const addRules = {
  mainCaseNo: [{ required: true, message: '请选择主案', trigger: 'change' }],
  relatedCaseNo: [{ required: true, message: '请选择关联案', trigger: 'change' }]
}
const mainOptions = ref([])
const subOptions = ref([])
const mainLoading = ref(false)
const subLoading = ref(false)

function typeText(type) {
  if (type === 'INJURY') return '工伤'
  if (type === 'ARBITRATION') return '仲裁'
  return '其他'
}

function typeClass(type) {
  if (type === 'INJURY') return 'injury'
  if (type === 'ARBITRATION') return 'arb'
  return 'neutral'
}

function optionLabel(c) {
  return `${c.caseNo} · ${c.employeeName || '未填姓名'}（${c.caseTypeName} · ${c.statusName}）`
}

async function load() {
  loading.value = true
  try {
    const data = await relationApi.page({
      caseType: query.caseType || undefined,
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    rows.value = data.page.list || []
    total.value = data.page.total || 0
    stats.relationCount = data.relationCount
    stats.activeCount = data.activeCount
    stats.employeeCount = data.employeeCount
  } finally {
    loading.value = false
  }
}

function reload() {
  query.pageNum = 1
  load()
}

function openCase(id) {
  if (id) openCaseDetail(id, '/relations')
}

async function onRemove(row) {
  await ElMessageBox.confirm(
    `确认解除「${row.mainCaseNo}」与「${row.subCaseNo}」的关联？解除后两侧案件的关联列表都会移除。`,
    '解除关联',
    { type: 'warning', confirmButtonText: '解除', cancelButtonText: '取消' }
  )
  await relationApi.remove(row.id)
  ElMessage.success('关联已解除')
  load()
}

let mainTimer = null
let subTimer = null
function searchCases(target, keyword) {
  const isMain = target === 'main'
  clearTimeout(isMain ? mainTimer : subTimer)
  const timer = setTimeout(async () => {
    if (isMain) mainLoading.value = true
    else subLoading.value = true
    try {
      const list = (await caseApi.options({ keyword: keyword || '', limit: 20 })) || []
      if (isMain) mainOptions.value = list
      else subOptions.value = list
    } catch (e) {
      if (isMain) mainOptions.value = []
      else subOptions.value = []
    } finally {
      if (isMain) mainLoading.value = false
      else subLoading.value = false
    }
  }, 250)
  if (isMain) mainTimer = timer
  else subTimer = timer
}

function openAdd() {
  addForm.mainCaseNo = ''
  addForm.relatedCaseNo = ''
  addForm.relationDesc = ''
  mainOptions.value = []
  subOptions.value = []
  addVisible.value = true
  searchCases('main', '')
  searchCases('sub', '')
}

async function submitAdd() {
  const valid = await addFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (addForm.mainCaseNo === addForm.relatedCaseNo) {
    ElMessage.warning('主案与关联案不能是同一件案件')
    return
  }
  saving.value = true
  try {
    await relationApi.create({ ...addForm })
    ElMessage.success('关联已建立')
    addVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function onExport() {
  exporting.value = true
  try {
    const response = await relationApi.exportList({
      caseType: query.caseType || undefined,
      keyword: query.keyword || undefined
    })
    const disposition = response.headers['content-disposition'] || ''
    let filename = `争议关联清单_${new Date().toISOString().slice(0, 10)}.xlsx`
    const match = disposition.match(/filename\*=UTF-8''(.+)$/)
    if (match) filename = decodeURIComponent(match[1])
    const url = URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('清单已导出')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-actions {
  display: flex;
  gap: 8px;
}

.stat-grid.three {
  grid-template-columns: repeat(3, 1fr);
}

.total-tip {
  font-size: 12px;
  color: var(--gsms-text-3);
}

.table-wrap {
  overflow-x: auto;
}

/* 移动端用卡片替代宽表，避免只能看到前两列 */
.rel-cards {
  display: none;
}

.rel-card {
  padding: 12px 14px;
  border-bottom: 1px solid var(--gsms-line-2);
}

.rel-card:last-child {
  border-bottom: 0;
}

.rc-line {
  display: flex;
  align-items: center;
  gap: 6px;
  line-height: 22px;
}

.rc-role {
  font-size: 11px;
  color: var(--gsms-text-3);
}

.rc-name {
  color: var(--gsms-text-sub);
}

.rc-status {
  margin-left: auto;
  font-size: 12px;
  color: var(--gsms-text-3);
}

.rc-desc {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--gsms-text-sub);
}

.rc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.rc-date {
  font-size: 12px;
  color: var(--gsms-text-3);
}

.rel-table {
  width: 100%;
}

.no-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.case-link {
  color: var(--gsms-primary);
  cursor: pointer;
  font-variant-numeric: tabular-nums;
}

.case-link:hover {
  text-decoration: underline;
}

.tag {
  padding: 0 5px;
  font-size: 10.5px;
  line-height: 17px;
  border-radius: 6px;
  white-space: nowrap;
}

.tag.injury {
  color: var(--gsms-injury);
  background: var(--gsms-injury-soft);
}

.tag.arb {
  color: var(--gsms-arb);
  background: var(--gsms-arb-soft);
}

.tag.neutral {
  color: var(--gsms-text-3);
  background: #f1f4f9;
}

.desc {
  line-height: 1.6;
  color: var(--gsms-text-sub);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--gsms-line-2);
}

@media (max-width: 768px) {
  .page-head {
    align-items: flex-start;
  }

  .head-actions {
    width: 100%;
  }

  .head-actions .el-button {
    flex: 1 1 0;
    margin-left: 0;
  }

  .stat-grid.three {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-grid.three .stat-card:last-child {
    grid-column: 1 / -1;
  }

  .rel-table,
  .table-wrap {
    display: none;
  }

  .rel-cards {
    display: block;
  }

  .toolbar {
    padding: 10px 12px;
  }

  .toolbar .el-select,
  .toolbar .el-input {
    width: 100% !important;
  }

  .toolbar .el-button {
    flex: 1 1 0;
  }

  .pager {
    justify-content: center;
    padding: 10px;
  }
}
</style>
