<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2 class="page-title">审计日志</h2>
      </div>
      <el-button :icon="Refresh" @click="reload">刷新</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="业务ID / 操作 / 操作人"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-input v-model="query.module" placeholder="模块" clearable style="width: 140px" @change="reload" />
        <el-select v-model="query.result" placeholder="结果" clearable style="width: 120px" @change="reload">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAIL" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 250px"
          @change="onDateChange"
        />
        <div class="spacer"></div>
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" plain @click="reload">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="list">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand">
              <div class="row"><span class="k">请求参数</span><span class="v">{{ row.requestParams || '-' }}</span></div>
              <div class="row"><span class="k">IP 地址</span><span class="v">{{ row.requestIp || '-' }}</span></div>
              <div class="row"><span class="k">链路 ID</span><span class="v text-mono">{{ row.traceId || '-' }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="operateTime" label="时间" width="170" />
        <el-table-column prop="userName" label="操作人" width="110">
          <template #default="{ row }">{{ row.userName }}<span class="text-sub">（{{ row.userId }}）</span></template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column prop="operation" label="操作" width="150" />
        <el-table-column prop="businessId" label="业务ID" width="110" />
        <el-table-column prop="requestParams" label="参数（脱敏）" min-width="260" show-overflow-tooltip />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.result === 'SUCCESS' ? 'success' : 'danger'">
              {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { auditApi } from '../api/modules'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const dateRange = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
  module: '',
  result: '',
  dateFrom: '',
  dateTo: ''
})

function onDateChange(val) {
  query.dateFrom = val ? val[0] : ''
  query.dateTo = val ? val[1] : ''
  reload()
}

async function load() {
  loading.value = true
  try {
    const data = await auditApi.page({ ...query })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  query.pageNum = 1
  load()
}

function reset() {
  query.keyword = ''
  query.module = ''
  query.result = ''
  query.dateFrom = ''
  query.dateTo = ''
  dateRange.value = []
  reload()
}

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
}

.expand {
  padding: 8px 24px 12px;
}

.expand .row {
  display: flex;
  gap: 12px;
  padding: 5px 0;
  font-size: 12.5px;
}

.expand .k {
  width: 80px;
  color: var(--gsms-text-sub);
  flex: 0 0 80px;
}

.expand .v {
  word-break: break-all;
}
</style>
