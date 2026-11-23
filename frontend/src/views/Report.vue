<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">报表中心</h2>
      </div>
      <div class="flex gap-8">
        <el-button v-if="hasPerm('report:export')" :icon="Download" @click="onExport" :loading="exporting">导出工伤统计表</el-button>
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card accent">
        <div class="label">案件总量</div>
        <div class="value">{{ summary.total || 0 }}</div>
        <div class="hint">在办 {{ summary.openCount || 0 }} · 结案 {{ summary.closedCount || 0 }} · 归档 {{ summary.archivedCount || 0 }}</div>
      </div>
      <div class="stat-card ok">
        <div class="label">平均结案周期</div>
        <div class="value">
          <template v-if="efficiency.avgCloseDays !== null && efficiency.avgCloseDays !== undefined">
            {{ efficiency.avgCloseDays }}<span class="unit">天</span>
          </template>
          <span v-else class="dash">暂无结案</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="label">任务完成率</div>
        <div class="value">{{ efficiency.taskDoneRate || 0 }}<span class="unit">%</span></div>
        <div class="hint">任务 {{ efficiency.taskTotal || 0 }} 个，超期 {{ efficiency.taskOverdue || 0 }} 个</div>
      </div>
      <div class="stat-card danger">
        <div class="label">期限超期率</div>
        <div class="value">{{ overdue.overdueRate || 0 }}<span class="unit">%</span></div>
        <div class="hint">节点 {{ overdue.deadlineTotal || 0 }} 个，超期 {{ overdue.deadlineOverdue || 0 }} 个</div>
      </div>
    </div>

    <el-row :gutter="14">
      <el-col :span="16">
        <div class="card chart-card">
          <div class="chart-title">月度建档趋势（近 12 个月）</div>
          <div ref="trendRef" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="card chart-card">
          <div class="chart-title">案件类型分布</div>
          <div ref="typeRef" class="chart"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" class="mt-12">
      <el-col :span="12">
        <div class="card chart-card">
          <div class="chart-title">状态分布</div>
          <div ref="statusRef" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card amount-card">
          <div class="chart-title">金额统计</div>
          <div v-if="amount.masked" class="masked">
            <el-icon :size="22"><Lock /></el-icon>
            <div>当前角色无金额查看权限</div>
            <div class="text-sub" style="font-size: 12px">金额类数据仅对管理员、部门负责人与承办人开放</div>
          </div>
          <div v-else class="amount-list">
            <div class="amount-item">
              <div class="k">工伤待遇金额</div>
              <div class="v">￥{{ formatMoney(amount.injuryTotal) }}</div>
            </div>
            <div class="amount-item">
              <div class="k">仲裁裁决 / 调解金额</div>
              <div class="v">￥{{ formatMoney(amount.arbitrationTotal) }}</div>
            </div>
            <div class="amount-total">
              <div class="k">合计</div>
              <div class="v">￥{{ formatMoney(amount.total) }}</div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh, Lock } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { reportApi } from '../api/modules'
import { useUserStore } from '../store/user'

const hasPerm = (code) => useUserStore().hasPerm(code)
const summary = ref({})
const efficiency = ref({})
const amount = ref({})
const overdue = ref({})
const exporting = ref(false)

const trendRef = ref()
const typeRef = ref()
const statusRef = ref()
let trendChart = null
let typeChart = null
let statusChart = null

const COLORS = ['#1e5eff', '#e6a23c', '#2ba471', '#8b5cf6', '#e34d59', '#0ea5e9']

function formatMoney(value) {
  const num = Number(value || 0)
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function renderTrend() {
  const items = summary.value.monthly || []
  trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    grid: { left: 44, right: 20, top: 24, bottom: 34 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: items.map((i) => i.name),
      axisLine: { lineStyle: { color: '#dfe5ef' } },
      axisLabel: { color: '#6b7a90', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f3f9' } },
      axisLabel: { color: '#6b7a90', fontSize: 11 }
    },
    series: [
      {
        type: 'bar',
        data: items.map((i) => i.value),
        barMaxWidth: 26,
        itemStyle: { color: '#1e5eff', borderRadius: [4, 4, 0, 0] }
      }
    ]
  })
}

