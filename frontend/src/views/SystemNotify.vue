<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">推送设置</h2>
        <p class="page-sub">自定义推送周期、日期与字段，提前预览消息内容，定时推送未结案临期案件至企业微信群。</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建配置</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <div class="tip">
          推送规则：未结案案件的「当前节点」期限，距截止不足设定天数时纳入推送。
          周期可设为每天 / 每周指定星期 / 每月指定日期；字段勾选后决定消息中显示哪些内容。
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <el-table :data="configs" v-loading="loading" size="small" border>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="configName" label="配置名称" min-width="140" />
          <el-table-column label="周期" width="130">
            <template #default="{ row }">{{ cycleLabel(row.cycleType) }}</template>
          </el-table-column>
          <el-table-column label="推送时间" width="90">
            <template #default="{ row }">{{ row.pushTime }}</template>
          </el-table-column>
          <el-table-column label="提前天数" width="90">
            <template #default="{ row }">{{ row.aheadDays }} 天</template>
          </el-table-column>
          <el-table-column label="已超期" width="80">
            <template #default="{ row }">
              <el-tag :type="row.includeOverdue ? 'warning' : 'info'" size="small">{{ row.includeOverdue ? '包含' : '不含' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="85">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled === 1" @change="v => toggle(row, v)" />
            </template>
          </el-table-column>
          <el-table-column label="最近推送" width="150">
            <template #default="{ row }">{{ row.lastPushTime || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="previewSaved(row)">预览</el-button>
              <el-button link type="success" :loading="testingId === row.id" @click="testPush(row)">测试</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 配置抽屉 -->
    <el-drawer v-model="drawer" :title="editing ? '编辑推送配置' : '新建推送配置'" size="720px" destroy-on-close>
      <div class="form-wrap">
        <el-form :model="form" label-width="100px" size="small">
          <el-form-item label="配置名称" required>
            <el-input v-model="form.configName" placeholder="如：临期案件每日提醒" />
          </el-form-item>

          <el-form-item label="推送周期">
            <el-radio-group v-model="form.cycleType" @change="onCycleChange">
              <el-radio-button value="DAILY">每天</el-radio-button>
              <el-radio-button value="WEEKLY">每周</el-radio-button>
              <el-radio-button value="MONTHLY">每月</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="form.cycleType === 'WEEKLY'" label="推送星期" required>
            <el-checkbox-group v-model="weekDays">
              <el-checkbox v-for="d in weekOptions" :key="d.value" :value="d.value">{{ d.label }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item v-if="form.cycleType === 'MONTHLY'" label="推送日期" required>
            <el-checkbox-group v-model="monthDays">
              <el-checkbox v-for="d in monthOptions" :key="d.value" :value="d.value">{{ d.label }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item label="推送时间">
            <el-time-picker v-model="pushTimeObj" format="HH:mm" value-format="HH:mm" placeholder="选择时间" style="width: 160px" />
          </el-form-item>

          <el-form-item label="推送条件">
            <div class="cond-builder">
              <div v-for="(c, i) in conditions" :key="i" class="cond-row">
                <span class="cond-op">{{ i === 0 ? '当' : '并且' }}</span>
                <el-select v-model="c.field" placeholder="字段" size="small" style="width: 120px" @change="onCondField(i)">
                  <el-option v-for="f in conditionFields" :key="f.code" :label="f.label" :value="f.code" />
                </el-select>
                <el-select v-model="c.op" placeholder="操作符" size="small" style="width: 108px" @change="doPreview">
                  <el-option v-for="op in opsOf(c.field)" :key="op" :label="opLabel(op)" :value="op" />
                </el-select>
                <el-select v-if="isEnum(c.field)" v-model="c.value" placeholder="值" size="small" style="width: 130px" @change="doPreview">
                  <el-option v-for="o in optionsOf(c.field)" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
                <el-input v-else-if="!noValue(c.op)" v-model="c.value" placeholder="值" size="small" style="width: 130px" @change="doPreview" />
                <span v-else class="cond-novalue">—</span>
                <el-button link type="danger" size="small" @click="removeCond(i)">删除</el-button>
              </div>
              <div class="cond-actions">
                <el-button size="small" :icon="Plus" @click="addCond">添加条件</el-button>
                <el-button size="small" @click="resetConditions">恢复默认</el-button>
                <span class="hint">多条条件同时满足才推送，字段可持续扩展</span>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="推送字段">
            <el-checkbox-group v-model="fieldList" @change="doPreview">
              <el-checkbox v-for="f in allFields" :key="f.code" :value="f.code">{{ f.label }}</el-checkbox>
            </el-checkbox-group>
            <div class="hint block">勾选的字段及顺序决定消息中的显示内容</div>
          </el-form-item>

          <el-form-item label="Webhook">
            <el-input v-model="form.webhook" placeholder="留空使用系统默认企业微信群机器人" />
          </el-form-item>

          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
          </el-form-item>

          <el-form-item label="启用">
            <el-switch v-model="enabledBool" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </el-form>

        <div class="preview-block">
          <div class="preview-head">
            <span>消息预览</span>
            <el-button size="small" :icon="View" :loading="previewing" @click="doPreview">刷新预览</el-button>
          </div>
          <div class="preview-img-wrap">
            <img v-if="previewImage" :src="previewImage" alt="推送消息预览" class="preview-img" />
            <div v-else class="preview-empty">点击「刷新预览」查看消息图片</div>
          </div>
          <div v-if="previewRows.length" class="preview-meta">当前命中 {{ previewRows.length }} 件案件</div>
        </div>

        <div class="drawer-actions">
          <el-button @click="drawer = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 预览弹窗（已保存配置） -->
    <el-dialog v-model="previewDialog" title="推送消息预览" width="620px">
      <div class="preview-img-wrap dialog">
        <img v-if="previewImage" :src="previewImage" alt="推送消息预览" class="preview-img" />
        <div v-else class="preview-empty">暂无内容</div>
      </div>
      <div v-if="previewRows.length" class="preview-meta">当前命中 {{ previewRows.length }} 件案件</div>
      <template #footer>
        <el-button @click="previewDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 推送历史 -->
    <div class="card">
      <div class="card-head"><span>推送历史</span></div>
      <div class="card-body">
        <el-table :data="history" v-loading="loading" size="small" border max-height="320">
          <el-table-column prop="pushTime" label="推送时间" width="160" />
          <el-table-column prop="configName" label="配置名称" min-width="140" />
          <el-table-column prop="matched" label="命中数" width="80" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="error" label="失败原因" min-width="180" show-overflow-tooltip />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, View } from '@element-plus/icons-vue'
import { notifyApi } from '../api/modules'

const configs = ref([])
const history = ref([])
const loading = ref(false)
const saving = ref(false)
const previewing = ref(false)
const testingId = ref(null)
const drawer = ref(false)
const editing = ref(false)
const previewDialog = ref(false)
const previewImage = ref('')
const previewRows = ref([])
const allFields = ref([])
const conditionFields = ref([])
const conditions = ref([])

const weekOptions = [
  { value: 1, label: '周一' }, { value: 2, label: '周二' }, { value: 3, label: '周三' },
  { value: 4, label: '周四' }, { value: 5, label: '周五' }, { value: 6, label: '周六' }, { value: 7, label: '周日' }
]
const monthOptions = Array.from({ length: 31 }, (_, i) => ({ value: i + 1, label: `${i + 1}日` }))
const OP_LABELS = {
  EQ: '等于', NE: '不等于', CONTAINS: '包含', IN: '属于', NOT_IN: '不属于',
  GT: '大于', GTE: '大于等于', LT: '小于', LTE: '小于等于', IS_EMPTY: '为空', NOT_EMPTY: '不为空'
}
const opLabel = op => OP_LABELS[op] || op

const blank = () => ({
  id: null, configName: '', enabled: 1, cycleType: 'DAILY', weekDays: '', monthDays: '',
  pushTime: '09:00', aheadDays: 7, includeOverdue: 1,
  fields: 'caseNo,caseName,nodeName,deadlineDate,remainDays,handlerName', conditions: '', webhook: '', remark: ''
})
const form = ref(blank())

const weekDays = computed({
  get: () => (form.value.weekDays || '').split(',').filter(Boolean).map(Number),
  set: v => { form.value.weekDays = v.join(',') }
})
const monthDays = computed({
  get: () => (form.value.monthDays || '').split(',').filter(Boolean).map(Number),
  set: v => { form.value.monthDays = v.join(',') }
})
const fieldList = computed({
  get: () => (form.value.fields || '').split(',').filter(Boolean),
  set: v => { form.value.fields = v.join(',') }
})
const enabledBool = computed({
  get: () => form.value.enabled === 1,
  set: v => { form.value.enabled = v ? 1 : 0 }
})
const pushTimeObj = computed({
  get: () => form.value.pushTime || '09:00',
  set: v => { form.value.pushTime = typeof v === 'string' ? v : (v ? `${String(v.getHours()).padStart(2,'0')}:${String(v.getMinutes()).padStart(2,'0')}` : '09:00') }
})

const cycleLabel = t => ({ DAILY: '每天', WEEKLY: '每周', MONTHLY: '每月' }[t] || t)

function metaOf(code) { return conditionFields.value.find(f => f.code === code) }
function opsOf(code) { const f = metaOf(code); return f ? f.operators : [] }
function isEnum(code) { const f = metaOf(code); return f ? f.type === 'ENUM' : false }
function optionsOf(code) { const f = metaOf(code); return f ? f.options : [] }
function noValue(op) { return op === 'IS_EMPTY' || op === 'NOT_EMPTY' }

function conditionsToForm() {
  const raw = form.value.conditions
  if (raw && raw.trim()) {
    try { conditions.value = JSON.parse(raw) } catch (e) { conditions.value = [] }
  } else {
    conditions.value = defaultConds.value.length ? JSON.parse(JSON.stringify(defaultConds.value)) : []
  }
}
function formToConditions() { form.value.conditions = JSON.stringify(conditions.value) }

function addCond() {
  const first = conditionFields.value[0]
  if (!first) return
  conditions.value.push({ field: first.code, op: first.operators[0], value: '', values: [] })
}
function removeCond(i) { conditions.value.splice(i, 1); doPreview() }
function onCondField(i) {
  const f = metaOf(conditions.value[i].field)
  conditions.value[i].op = f ? f.operators[0] : 'EQ'
  conditions.value[i].value = ''
  doPreview()
}
function resetConditions() {
  conditions.value = JSON.parse(JSON.stringify(defaultConds.value))
  doPreview()
}

const defaultConds = ref([])

function loadAll() {
  loading.value = true
  Promise.all([notifyApi.configs(), notifyApi.history(), notifyApi.conditionFields(), notifyApi.defaultConditions()])
    .then(([c, h, f, d]) => {
      configs.value = c || []
      history.value = h || []
      conditionFields.value = f || []
      defaultConds.value = d || []
    })
    .catch(e => ElMessage.error(e.message || '加载失败'))
    .finally(() => { loading.value = false })
}

function openCreate() {
  editing.value = false
  form.value = blank()
  conditions.value = JSON.parse(JSON.stringify(defaultConds.value))
  previewImage.value = ''
  previewRows.value = []
  drawer.value = true
  doPreview()
}

function openEdit(row) {
  editing.value = true
  form.value = { ...blank(), ...row }
  conditionsToForm()
  drawer.value = true
  doPreview()
}

function onCycleChange() {
  if (form.value.cycleType === 'WEEKLY' && !form.value.weekDays) form.value.weekDays = '1,2,3,4,5'
  if (form.value.cycleType === 'MONTHLY' && !form.value.monthDays) form.value.monthDays = '1'
}

function previewPayload() {
  formToConditions()
  return { ...form.value }
}

function doPreview() {
  previewing.value = true
  notifyApi.preview(previewPayload())
    .then(r => {
      previewImage.value = r?.imageBase64 || ''
      previewRows.value = r?.rows || []
      allFields.value = r?.availableFields || allFields.value
    })
    .catch(e => ElMessage.error(e.message || '预览失败'))
    .finally(() => { previewing.value = false })
}

function previewSaved(row) {
  notifyApi.previewById(row.id)
    .then(r => {
      previewImage.value = r?.imageBase64 || ''
      previewRows.value = r?.rows || []
      previewDialog.value = true
    })
    .catch(e => ElMessage.error(e.message || '预览失败'))
}

function save() {
  if (!form.value.configName) { ElMessage.warning('请填写配置名称'); return }
  formToConditions()
  saving.value = true
  const req = editing.value
    ? notifyApi.update(form.value.id, form.value)
    : notifyApi.create(form.value)
  req.then(() => {
    ElMessage.success('保存成功')
    drawer.value = false
    loadAll()
  }).catch(e => ElMessage.error(e.message || '保存失败'))
    .finally(() => { saving.value = false })
}

function toggle(row, v) {
  notifyApi.toggle(row.id, v ? 1 : 0)
    .then(() => { row.enabled = v ? 1 : 0; ElMessage.success('状态已更新') })
    .catch(e => ElMessage.error(e.message || '操作失败'))
}

function testPush(row) {
  testingId.value = row.id
  notifyApi.test(row.id)
    .then(r => ElMessage.success(`已推送图片，命中 ${r?.matched || 0} 件案件`))
    .catch(e => ElMessage.error(e.message || '推送失败'))
    .finally(() => { testingId.value = null; loadAll() })
}

function remove(row) {
  ElMessageBox.confirm(`确认删除配置「${row.configName}」？`, '提示', { type: 'warning' })
    .then(() => notifyApi.remove(row.id))
    .then(() => { ElMessage.success('已删除'); loadAll() })
    .catch(() => {})
}

onMounted(loadAll)
</script>

<style scoped>
.page-sub { margin: 4px 0 0; color: var(--gsms-text-sub); font-size: 12.5px; }
.head-actions { display: flex; gap: 8px; }
.tip { color: var(--gsms-text-sub); font-size: 12.5px; line-height: 1.7; }
.card + .card { margin-top: 14px; }
.card-head { padding: 12px 16px 0; font-weight: 600; font-size: 13.5px; }
.hint { color: var(--gsms-text-sub); font-size: 12px; margin-left: 10px; }
.hint.block { display: block; margin-left: 0; margin-top: 4px; }
.form-wrap { padding: 4px 10px 20px; }
.preview-block { margin-top: 10px; border: 1px solid var(--gsms-border); border-radius: 8px; overflow: hidden; }
.preview-head { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; background: var(--gsms-bg); font-weight: 600; font-size: 13px; }
.preview-img-wrap { padding: 12px; background: #fff; display: flex; justify-content: center; align-items: center; min-height: 120px; }
.preview-img-wrap.dialog { border: 1px solid var(--gsms-border); border-radius: 6px; max-height: 480px; overflow: auto; }
.preview-img { max-width: 100%; max-height: 420px; border: 1px solid var(--gsms-border); border-radius: 4px; }
.preview-empty { color: var(--gsms-text-sub); font-size: 12.5px; padding: 24px; }
.cond-builder { width: 100%; }
.cond-row { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; flex-wrap: nowrap; }
.cond-op { width: 28px; color: var(--gsms-text-sub); font-size: 12px; flex: 0 0 28px; }
.cond-novalue { width: 130px; color: var(--gsms-text-sub); font-size: 12px; }
.cond-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 4px; }
.cond-actions .hint { margin-left: 0; }
.preview-meta { padding: 6px 12px; font-size: 12px; color: var(--gsms-text-sub); border-top: 1px solid var(--gsms-border); }
.drawer-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; }
.el-checkbox-group { display: flex; flex-wrap: wrap; gap: 4px 14px; }
</style>
