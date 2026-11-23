<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <div>
        <h2 class="page-title">流程总览</h2>
        <p class="page-sub">{{ today }}</p>
      </div>
      <div class="head-actions">
        <el-button @click="$router.push('/reports')">导出报表</el-button>
        <el-button type="primary" :icon="Plus" @click="$router.push('/cases/create')">新建案件</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="label">在办案件</div>
        <div class="value">{{ data.activeCount }}</div>
        <div class="hint">工伤 {{ data.injuryActiveCount }} · 仲裁 {{ data.arbitrationActiveCount }}</div>
      </div>
      <div class="stat-card warn">
        <div class="label">节点预警</div>
        <div class="value">{{ data.warningCount }}</div>
        <div class="hint">含超期 {{ data.overdueCount }} 件，需今日处理</div>
      </div>
      <div class="stat-card ok">
        <div class="label">本年度已结案</div>
        <div class="value">{{ data.yearClosedCount }}</div>
        <div class="hint">平均办理 {{ data.avgCloseDays }} 天</div>
      </div>
      <div class="stat-card injury">
        <div class="label">累计赔付金额</div>
        <div class="value">{{ wan(data.totalAmount) }}<span class="unit">万</span></div>
        <div class="hint">工伤待遇 {{ wan(data.injuryAmount) }} 万 · 仲裁 {{ wan(data.arbitrationAmount) }} 万</div>
      </div>
    </div>

    <div class="card" v-for="flow in data.flows" :key="flow.key">
      <div class="card-head">
        <h2>{{ flow.name }}流程</h2>
        <span class="pill">{{ flow.stages.length }} 个节点 · 在办 {{ flow.total }} 件</span>
      </div>
      <div class="card-body">
        <FlowStrip :stages="flow.stages" @pick="(code) => drill(flow.key, code)" />
      </div>
    </div>

    <div class="cols">
      <div class="card" style="margin-bottom: 0">
        <div class="card-head">
          <h2>待办与预警</h2>
          <span class="pill">{{ data.todos.length }} 项</span>
        </div>
        <div class="card-body" style="padding-top: 4px; padding-bottom: 6px">
          <div v-if="!data.todos.length" class="empty">暂无预警</div>
          <div v-for="todo in data.todos" :key="todo.caseId + todo.stageName" class="todo">
            <div class="ic" :class="todo.overdue ? 'r' : 'o'">{{ todo.overdue ? '!' : '⏱' }}</div>
            <div>
              <div class="t">{{ todo.caseNo }} · {{ todo.employeeName }} · {{ todo.stageName }}</div>
              <div class="m">
                承办人 {{ todo.handlerName || '-' }}
                <template v-if="todo.overdue">　·　已超节点时限 {{ todo.days }} 天</template>
                <template v-else>　·　距节点到期 {{ todo.days }} 天</template>
              </div>
            </div>
            <div class="go">
              <el-button size="small" @click="openCaseDetail(todo.caseId, '/dashboard')">办理</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="card" style="margin-bottom: 0">
        <div class="card-head">
          <h2>工伤阶段分布</h2>
          <span class="pill">在办</span>
        </div>
        <div class="card-body">
          <div class="mini-bars">
            <div class="mb" v-for="stage in injuryStages" :key="stage.code">
              <div class="lb">{{ stage.name }}</div>
              <div class="tr">
                <i :style="{ width: barWidth(stage.count), background: 'var(--gsms-injury)' }"></i>
              </div>
              <div class="vl">{{ stage.count }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { dashboardApi } from '../api/modules'
import FlowStrip from '../components/FlowStrip.vue'
import { useCaseNav } from '../composables/useCaseNav'

const router = useRouter()
/** 进案件详情时带上来源，返回时仍回到工作台 */
const { openCaseDetail } = useCaseNav()
const loading = ref(false)
const data = ref({
  activeCount: 0,
  injuryActiveCount: 0,
  arbitrationActiveCount: 0,
  warningCount: 0,
  overdueCount: 0,
  yearClosedCount: 0,
  avgCloseDays: 0,
  totalAmount: 0,
  injuryAmount: 0,
  arbitrationAmount: 0,
  flows: [],
  todos: []
})

const today = computed(() => {
  const d = new Date()
  const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 · ${week[d.getDay()]}`
})

const injuryStages = computed(() => {
  const flow = data.value.flows.find((f) => f.key === 'INJURY')
  return flow ? flow.stages : []
})

const maxCount = computed(() =>
  Math.max(1, ...injuryStages.value.map((s) => s.count))
)

function barWidth(count) {
  return `${Math.max(count === 0 ? 0 : 6, (count / maxCount.value) * 100)}%`
}

function wan(value) {
  const n = Number(value || 0)
  return (n / 10000).toFixed(1)
}

function drill(flowKey, status) {
  router.push({ path: '/cases', query: { type: flowKey, status } })
}

async function load() {
  loading.value = true
  try {
    data.value = await dashboardApi.overview()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
/* ---------------- 移动端：一屏放下统计与流程 ---------------- */
@media (max-width: 768px) {
  .page-head {
    align-items: flex-start;
    margin-bottom: 10px;
  }

  .page-title {
    font-size: 17px;
  }

  .page-sub {
    font-size: 11.5px;
    line-height: 1.4;
    margin-top: 2px;
  }

  /* 两个按钮等分一行，高度收敛，不再各占半屏宽的大块 */
  .head-actions {
    width: 100%;
    margin-top: 4px;
  }

  .head-actions .el-button {
    flex: 1;
    margin-left: 0;
    height: 34px;
  }

  /* 四张统计卡各占一行共 456px，改 2×2 后只占约 150px */
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    margin-bottom: 10px;
  }

  .stat-card {
    padding: 10px 12px 11px;
  }

  .stat-card .label {
    font-size: 11px;
  }

  .stat-card .value {
    font-size: 22px;
    letter-spacing: -0.3px;
  }

  .stat-card .unit {
    font-size: 12px;
  }

  .stat-card .hint {
    font-size: 10.5px;
    line-height: 1.35;
    margin-top: 3px;
  }

  /* 流程卡（标题/流程条/说明）保持原样，仅统计卡与页头做窄屏调整 */

  /* 待办行：图标缩小、按钮收窄，案号与节点不再折成三行 */
  .todo {
    gap: 8px;
    padding: 10px 0;
  }

  .todo .ic {
    width: 22px;
    height: 22px;
    flex: 0 0 22px;
    font-size: 11px;
    border-radius: 6px;
  }

  .todo .t {
    font-size: 12.5px;
    line-height: 1.4;
  }

  .todo .m {
    font-size: 11px;
    line-height: 1.4;
  }

  .todo .go .el-button {
    height: 28px;
    padding: 0 10px;
    font-size: 12px;
  }

  .cols {
    gap: 10px;
  }

  .mini-bars {
    gap: 9px;
  }

  .mb {
    gap: 8px;
    font-size: 11.5px;
  }

  .mb .lb {
    width: 62px;
    flex: 0 0 62px;
  }
}
</style>