function renderType() {
  const items = summary.value.byType || []
  typeChart = echarts.init(typeRef.value)
  typeChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#6b7a90', fontSize: 11 } },
    color: COLORS,
    series: [
      {
        type: 'pie',
        radius: ['42%', '66%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        label: { show: false },
        data: items.map((i) => ({ name: i.name, value: i.value }))
      }
    ]
  })
}

function renderStatus() {
  const items = summary.value.byStatus || []
  statusChart = echarts.init(statusRef.value)
  statusChart.setOption({
    grid: { left: 84, right: 24, top: 16, bottom: 24 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f3f9' } },
      axisLabel: { color: '#6b7a90', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: items.map((i) => i.name).reverse(),
      axisLine: { lineStyle: { color: '#dfe5ef' } },
      axisLabel: { color: '#6b7a90', fontSize: 11 }
    },
    series: [
      {
        type: 'bar',
        data: items.map((i) => i.value).reverse(),
        barMaxWidth: 16,
        itemStyle: { color: '#2ba471', borderRadius: [0, 4, 4, 0] }
      }
    ]
  })
}

async function loadAll() {
  try {
    const [s, e, a, o] = await Promise.all([
      reportApi.caseSummary(),
      reportApi.efficiency(),
      reportApi.amount(),
      reportApi.overdue()
    ])
    summary.value = s || {}
    efficiency.value = e || {}
    amount.value = a || {}
    overdue.value = o || {}
    await nextTick()
    if (trendChart) trendChart.dispose()
    if (typeChart) typeChart.dispose()
    if (statusChart) statusChart.dispose()
    renderTrend()
    renderType()
    renderStatus()
  } catch (err) {
    /* 拦截器已提示 */
  }
}

async function onExport() {
  exporting.value = true
  try {
    const response = await reportApi.exportCases({ pageNum: 1, pageSize: 100 })
    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const disposition = response.headers['content-disposition'] || ''
    let filename = `工伤事故人员信息统计表_${new Date().toISOString().slice(0, 10)}.xlsx`
    const match = disposition.match(/filename\*=UTF-8''(.+)$/)
    if (match) {
      filename = decodeURIComponent(match[1])
    }
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功：工伤事故人员信息统计表')
  } finally {
    exporting.value = false
  }
}

function onResize() {
  trendChart && trendChart.resize()
  typeChart && typeChart.resize()
  statusChart && statusChart.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart && trendChart.dispose()
  typeChart && typeChart.dispose()
  statusChart && statusChart.dispose()
})
</script>

<style scoped>
.unit {
  font-size: 14px;
  font-weight: 500;
  margin-left: 3px;
  color: var(--gsms-text-sub);
}

.dash {
  font-size: 16px;
  font-weight: 500;
  color: var(--gsms-text-sub);
}

.chart-card {
  padding: 16px 18px 8px;
}

.chart-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 8px;
}

.chart {
  height: 280px;
  width: 100%;
  /* ECharts 的 SVG 标签会略微超出容器，裁掉避免整页出现横向滚动条 */
  overflow: hidden;
}

.amount-card {
  padding: 16px 18px;
  height: 100%;
}

.amount-list {
  padding-top: 6px;
}

.amount-item {
  padding: 14px 0;
  border-bottom: 1px dashed #eef1f7;
}

.amount-item .k {
  font-size: 12.5px;
  color: var(--gsms-text-sub);
  margin-bottom: 6px;
}

.amount-item .v {
  font-size: 21px;
  font-weight: 700;
  color: var(--gsms-text);
  font-variant-numeric: tabular-nums;
}

.amount-total {
  margin-top: 14px;
  padding: 16px;
  background: linear-gradient(135deg, #f2f6ff, #eaf1ff);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.amount-total .k {
  font-size: 13px;
  font-weight: 600;
  color: #45536b;
}

.amount-total .v {
  font-size: 23px;
  font-weight: 800;
  color: var(--gsms-primary);
}

.masked {
  height: 250px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--gsms-text-sub);
  font-size: 13.5px;
}

/* 移动端：金额合计卡字号收一档，避免 23px 数字撑出容器 */
@media (max-width: 640px) {
  .amount-total {
    padding: 12px;
    gap: 8px;
  }

  .amount-total .v {
    font-size: 19px;
  }

  .amount-item .v {
    font-size: 19px;
  }
}
</style>
