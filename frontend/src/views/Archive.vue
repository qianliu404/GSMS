<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">归档库</h2>
      </div>
      <div class="head-actions">
        <el-button :icon="Download" @click="onExport">导出卷宗目录</el-button>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <div class="seg">
          <button :class="{ on: !query.caseType }" @click="setType('')">全部</button>
          <button :class="{ on: query.caseType === 'INJURY' }" @click="setType('INJURY')">工伤</button>
          <button :class="{ on: query.caseType === 'ARBITRATION' }" @click="setType('ARBITRATION')">仲裁</button>
        </div>
        <el-select
          v-model="year"
          placeholder="全部年度"
          clearable
          class="f-adv"
          :class="{ 'f-adv--closed': !moreOpen }"
          style="width: 118px"
          @change="onYearChange"
        >
          <el-option v-for="y in years" :key="y" :label="`${y} 年`" :value="y" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="案号 / 当事人"
          clearable
          class="f-keyword"
          style="width: 180px"
          :prefix-icon="Search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button class="m-only f-filter" @click="moreOpen = !moreOpen">
          {{ moreOpen ? '收起' : '年度' }}<em v-if="!moreOpen && year">{{ year }}</em>
        </el-button>
        <div class="spacer d-only"></div>
        <span class="tip">共 {{ total }} 卷</span>
      </div>

      <el-table v-loading="loading" :data="list" class="d-table" style="width: 100%" @row-click="openDetail">
        <el-table-column label="案号" width="152">
          <template #default="{ row }">
            <span class="cell-no">{{ row.caseNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="86">
          <template #default="{ row }">
            <span class="tag" :class="row.caseType === 'INJURY' ? 'injury' : 'arb'">
              {{ row.caseType === 'INJURY' ? '工伤' : '仲裁' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="当事人" width="96">
          <template #default="{ row }">
            <span class="cell-who">{{ row.employeeName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结案方式" width="118">
          <template #default="{ row }">{{ row.closeTypeName || '—' }}</template>
        </el-table-column>
        <el-table-column label="结案日期" width="112">
          <template #default="{ row }">
            <span class="cell-dim">{{ row.closeDate || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="卷宗页数" width="96">
          <template #default="{ row }">
            <span class="cell-dim">{{ row.archivePages ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="保管期限" width="96">
          <template #default="{ row }">
            <span class="cell-dim">{{ row.archiveYears ? row.archiveYears + ' 年' : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="承办人" width="96">
          <template #default="{ row }">{{ row.handlerName || row.handlerId }}</template>
        </el-table-column>
        <el-table-column label="" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="openDetail(row)">查看 ›</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">暂无归档卷宗</div>
        </template>
      </el-table>

      <!-- 窄屏：卷宗列表改为卡片，一屏可看清案号、当事人与归档要素 -->
      <div v-loading="loading" class="m-arc">
        <div v-for="row in list" :key="row.id" class="m-arc-card" @click="openDetail(row)">
          <div class="m-l1">
            <span class="tag" :class="row.caseType === 'INJURY' ? 'injury' : 'arb'">
              {{ row.caseType === 'INJURY' ? '工伤' : '仲裁' }}
            </span>
            <span class="cell-no">{{ row.caseNo }}</span>
            <span class="m-open">查看 ›</span>
          </div>

          <div class="m-l2">{{ row.employeeName }}</div>

          <div class="m-l3">
            <span>{{ row.closeTypeName || '结案方式 —' }}</span>
            <span class="cell-dim">{{ row.closeDate || '—' }}</span>
          </div>

          <div class="m-l4">
            <span class="cell-dim">卷宗 {{ row.archivePages ?? '—' }} 页</span>
            <span class="cell-dim">保管 {{ row.archiveYears ? row.archiveYears + ' 年' : '—' }}</span>
            <span class="cell-dim">承办 {{ row.handlerName || row.handlerId }}</span>
          </div>
        </div>
        <div v-if="!loading && !list.length" class="empty">暂无归档卷宗</div>
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'
import { caseApi, reportApi } from '../api/modules'
import { useNarrow } from '../composables/useNarrow'

const route = useRoute()
const router = useRouter()
const isNarrow = useNarrow()
const moreOpen = ref(false)
const loading = ref(false)
const list = ref([])
const total = ref(0)
const year = ref(route.query.year || '')

const pagerLayout = computed(() => (isNarrow.value ? 'prev, pager, next' : 'total, sizes, prev, pager, next'))

const currentYear = new Date().getFullYear()
const years = Array.from({ length: 5 }, (_, i) => currentYear - i)

const query = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: route.query.keyword || '',
  caseType: route.query.caseType || '',
  archiveStatus: 1,
  occurDateFrom: year.value ? `${year.value}-01-01` : '',
  occurDateTo: year.value ? `${year.value}-12-31` : ''
})

/** 筛选条件写入地址栏，从卷宗详情返回时仍保持当前筛选 */
function syncRoute() {
  const q = {}
  if (query.caseType) q.caseType = query.caseType
  if (year.value) q.year = year.value
  if (query.keyword) q.keyword = query.keyword
  router.replace({ path: '/archive', query: q })
}

function setType(type) {
  query.caseType = type
  reload()
}

function onYearChange(val) {
  query.occurDateFrom = val ? `${val}-01-01` : ''
  query.occurDateTo = val ? `${val}-12-31` : ''
  reload()
}

async function load() {
  loading.value = true
  try {
    const data = await caseApi.page({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  query.pageNum = 1
  syncRoute()
  load()
}

/** 卷宗详情：留在归档库模块内查看，不跳转到案件列表 */
function openDetail(row) {
  router.push(`/archive/${row.id}`)
}

async function onExport() {
  try {
    const response = await reportApi.exportArchive({ ...query, pageNum: 1, pageSize: 100 })
    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const disposition = response.headers['content-disposition'] || ''
    let filename = '归档卷宗目录.xlsx'
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
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

onMounted(load)
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

.tip {
  font-size: 12px;
  color: var(--gsms-text-3);
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
}

/* 卡片列表只在窄屏显示（须写在媒体查询之前） */
.m-arc {
  display: none;
}

@media (max-width: 768px) {
  .toolbar {
    gap: 8px;
    padding: 10px 12px;
  }

  .d-table {
    display: none;
  }

  .m-arc {
    display: block;
  }

  .pager {
    justify-content: center;
    padding: 10px 8px;
  }
}

/* ---------------- 卷宗卡片 ---------------- */
.m-arc-card {
  padding: 12px 14px;
  border-bottom: 1px solid var(--gsms-line-2);
  cursor: pointer;
}

.m-arc-card:last-child {
  border-bottom: 0;
}

.m-l1 {
  display: flex;
  align-items: center;
  gap: 6px;
}

.m-open {
  margin-left: auto;
  font-size: 12px;
  color: var(--gsms-primary);
}

.m-l2 {
  margin-top: 6px;
  font-size: 14px;
  font-weight: 600;
}

.m-l3 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  font-size: 12.5px;
}

.m-l4 {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 6px;
}
</style>
