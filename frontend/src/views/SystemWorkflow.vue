<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h2 class="page-title">流程节点配置</h2>
      </div>
      <div class="head-actions">
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div class="card rule-card">
      <div class="card-body rule-body">
        <b>节点变更规则</b>
        · 新增：填节点名称与插入位置即可，系统自动接在前驱与原后继之间，状态码自动生成
        · 删除：首末节点固定，且节点上无在办案件时才可删除；删除后前驱自动接续其后继
        · 编辑：仅改流程定义，不改历史流转记录；后继需同流程、不能指向自身或成环
        · 调序：↑↓ 交换相邻节点，首末节点固定；只影响展示顺序，不改变流转关系
        · 停用：停用后不再作为流转目标；有在办案件、或会使前驱无出路时不可停用
        · 分支：节点可配置多个后继（如裁决 → 履行 / 上诉），留空沿用内置流程
        · 状态码：系统优先取名称中的英文/数字，纯中文节点按 NODE_序号 生成，创建后不可修改
      </div>
    </div>

    <div class="card" v-for="flow in FLOWS" :key="flow.key">
      <div class="card-head">
        <h2>{{ flow.name }} · 节点定义</h2>
        <span class="pill">{{ nodesOf(flow.key).length }} 个节点</span>
        <el-button size="small" style="margin-left: 8px" @click="openForm(flow.key, null)">
          + 新增节点
        </el-button>
      </div>
      <!-- 流程图：直观展示节点顺序与分支走向，点击节点直接编辑 -->
      <div v-if="stagesOf(flow.key).length" class="flow-wrap">
        <FlowStrip
          :stages="stagesOf(flow.key)"
          @pick="(code) => onPickNode(flow.key, code)"
        />
        <div class="flow-hint">点击节点可编辑；虚线框为分支，同一节点配置多个后继即从这里分出多条路径</div>
      </div>

      <el-table :data="nodesOf(flow.key)" style="width: 100%" :row-class-name="rowClass">
        <el-table-column label="序号" width="58">
          <template #default="{ $index }">
            <span class="cfg-no">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="节点名称" width="132">
          <template #default="{ row }">
            <span class="cfg-name link" @click="openForm(flow.key, row)">{{ row.nodeName }}</span>
            <span v-if="isEdge(row)" class="mini">系统</span>
          </template>
        </el-table-column>
        <el-table-column label="状态码" width="128">
          <template #default="{ row }">
            <span class="text-mono mini">{{ row.statusCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="办理时限" width="92">
          <template #default="{ row }">{{ row.timeLimit || '—' }}</template>
        </el-table-column>
        <el-table-column label="主责角色" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.mainRole || '—' }}</template>
        </el-table-column>
        <el-table-column label="关键材料 / 输出文书" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.keyMaterial || '—' }}</template>
        </el-table-column>
        <el-table-column label="系统动作" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mini">{{ row.sysAction || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="后继节点" width="176">
          <template #default="{ row }">
            <template v-if="row.nextCodes">
              <span v-for="c in row.nextCodes.split(',')" :key="c" class="tag neutral" style="margin: 0 4px 2px 0">
                {{ statusName(c) }}
              </span>
            </template>
            <span v-else class="mini text-sub">按内置流程</span>
          </template>
        </el-table-column>
        <el-table-column label="回流" width="84">
          <template #default="{ row }">
            <span v-if="row.allowRollback === 1" class="tag ok">可退回</span>
            <span v-else class="tag neutral">禁止退回</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="78">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              :disabled="isEdge(row) || togglingId === row.id"
              size="small"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              @change="(val) => toggleEnabled(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="168" fixed="right">
          <template #default="{ row }">
            <div class="acts">
              <el-button link size="small" :disabled="isFirstRow(row)" @click="move(row, -1)">↑</el-button>
              <el-button link size="small" :disabled="isLastRow(row)" @click="move(row, 1)">↓</el-button>
              <el-button link type="primary" size="small" @click="openForm(flow.key, row)">编辑</el-button>
              <el-button
                link
                type="danger"
                size="small"
                :disabled="isEdge(row)"
                @click="remove(row)"
              >删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="节点名称" prop="nodeName">
              <el-input v-model="form.nodeName" placeholder="如：伤情复核" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="办理时限">
              <el-input v-model="form.timeLimit" placeholder="如：10 天" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态码">
          <div class="code-box">
            <span v-if="editingId" class="text-mono mini">{{ form.statusCode }}</span>
            <span v-else class="code-auto">保存时按节点名称自动生成</span>
          </div>
          <div class="dlg-hint" style="margin-top: 4px">
            {{ editingId
              ? '状态码是系统内部标识，创建后不可修改'
              : '系统优先提取名称中的英文/数字（如「Review 复核」→ REVIEW），纯中文节点按 NODE_序号 生成' }}
          </div>
        </el-form-item>
        <el-form-item label="后继节点（分支）">
          <el-select v-model="form.nextCodes" multiple clearable style="width: 100%" placeholder="留空则沿用内置流程">
            <el-option
              v-for="c in chainOf(form.flowKey).filter((x) => x !== form.statusCode)"
              :key="c"
              :label="`${statusName(c)}（${c}）`"
              :value="c"
            />
          </el-select>
          <div class="dlg-hint" style="margin-top: 4px">
            可多选：选中多个即为分支节点，例如「裁决」同时指向「履行」与「上诉」
          </div>
        </el-form-item>
        <el-form-item label="主责角色">
          <el-input v-model="form.mainRole" placeholder="如：法务专员 / 业务部门" />
        </el-form-item>
        <el-form-item label="关键材料 / 输出文书">
          <el-input v-model="form.keyMaterial" placeholder="如：现场照片、证人证言" />
        </el-form-item>
        <el-form-item label="系统动作">
          <el-input v-model="form.sysAction" placeholder="如：生成待办、启动节点计时" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="插入位置">
          <el-select v-model="form.insertAfter" style="width: 100%">
            <el-option
              v-for="(node, i) in insertOptions(form.flowKey)"
              :key="node.id"
              :label="`在「${node.nodeName}」之后`"
              :value="i + 1"
            />
          </el-select>
          <div class="dlg-hint" style="margin-top: 4px">
            新节点会自动接在前驱与原后继之间，默认插在归档之前
          </div>
        </el-form-item>
        <el-checkbox v-model="form.allowRollback">允许从本节点退回上一节点</el-checkbox>
      </el-form>
      <template #footer>
        <span class="dlg-tip">{{ editingId ? '修改仅影响流程定义，不改动历史记录' : '新增节点对现有在办案件即时生效' }}</span>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          {{ editingId ? '保存修改' : '新增节点' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workflowApi } from '../api/modules'
import FlowStrip from '../components/FlowStrip.vue'

const FLOWS = [
  { key: 'INJURY', name: '工伤案件' },
  { key: 'ARBITRATION', name: '劳动仲裁案件' }
]

const STATUS_NAME = {
  DRAFT: '草稿',
  FILED: '立案',
  INVESTIGATION: '调查取证',
  DECLARE: '申报',
  RECOGNITION: '认定',
  APPRAISAL: '鉴定',
  TREATMENT: '待遇处理',
  DISPUTE: '争议处理',
  EVIDENCE: '证据准备',
  HEARING: '开庭',
  AWARD: '裁决',
  MEDIATION: '调解',
  PERFORM: '履行',
  APPEAL: '上诉',
  RETRIAL_HEARING: '开庭（二审）',
  RULING: '裁定',
  CLOSED: '结案',
  ARCHIVED: '归档'
}

const CHAINS = {
  INJURY: ['DRAFT', 'FILED', 'INVESTIGATION', 'DECLARE', 'RECOGNITION', 'APPRAISAL', 'TREATMENT', 'DISPUTE', 'CLOSED', 'ARCHIVED'],
  ARBITRATION: ['DRAFT', 'FILED', 'EVIDENCE', 'HEARING', 'AWARD', 'APPEAL', 'RETRIAL_HEARING', 'RULING', 'PERFORM', 'CLOSED', 'ARCHIVED']
}

const loading = ref(false)
const saving = ref(false)
const togglingId = ref(null)
const nodes = ref([])
/** 各流程的节点拓扑定义（含 next），供流程图渲染 */
const defs = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()

const form = reactive({
  flowKey: 'INJURY',
  statusCode: '',
  nodeName: '',
  timeLimit: '',
  mainRole: '',
  keyMaterial: '',
  sysAction: '',
  allowRollback: true,
  nextCodes: [],
  insertAfter: 1
})

const rules = {
  nodeName: [{ required: true, message: '请填写节点名称' }]
}

const dialogTitle = computed(() => {
  const name = FLOWS.find((f) => f.key === form.flowKey)?.name || ''
  return (editingId.value ? '编辑流程节点 · ' : '新增流程节点 · ') + name
})

function nodesOf(flowKey) {
  return nodes.value.filter((n) => n.flowKey === flowKey).sort((a, b) => a.sortNo - b.sortNo)
}

/** 草稿 / 归档为系统节点：不可删除、不可移动、不可停用 */
function isFirstRow(row) {
  return row.statusCode === 'DRAFT'
}

function isLastRow(row) {
  return row.statusCode === 'ARCHIVED'
}

function isEdge(row) {
  return isFirstRow(row) || isLastRow(row)
}

function rowClass({ row }) {
  return row.enabled === 0 ? 'row-disabled' : ''
}

/** 新增节点只能插在草稿之后、归档之前 */
function insertOptions(flowKey) {
  return nodesOf(flowKey).filter((n) => n.statusCode !== 'ARCHIVED')
}

/** 可选状态码：内置链 + 节点表里已有的自定义状态（跳过已停用节点） */
function chainOf(flowKey) {
  const list = []
  for (const code of CHAINS[flowKey] || []) {
    const node = nodesOf(flowKey).find((n) => n.statusCode === code)
    if (!node || node.enabled !== 0) list.push(code)
  }
  for (const node of nodesOf(flowKey)) {
    if (node.enabled !== 0 && !list.includes(node.statusCode)) list.push(node.statusCode)
  }
  return list
}

/** 状态名：节点配置优先（支持自定义状态），其次内置字典 */
function statusName(code) {
  const node = nodes.value.find((n) => n.statusCode === code)
  return node?.nodeName || STATUS_NAME[code] || code
}

async function load() {
  loading.value = true
  try {
    const [nodeList, defList] = await Promise.all([
      workflowApi.nodes(),
      workflowApi.definitions()
    ])
    nodes.value = nodeList || []
    defs.value = defList || []
  } finally {
    loading.value = false
  }
}

/**
 * 流程图数据：结构与 /workflow/definitions 一致（拓扑已按节点配置的后继解析），
 * 补上节点表里的办理时限；配置页没有在办案件，数量位置留空。
 */
function stagesOf(flowKey) {
  const def = defs.value.find((d) => d.caseType === flowKey)
  if (!def || !def.nodes) return []
  const limitOf = new Map(nodesOf(flowKey).map((n) => [n.statusCode, n.timeLimit]))
  return def.nodes.map((n) => ({
    code: n.code,
    name: n.name,
    timeLimit: limitOf.get(n.code) || '—',
    count: 0,
    next: n.next || []
  }))
}

/** 点击流程图节点 → 打开该节点的编辑弹窗 */
function onPickNode(flowKey, code) {
  const node = nodesOf(flowKey).find((n) => n.statusCode === code)
  if (node) openForm(flowKey, node)
}

function openForm(flowKey, row) {
  editingId.value = row ? row.id : null
  Object.assign(form, {
    flowKey,
    statusCode: row ? row.statusCode : '',
    nodeName: row ? row.nodeName : '',
    timeLimit: row ? row.timeLimit || '' : '10 天',
    mainRole: row ? row.mainRole || '' : '',
    keyMaterial: row ? row.keyMaterial || '' : '',
    sysAction: row ? row.sysAction || '' : '',
    allowRollback: row ? row.allowRollback === 1 : true,
    nextCodes: row && row.nextCodes ? row.nextCodes.split(',').filter(Boolean) : [],
    insertAfter: row ? nodesOf(flowKey).length : insertOptions(flowKey).length
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form, nextCodes: (form.nextCodes || []).join(',') }
    if (editingId.value) {
      await workflowApi.updateNode(editingId.value, payload)
      ElMessage.success('节点已更新')
    } else {
      // 新增时状态码由后端按节点名称自动生成，不提交空值
      delete payload.statusCode
      await workflowApi.createNode(payload)
      ElMessage.success('节点已新增')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function move(row, direction) {
  await workflowApi.moveNode(row.id, direction)
  await load()
}

async function toggleEnabled(row, enabled) {
  if (!enabled) {
    try {
      await ElMessageBox.confirm(
        `停用「${row.nodeName}」后，案件不能再流转到该节点。若该节点有在办案件，或停用会让前驱没有可流转的下一节点，系统会拒绝。`,
        '停用流程节点',
        { type: 'warning', confirmButtonText: '确认停用', cancelButtonText: '取消' }
      )
    } catch (e) {
      return
    }
  }
  togglingId.value = row.id
  try {
    await workflowApi.setNodeEnabled(row.id, enabled)
    ElMessage.success(enabled ? '节点已启用' : '节点已停用')
    await load()
  } finally {
    togglingId.value = null
  }
}

async function remove(row) {
  await ElMessageBox.confirm(
    `即将删除节点「${row.nodeName}」。删除后前驱会自动接续它的后继，其他节点中指向它的后继会被一并摘除，历史流转记录保留。`,
    '删除流程节点',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )
  await workflowApi.deleteNode(row.id)
  ElMessage.success('节点已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
/* 状态码只读展示：改用系统生成后不再提供输入框 */
.code-box {
  display: flex;
  align-items: center;
  min-height: 32px;
  padding: 0 11px;
  border: 1px dashed var(--gsms-line-2);
  border-radius: 6px;
  background: #f7f9fd;
}

.code-auto {
  font-size: 12.4px;
  color: var(--gsms-text-3);
}

.rule-card {
  border-style: dashed;
  box-shadow: none;
  background: #fbfcff;
}

.rule-body {
  padding: 12px 16px;
  font-size: 12.4px;
  color: var(--gsms-text-sub);
  line-height: 1.7;
}

.cfg-no {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 5px;
  background: #eef2fa;
  color: var(--gsms-text-sub);
  font-size: 11px;
  font-weight: 700;
}

.cfg-name {
  font-weight: 600;
}

.mini {
  font-size: 11.5px;
  color: var(--gsms-text-3);
  margin-left: 3px;
}

.link {
  cursor: pointer;
}

.link:hover {
  color: var(--gsms-primary);
  text-decoration: underline dotted;
}

.acts {
  display: flex;
  gap: 2px;
  align-items: center;
}

.dlg-tip {
  float: left;
  font-size: 11.8px;
  color: var(--gsms-text-3);
  line-height: 32px;
}

/* 流程图区块：表格上方独立留白，窄屏横向滑动 */
.flow-wrap {
  padding: 16px 16px 10px;
  border-bottom: 1px solid var(--gsms-line-2);
  background: #fbfcff;
  overflow-x: auto;
}

.flow-hint {
  margin-top: 8px;
  font-size: 11.8px;
  color: var(--gsms-text-3);
}

/* 停用节点整行降透明度，避免误以为是可流转节点 */
.row-disabled {
  opacity: 0.55;
}
</style>
