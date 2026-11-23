<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">新建案件</h2>
      </div>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      :label-width="labelWidth"
      :label-position="labelPosition"
      class="card card-pad create-form"
    >
      <el-divider content-position="left" class="type-divider">案件类型</el-divider>
      <el-form-item label="案件类型" prop="caseType">
        <el-radio-group v-model="form.caseType" class="type-group" @change="onTypeChange">
          <el-radio-button label="INJURY">工伤案件</el-radio-button>
          <el-radio-button label="ARBITRATION">劳动仲裁案件</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-divider content-position="left">基础信息</el-divider>
      <el-form-item label="关联案件">
        <el-select
          v-model="form.relatedCaseNo"
          filterable
          remote
          clearable
          :remote-method="searchRelatedCases"
          :loading="caseLoading"
          placeholder="输入案号或当事人搜索"
          style="width: 100%"
          @change="onRelatedChange"
          @visible-change="onRelatedVisible"
        >
          <el-option
            v-for="c in caseOptions"
            :key="c.caseNo"
            :value="c.caseNo"
            :label="`${c.caseNo} · ${c.employeeName || '未填姓名'}（${c.caseTypeName} · ${c.statusName}）`"
          >
            <div class="rel-opt-row">
              <span class="rel-opt-no">{{ c.caseNo }}</span>
              <span class="rel-opt-name">{{ c.employeeName || '未填姓名' }}</span>
              <span class="rel-opt-meta">{{ c.caseTypeName }} · {{ c.statusName }}</span>
            </div>
          </el-option>
          <!-- 结果被截断时提示收窄关键字，避免用户误以为只有这些案件 -->
          <el-option
            v-if="optionTruncated"
            disabled
            value="__more__"
            label="__more__"
          >
            <span class="rel-opt-more">结果较多，仅显示前 {{ RELATED_LIMIT }} 条，请输入更精确的案号或姓名</span>
          </el-option>
        </el-select>

        <!-- 选中后回显关键信息，确认带对了案件再填说明 -->
        <div v-if="relatedPicked" class="rel-picked">
          <div class="rp-head">
            <span class="rp-no">{{ relatedPicked.caseNo }}</span>
            <span class="rp-tag" :class="relatedPicked.caseType === 'INJURY' ? 'injury' : 'arb'">
              {{ relatedPicked.caseType === 'INJURY' ? '工伤' : '仲裁' }}
            </span>
            <span class="rp-stage">{{ relatedPicked.statusName }}</span>
          </div>
          <div class="rp-body">
            <span>当事人 {{ relatedPicked.employeeName || '—' }}</span>
            <span>承办人 {{ relatedPicked.handlerName || relatedPicked.handlerId || '—' }}</span>
            <span>发生日期 {{ relatedPicked.occurDate || '—' }}</span>
            <span>厂区 {{ relatedPicked.plantArea || '—' }}</span>
          </div>
          <div class="rel-hint">已带入基本信息</div>
        </div>
      </el-form-item>

      <el-form-item v-if="relatedPicked" label="关联说明">
        <el-input
          v-model="form.relationDesc"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="如：员工不服工伤认定结论提起劳动仲裁，两案合并取证"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="16">
          <el-form-item label="案件名称" prop="caseName">
            <el-input v-model="form.caseName" placeholder="如：张三车间机械伤害工伤认定" maxlength="200" show-word-limit />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="员工姓名" prop="employeeName">
            <el-input v-model="form.employeeName" placeholder="员工姓名" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="案号" prop="caseNo">
            <el-input
              v-model="form.caseNo"
              :placeholder="`留空自动生成，如 ${autoCaseNoTip}`"
              maxlength="32"
              clearable
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="身份证号">
            <el-input v-model="form.idCardNo" placeholder="选填，加密存储，仅授权角色可见" maxlength="18" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="发生日期" prop="occurDate">
            <el-date-picker v-model="form.occurDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择发生日期" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="案件描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          maxlength="5000"
          show-word-limit
          placeholder="简述事故经过或争议事实"
        />
      </el-form-item>

      <el-divider content-position="left">组织归属（与台账一致）</el-divider>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="厂区">
            <div class="area-row">
              <el-select
                v-model="form.plantArea"
                placeholder="选择或直接输入"
                filterable
                allow-create
                default-first-option
                clearable
                style="flex: 1; min-width: 0"
                @change="onAreaChange"
              >
                <el-option v-for="a in areas" :key="a.id" :label="a.dictName" :value="a.dictName" />
              </el-select>
              <el-tooltip content="厂区自定义设置" placement="top">
                <el-button :icon="Setting" @click="areaDialog = true" />
              </el-tooltip>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="车间班组">
            <el-input v-model="form.workshopTeam" placeholder="如：生产一部 · 二车间甲班" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="小班长" label-width="70px">
            <el-input v-model="form.leaderL1" placeholder="直属班组长" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="大班长" label-width="70px">
            <el-input v-model="form.leaderL2" placeholder="工序主管" maxlength="50" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">承办与协同</el-divider>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="承办人" prop="handlerId">
            <el-select v-model="form.handlerId" placeholder="选择承办人" filterable style="width: 100%">
              <el-option v-for="u in users" :key="u.userId" :label="`${u.userName}（${u.deptName || '-'}）`" :value="u.userId" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="协办人">
            <el-select v-model="form.coHandlerIds" multiple filterable placeholder="可多选" style="width: 100%">
              <el-option v-for="u in users" :key="u.userId" :label="u.userName" :value="u.userId" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="会签人">
            <el-select v-model="form.countersignerIds" multiple filterable placeholder="可多选" style="width: 100%">
              <el-option v-for="u in users" :key="u.userId" :label="u.userName" :value="u.userId" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="阅知人">
            <el-select v-model="form.readerIds" multiple filterable placeholder="可多选" style="width: 100%">
              <el-option v-for="u in users" :key="u.userId" :label="u.userName" :value="u.userId" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <template v-if="form.caseType === 'INJURY'">
        <el-divider content-position="left">工伤扩展信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="伤害日期">
              <el-date-picker v-model="form.injury.injuryDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择伤害日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="9">
            <el-form-item label="伤害地点">
              <el-input v-model="form.injury.injuryPlace" placeholder="如 冲压车间" />
            </el-form-item>
          </el-col>
          <el-col :span="9">
            <el-form-item label="待遇金额">
              <el-input-number v-model="form.injury.treatmentAmount" :min="0" :precision="2" :controls="false" style="width: 100%" placeholder="元" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="18">
            <el-form-item label="伤害原因">
              <el-input v-model="form.injury.injuryReason" placeholder="简述受伤经过与原因" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="是否争议">
              <el-switch v-model="form.injury.disputeFlag" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <template v-if="form.caseType === 'ARBITRATION'">
        <el-divider content-position="left">仲裁扩展信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="仲裁案号">
              <el-input v-model="form.arbitration.arbitrationNo" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="申请日期">
              <el-date-picker v-model="form.arbitration.applyDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择申请日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="开庭时间">
              <el-date-picker
                v-model="form.arbitration.hearingDate"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="请选择开庭时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="裁决金额">
              <el-input-number v-model="form.arbitration.awardAmount" :min="0" :precision="2" :controls="false" style="width: 100%" placeholder="元" />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <div class="actions">
        <el-button :loading="saving" @click="onSave(false)">保存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="onSave(true)">提交立案</el-button>
      </div>
    </el-form>

    <!-- 厂区自定义设置 -->
    <el-dialog v-model="areaDialog" title="厂区自定义设置" width="420px">
      <div class="area-add">
        <el-input
          v-model="newArea"
          placeholder="输入厂区名称，如 华东二厂"
          maxlength="50"
          @keyup.enter="addArea"
        />
        <el-button type="primary" :loading="areaSaving" @click="addArea">添加</el-button>
      </div>
      <div class="area-list">
        <div v-for="a in areas" :key="a.id" class="area-item">
          <span class="ai-name">{{ a.dictName }}</span>
          <el-button v-if="isAdmin" link type="danger" @click="removeArea(a)">删除</el-button>
        </div>
        <div v-if="!areas.length" class="empty">暂无厂区，请先添加</div>
      </div>
      <div class="area-tip">新增厂区会保存为公共选项</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { caseApi, systemApi } from '../api/modules'
