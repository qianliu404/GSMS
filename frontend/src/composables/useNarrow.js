import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 窄屏判定（默认 ≤768px）。
 *
 * 多个列表页需要在手机端切换成卡片布局、折叠筛选、精简分页，
 * 统一在此响应式断点，避免各页各写一份 matchMedia 导致临界值不一致。
 */
export function useNarrow(maxWidth = 768) {
  const media = window.matchMedia(`(max-width: ${maxWidth}px)`)
  const isNarrow = ref(media.matches)
  const onChange = (e) => {
    isNarrow.value = e.matches
  }

  onMounted(() => media.addEventListener('change', onChange))
  onBeforeUnmount(() => media.removeEventListener('change', onChange))

  return isNarrow
}
