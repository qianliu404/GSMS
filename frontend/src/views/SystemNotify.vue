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
    <el-drawer v-model="drawer" :title="editing ? '编辑推送配置' : '新建推送配置'" size="560px" destroy-on-close>
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

          <el-form-item label="提前天数">
            <el-input-number v-model="form.aheadDays" :min="1" :max="365" style="width: 140px" @change="doPreview" />
            <span class="hint">距截止不足 N 天纳入</span>
          </el-form-item>

          <el-form-item label="已超期案件">
            <el-switch v-model="overdueBool" active-text="包含" inactive-text="不包含" @change="doPreview" />
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
          <div class="preview-box">{{ previewContent || '点击「刷新预览」查看消息效果' }}</div>
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
      <div class="preview-box dialog">{{ previewContent || '暂无内容' }}</div>
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
import { ref, computed, watch, onMounted } from 'vue'
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
const previewContent = ref('')
const previewRows = ref([])
const allFields = ref([])

const weekOptions = [
  { value: 1, label: '周一' }, { value: 2, label: '周二' }, { value: 3, label: '周三' },
  { value: 4, label: '周四' }, { value: 5, label: '周五' }, { value: 6, label: '周六' }, { value: 7, label: '周日' }
]
const monthOptions = Array.from({ length: 31 }, (_, i) => ({ value: i + 1, label: `${i + 1}日` }))

const blank = () => ({
  id: null, configName: '', enabled: 1, cycleType: 'DAILY', weekDays: '', monthDays: '',
  pushTime: '09:00', aheadDays: 7, includeOverdue: 1,
  fields: 'caseNo,caseName,nodeName,deadlineDate,remainDays,handlerName', webhook: '', remark: ''
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
const overdueBool = computed({
  get: () => form.value.includeOverdue === 1,
  set: v => { form.value.includeOverdue = v ? 1 : 0 }
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

function loadAll() {
  loading.value = true
  Promise.all([notifyApi.configs(), notifyApi.history()])
    .then(([c, h]) => { configs.value = c || []; history.value = h || [] })
    .catch(e => ElMessage.error(e.message || '加载失败'))
    .finally(() => { loading.value = false })
}

function openCreate() {
  editing.value = false
  form.value = blank()
  previewContent.value = ''
  previewRows.value = []
  drawer.value = true
}

function openEdit(row) {
  editing.value = true
  form.value = { ...blank(), ...row }
  drawer.value = true
  doPreview()
}

function onCycleChange() {
  if (form.value.cycleType === 'WEEKLY' && !form.value.weekDays) form.value.weekDays = '1,2,3,4,5'
  if (form.value.cycleType === 'MONTHLY' && !form.value.monthDays) form.value.monthDays = '1'
}

function doPreview() {
  previewing.value = true
  notifyApi.preview(form.value)
    .then(r => {
      previewContent.value = r?.content || ''
      previewRows.value = r?.rows || []
      allFields.value = r?.availableFields || allFields.value
    })
    .catch(e => ElMessage.error(e.message || '预览失败'))
    .finally(() => { previewing.value = false })
}

function previewSaved(row) {
  notifyApi.previewById(row.id)
    .then(r => {
      previewContent.value = r?.content || ''
      previewRows.value = r?.rows || []
      previewDialog.value = true
    })
    .catch(e => ElMessage.error(e.message || '预览失败'))
}

function save() {
  if (!form.value.configName) { ElMessage.warning('请填写配置名称'); return }
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
    .then(r => ElMessage.success(`已推送，命中 ${r?.matched || 0} 件案件`))
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
.preview-box { padding: 12px; font-size: 12.5px; line-height: 1.8; white-space: pre-wrap; word-break: break-all; min-height: 80px; background: #fff; }
.preview-box.dialog { border: 1px solid var(--gsms-border); border-radius: 6px; max-height: 420px; overflow: auto; }
.preview-meta { padding: 6px 12px; font-size: 12px; color: var(--gsms-text-sub); border-top: 1px solid var(--gsms-border); }
.drawer-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; }
.el-checkbox-group { display: flex; flex-wrap: wrap; gap: 4px 14px; }
</style>