import { useUserStore } from '../store/user'

const AREA_DICT = 'plant_area'

const router = useRouter()
const store = useUserStore()
const formRef = ref()
const saving = ref(false)
const users = ref([])

/**
 * 窄屏（手机）把标签改为上置：左侧固定标签会吃掉 110px，
 * 390px 视口下输入框只剩几十像素，几乎没法看清已填内容。
 */
const narrowQuery = window.matchMedia('(max-width: 768px)')
const isNarrow = ref(narrowQuery.matches)
const labelPosition = computed(() => (isNarrow.value ? 'top' : 'right'))
const labelWidth = computed(() => (isNarrow.value ? 'auto' : '110px'))

function onNarrowChange(e) {
  isNarrow.value = e.matches
}

const areas = ref([])
const areaDialog = ref(false)
const areaSaving = ref(false)
const newArea = ref('')
const isAdmin = computed(() => (store.roles || []).includes('ROLE_SYS_ADMIN'))

const form = reactive({
  caseType: 'INJURY',
  /** 案号：留空则由后端按「前缀+年月+4 位流水」生成 */
  caseNo: '',
  /** 关联案件案号：选填，保存后与目标案件建立争议关联 */
  relatedCaseNo: '',
  /** 关联说明：随关联关系一起保存 */
  relationDesc: '',
  caseName: '',
  employeeName: '',
  idCardNo: '',
  plantArea: '',
  workshopTeam: '',
  leaderL1: '',
  leaderL2: '',
  occurDate: '',
  description: '',
  handlerId: '',
  ownerId: '',
  coHandlerIds: [],
  countersignerIds: [],
  readerIds: [],
  injury: {
    injuryDate: '',
    injuryPlace: '',
    injuryReason: '',
    treatmentAmount: null,
    disputeFlag: 0
  },
  arbitration: {
    arbitrationNo: '',
    applyDate: '',
    hearingDate: '',
    awardAmount: null
  }
})

