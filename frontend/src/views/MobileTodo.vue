<template>
  <div class="mobile">
    <div class="mhead">
      <div class="mtitle">我的待办</div>
      <div class="msub">{{ userName }} · {{ summary.list?.length || 0 }} 条待处理</div>
    </div>

    <div class="filter">
      <span
        v-for="tab in tabs"
        :key="tab.value"
        class="tab"
        :class="{ active: filterType === tab.value }"
        @click="filterType = tab.value"
      >
        {{ tab.label }}
      </span>
    </div>

    <div class="list" v-loading="loading">
      <div v-for="item in filtered" :key="item.type + item.targetId" class="item" @click="open(item)">
        <div class="top">
          <span class="tname">{{ item.typeName }}</span>
          <span v-if="item.overdue" class="over">超期{{ item.overdueDays }}天</span>
        </div>
        <div class="title">{{ item.title }}</div>
        <div class="case" v-if="item.caseNo">{{ item.caseNo }} · {{ item.caseName }}</div>
        <div class="foot">
          <span class="deadline">{{ item.deadline || item.deadlineDate || '无截止时间' }}</span>
          <span class="go">处理 ›</span>
        </div>
      </div>
      <div v-if="!loading && !filtered.length" class="empty">暂无待办事项</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { collabApi } from '../api/modules'
import { useCaseNav } from '../composables/useCaseNav'
import { useUserStore } from '../store/user'

/** 进案件详情时带上来源，返回时仍回到移动待办 */
const { openCaseDetail } = useCaseNav()
const store = useUserStore()
const loading = ref(false)
const summary = ref({})
const filterType = ref('')

const userName = computed(() => (store.user && store.user.userName) || '')

const tabs = [
  { label: '全部', value: '' },
  { label: '待办理', value: 'TASK' },
  { label: '待会签', value: 'COUNTERSIGN' },
  { label: '期限', value: 'DEADLINE' }
]

const filtered = computed(() => {
  const list = summary.value.list || []
  return filterType.value ? list.filter((i) => i.type === filterType.value) : list
})

function open(item) {
  if (item.caseId) {
    openCaseDetail(item.caseId, '/m/todos')
  }
}

onMounted(async () => {
  loading.value = true
  try {
    if (!store.user) {
      await store.fetchMe()
    }
    summary.value = await collabApi.todos()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.mobile {
  min-height: 100vh;
  background: #f4f6fb;
  padding-bottom: 24px;
}

.mhead {
  background: linear-gradient(135deg, #143066, #1e5eff);
  color: #fff;
  padding: 26px 20px 24px;
}

.mtitle {
  font-size: 21px;
  font-weight: 700;
}

.msub {
  font-size: 12.5px;
  color: #c3d5f7;
  margin-top: 6px;
}

.filter {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid var(--gsms-border);
  overflow-x: auto;
}

.tab {
  font-size: 13px;
  padding: 5px 14px;
  border-radius: 14px;
  background: #f2f5fa;
  color: #5b6b84;
  white-space: nowrap;
}

.tab.active {
  background: var(--gsms-primary);
  color: #fff;
}

.list {
  padding: 12px 14px;
}

.item {
  background: #fff;
  border-radius: 10px;
  padding: 14px 15px;
  margin-bottom: 10px;
  border-left: 3px solid #d9e2f2;
}

.item:has(.over) {
  border-left-color: var(--gsms-danger);
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 7px;
}

.tname {
  font-size: 11.5px;
  color: var(--gsms-primary);
  background: #eef4ff;
  padding: 2px 8px;
  border-radius: 9px;
}

.over {
  font-size: 11.5px;
  color: var(--gsms-danger);
  font-weight: 600;
}

.title {
  font-size: 14.5px;
  font-weight: 600;
  line-height: 1.5;
}

.case {
  font-size: 12px;
  color: var(--gsms-text-sub);
  margin-top: 5px;
}

.foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 9px;
  font-size: 12px;
  color: var(--gsms-text-sub);
}

.go {
  color: var(--gsms-primary);
  font-weight: 600;
}

.empty {
  text-align: center;
  color: var(--gsms-text-sub);
  font-size: 13px;
  padding: 60px 0;
}
</style>
