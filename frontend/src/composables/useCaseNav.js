import { useRoute, useRouter } from 'vue-router'

/**
 * 案件详情跳转：把来源页面地址记进 query.from。
 *
 * 详情页有两种来源：列表类页面（案件列表 / 工伤 / 仲裁 / 台账 / 待办 / 关联 / 期限）
 * 和案件详情自身（点关联案件跳过去）。把来源写进 URL 后：
 * 1. 详情页「返回」回到原来那个列表，而不是统一回「案件列表」；
 * 2. 左侧菜单按来源模块保持高亮，不会跳回「案件列表」。
 *
 * from 缺省取当前地址；列表页有未写进 URL 的筛选状态时，可显式传入完整地址。
 */
export function useCaseNav() {
  const route = useRoute()
  const router = useRouter()

  function openCaseDetail(id, from) {
    if (!id) return
    router.push({ path: `/cases/${id}`, query: { from: from || route.fullPath } })
  }

  return { openCaseDetail }
}