const caseOptions = ref([])
const caseLoading = ref(false)
const relatedPicked = ref(null)
const optionTruncated = ref(false)
let searchTimer = null
/** 请求序号：快速输入时丢弃过期响应，避免旧结果覆盖新结果 */
let searchSeq = 0
/** 最近一次输入的关键字：展开下拉时据此判断要不要预载最近案件 */
let lastKeyword = ''
const RELATED_LIMIT = 30

function searchRelatedCases(keyword) {
  const kw = (keyword || '').trim()
  lastKeyword = kw
  clearTimeout(searchTimer)
  const seq = ++searchSeq
  searchTimer = setTimeout(async () => {
    caseLoading.value = true
    try {
      const list = (await caseApi.options({ keyword: kw, limit: RELATED_LIMIT })) || []
      if (seq !== searchSeq) return
      optionTruncated.value = list.length >= RELATED_LIMIT
      // 已选案件不在本次结果里时补回去，避免输入框只剩案号
      if (relatedPicked.value && !list.some((c) => c.caseNo === relatedPicked.value.caseNo)) {
        caseOptions.value = [relatedPicked.value, ...list]
      } else {
        caseOptions.value = list
      }
    } catch (e) {
      if (seq === searchSeq) {
        caseOptions.value = []
        optionTruncated.value = false
      }
    } finally {
      if (seq === searchSeq) caseLoading.value = false
    }
  }, 250)
}

/**
 * 展开下拉时预载最近案件；已输入关键字则交给 remote-method，
 * 避免预载的空关键字结果把正在搜索的结果覆盖掉。
 */
function onRelatedVisible(visible) {
  if (!visible || lastKeyword) return
  if (caseOptions.value.length && !optionTruncated.value) return
  searchRelatedCases('')
}

/** 选中关联案件：把可复用的基本信息带入当前表单，案件名称仅在为空时给个建议值 */
function onRelatedChange(caseNo) {
  if (!caseNo || caseNo === '__more__') {
    relatedPicked.value = null
    form.relationDesc = ''
    form.relatedCaseNo = ''
    return
  }
  const picked = caseOptions.value.find((c) => c.caseNo === caseNo)
  if (!picked) return
  relatedPicked.value = picked
  form.employeeName = picked.employeeName || form.employeeName
  form.plantArea = picked.plantArea || form.plantArea
  form.workshopTeam = picked.workshopTeam || form.workshopTeam
  form.leaderL1 = picked.leaderL1 || form.leaderL1
  form.leaderL2 = picked.leaderL2 || form.leaderL2
  form.handlerId = picked.handlerId || form.handlerId
  form.occurDate = picked.occurDate || form.occurDate
  if (!form.caseName) {
    const suffix = form.caseType === 'ARBITRATION' ? '劳动争议案' : '工伤案'
    form.caseName = `${picked.employeeName || ''}${suffix}`
  }
  ElMessage.success(`已带入 ${picked.caseNo} 的基本信息`)
}

/** 各类型案号前缀，与后端编号规则保持一致，仅用于占位提示 */
const NO_PREFIX = { INJURY: 'GY', ARBITRATION: 'ZS', RELATED: 'GL' }
const autoCaseNoTip = computed(() => {
  const d = new Date()
  const ym = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}`
  return `${NO_PREFIX[form.caseType] || 'GY'}${ym}0001`
})

const rules = {
  caseType: [{ required: true, message: '请选择案件类型', trigger: 'change' }],
  caseNo: [{ max: 32, message: '案号不能超过 32 个字符', trigger: 'blur' }],
  caseName: [{ required: true, message: '请输入案件名称', trigger: 'blur' }],
  employeeName: [{ required: true, message: '请输入员工姓名', trigger: 'blur' }],
  occurDate: [{ required: true, message: '请选择发生日期', trigger: 'change' }],
  handlerId: [{ required: true, message: '请选择承办人', trigger: 'change' }]
}

function onTypeChange() {
  form.injury = { injuryDate: '', injuryPlace: '', injuryReason: '', treatmentAmount: null, disputeFlag: 0 }
  form.arbitration = { arbitrationNo: '', applyDate: '', hearingDate: '', awardAmount: null }
}

async function loadAreas() {
  try {
    areas.value = (await systemApi.dictByType(AREA_DICT)) || []
  } catch (e) {
    areas.value = []
  }
}

/** 选择框里直接输入的新厂区，自动落库为公共选项 */
async function onAreaChange(val) {
  const name = (val || '').trim()
  if (!name) return
  if (areas.value.some((a) => a.dictName === name)) return
  try {
    await systemApi.addDictItem(AREA_DICT, name)
    await loadAreas()
  } catch (e) {
    ElMessage.warning('厂区已填入本单，但保存为公共选项失败')
  }
}

async function addArea() {
  const name = newArea.value.trim()
  if (!name) {
    ElMessage.warning('请输入厂区名称')
    return
  }
  if (areas.value.some((a) => a.dictName === name)) {
    ElMessage.info('该厂区已存在')
    form.plantArea = name
    newArea.value = ''
    return
  }
  areaSaving.value = true
  try {
    await systemApi.addDictItem(AREA_DICT, name)
    await loadAreas()
    form.plantArea = name
    newArea.value = ''
    ElMessage.success('厂区已添加')
  } finally {
    areaSaving.value = false
  }
}

async function removeArea(area) {
  await ElMessageBox.confirm(`确定删除厂区「${area.dictName}」？已建档案件中的厂区不会被修改。`, '删除确认', {
    type: 'warning'
  })
  await systemApi.removeDictItem(AREA_DICT, area.id)
  if (form.plantArea === area.dictName) form.plantArea = ''
  await loadAreas()
  ElMessage.success('已删除')
}

async function onSave(submit) {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请先补全必填项')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      caseNo: (form.caseNo || '').trim(),
      relatedCaseNo: (form.relatedCaseNo || '').trim() || null,
      relationDesc: (form.relationDesc || '').trim() || null,
      submit
    }
    if (payload.caseType !== 'INJURY') payload.injury = null
    if (payload.caseType !== 'ARBITRATION') payload.arbitration = null
    const id = await caseApi.create(payload)
    ElMessage.success(submit ? '已提交立案' : '草稿已保存')
    // 立案后进入详情，返回时落到该类型的列表（工伤 / 仲裁）
    const fromList =
      payload.caseType === 'INJURY'
        ? '/cases?type=INJURY'
        : payload.caseType === 'ARBITRATION'
          ? '/cases?type=ARBITRATION'
          : '/cases'
    router.replace({ path: `/cases/${id}`, query: { from: fromList } })
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  narrowQuery.addEventListener('change', onNarrowChange)
  loadAreas()
  try {
    users.value = (await systemApi.userOptions()) || []
  } catch (e) {
    /* 选项加载失败时仍可手工填写 */
  }
})

onBeforeUnmount(() => {
  narrowQuery.removeEventListener('change', onNarrowChange)
})
</script>

<style scoped>
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--gsms-border);
  margin-top: 8px;
}

/* 关联案件选中的回填说明 */
.rel-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--gsms-text-3);
}

/* 选中关联案件后的信息回显 */
.rel-picked {
  width: 100%;
  margin-top: 6px;
  padding: 8px 10px;
  border: 1px solid var(--gsms-line-2);
  border-radius: 8px;
  background: #f8fafd;
}

.rp-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
}

.rp-no {
  font-variant-numeric: tabular-nums;
}

.rp-tag {
  padding: 0 5px;
  font-size: 10.5px;
  font-weight: 400;
  line-height: 17px;
  border-radius: 6px;
}

.rp-tag.injury {
  color: var(--gsms-injury);
  background: var(--gsms-injury-soft);
}

.rp-tag.arb {
  color: var(--gsms-arb);
  background: var(--gsms-arb-soft);
}

.rp-stage {
  font-size: 12px;
  font-weight: 400;
  color: var(--gsms-text-3);
}

.rp-body {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--gsms-text-sub);
}

.area-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.area-add {
  display: flex;
  gap: 8px;
}

.area-list {
  margin-top: 12px;
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid var(--gsms-line-2);
  border-radius: 8px;
  padding: 4px 10px;
}

.area-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px dashed var(--gsms-line-2);
}

.area-item:last-child {
  border-bottom: 0;
}

.ai-name {
  font-size: 13px;
}

.area-tip {
  margin-top: 10px;
  font-size: 11.5px;
  color: var(--gsms-text-3);
  line-height: 1.6;
}

.empty {
  padding: 14px 0;
  text-align: center;
  font-size: 12px;
  color: var(--gsms-text-3);
}

/* ---------------- 移动端布局优化 ---------------- */
@media (max-width: 768px) {
  /* 首个分组的标题与下方「案件类型」标签重复，窄屏省掉一行高度 */
  .type-divider {
    display: none;
  }

  /* 给固定底栏留出空间，最后一项不被遮住 */
  .create-form {
    padding-bottom: 84px;
  }

  /* 栅格在窄屏一律单列：4/8 列并排会把输入框压成一条缝，右侧字段还会溢出 */
  .create-form :deep(.el-col) {
    width: 100% !important;
    max-width: 100%;
    flex: 0 0 100%;
  }

  .create-form :deep(.el-form-item) {
    margin-bottom: 14px;
  }

  .create-form :deep(.el-form-item__label) {
    padding: 0 0 4px;
    font-weight: 500;
    line-height: 1.4;
  }

  .create-form :deep(.el-divider--horizontal) {
    margin: 20px 0 14px;
  }

  .create-form :deep(.el-divider__text) {
    padding: 0 8px;
    font-size: 13px;
  }

  /* 三等分分段控件：文字不换行、每格等宽，类型区只占一行 */
  .type-group {
    display: flex;
    width: 100%;
  }

  .type-group :deep(.el-radio-button) {
    flex: 1 1 0;
    min-width: 0;
  }

  .type-group :deep(.el-radio-button__inner) {
    width: 100%;
    padding: 9px 2px;
    font-size: 12.5px;
    line-height: 1.3;
  }

  /* 16px 可避免 iOS 聚焦时自动放大页面 */
  .create-form :deep(.el-input__inner),
  .create-form :deep(.el-textarea__inner) {
    font-size: 16px;
  }

  .create-form :deep(.el-input__inner) {
    height: 38px;
  }

  /* 金额输入默认居中，与其他字段左对齐才不显突兀 */
  .create-form :deep(.el-input-number .el-input__inner) {
    text-align: left;
  }

  .area-row {
    gap: 6px;
  }

  /* 长表单把提交按钮固定在屏幕底部，滚到任何位置都不用往回找 */
  .actions {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 20;
    margin: 0;
    padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
    background: #fff;
    border-top: 1px solid var(--gsms-border);
    box-shadow: 0 -2px 10px rgba(20, 40, 80, 0.07);
  }

  .actions .el-button {
    flex: 1;
    height: 40px;
    margin-left: 0;
  }
}
</style>

<style>
/* 关联案件下拉候选项：两段式信息，避免整行过长被截断 */
.rel-opt-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.rel-opt-no {
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}

.rel-opt-name {
  color: var(--gsms-text-sub);
}

.rel-opt-meta {
  margin-left: auto;
  font-size: 11.5px;
  color: var(--gsms-text-3);
}

.rel-opt-more {
  font-size: 12px;
  color: var(--gsms-text-3);
}
</style>
