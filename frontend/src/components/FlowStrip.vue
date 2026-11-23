<template>
  <div class="flow" :class="{ 'flow-branch': hasBranch }">
    <template v-for="(block, bi) in layout" :key="bi">
      <!-- 节点卡片：主链节点与分支节点共用同一套样式 -->
      <div class="stp">
        <div
          class="stp-card"
          :class="{ has: block.stage.count > 0, picked: block.stage.code === picked }"
          @click="$emit('pick', block.stage.code)"
        >
          <div class="top">
            <span class="stp-idx">{{ noOf(block.stage.code) }}</span>
            <span class="stp-name">{{ block.stage.name }}</span>
          </div>
          <div class="stp-meta">
            <span>{{ block.stage.timeLimit || '—' }}</span>
            <span v-if="block.stage.count > 0" class="stp-cnt">{{ block.stage.count }}</span>
          </div>
        </div>
      </div>

      <!-- 分支：同一节点有多个后继时，按后继各自成行，合流回主链 -->
      <div v-if="block.rows" class="fork">
        <div v-for="(row, ri) in block.rows" :key="ri" class="fork-row">
          <div v-for="stage in row" :key="stage.code" class="stp fork-stp">
            <div
              class="stp-card"
              :class="{ has: stage.count > 0, picked: stage.code === picked }"
              @click="$emit('pick', stage.code)"
            >
              <div class="top">
                <span class="stp-idx">{{ noOf(stage.code) }}</span>
                <span class="stp-name">{{ stage.name }}</span>
              </div>
              <div class="stp-meta">
                <span>{{ stage.timeLimit || '—' }}</span>
                <span v-if="stage.count > 0" class="stp-cnt">{{ stage.count }}</span>
              </div>
            </div>
          </div>
        </div>
        <span class="fork-label">分支</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  stages: { type: Array, default: () => [] },
  picked: { type: String, default: '' }
})
defineEmits(['pick'])

/** 编号按「渲染顺序」连续排：主链节点在前，分支各行随后，视觉上不跳号 */
const numberMap = computed(() => {
  const map = {}
  let no = 0
  for (const block of layout.value) {
    map[block.stage.code] = ++no
    if (block.rows) {
      for (const row of block.rows) {
        for (const stage of row) {
          map[stage.code] = ++no
        }
      }
    }
  }
  return map
})

function noOf(code) {
  return numberMap.value[code] || ''
}

const hasBranch = computed(() => layout.value.some((b) => b.rows))

/**
 * 把线性 stage 列表还原成「主链 + 分支块」：
 * 主链沿每个节点的第一个后继前进；某节点有多个后继时，其余后继各自延展成一行，
 * 直到回到主链上的合流节点。只有一个有效分支时不拆分，维持原来的线性观感。
 */
const layout = computed(() => {
  const stages = props.stages || []
  if (!stages.length) return []
  const byCode = new Map(stages.map((s) => [s.code, s]))
  const indexOfCode = new Map(stages.map((s, i) => [s.code, i]))
  const nextsOf = (code) => (byCode.get(code)?.next || []).filter((c) => byCode.has(c))

  // 主链：从第一个节点开始，沿每个节点的第一个后继前进
  const spine = []
  const seen = new Set()
  let cur = stages[0].code
  while (cur && byCode.has(cur) && !seen.has(cur)) {
    seen.add(cur)
    spine.push(cur)
    cur = nextsOf(cur)[0]
  }
  const spineIndex = new Map(spine.map((c, i) => [c, i]))

  const used = new Set()
  const blocks = []
  for (let i = 0; i < spine.length; i++) {
    const code = spine[i]
    if (used.has(code)) continue
    const stage = byCode.get(code)
    const live = nextsOf(code)

    if (live.length <= 1) {
      used.add(code)
      blocks.push({ stage })
      continue
    }

    // 指向主链更后面节点的「捷径」（如待遇处理直达结案）不单独成行，保持线性观感
    const starts = live.filter((s) => !(spineIndex.has(s) && spineIndex.get(s) > i + 1))
    if (!starts.length) {
      used.add(code)
      blocks.push({ stage })
      continue
    }

    const branches = starts.map((start) => {
      const row = []
      let merge = null
      let c = start
      while (c && byCode.has(c) && !row.some((x) => x.code === c)) {
        row.push(byCode.get(c))
        const next = nextsOf(c)[0]
        // 下一个节点回到主链 → 本行到此为止，该节点作为合流点在外面渲染
        if (next && spineIndex.has(next) && spineIndex.get(next) > i) {
          merge = next
          break
        }
        c = next
      }
      return { row, merge }
    })

    // 直接后继同时是其它分支的合流点时（裁决 → 履行，而上诉分支的裁定也回到履行），
    // 不把它画成一条并列分支，而是留给主链，渲染在分支框之后
    const mergeCodes = new Set(branches.map((b) => b.merge).filter(Boolean))
    const rows = []
    let droppedDirect = false
    for (const branch of branches) {
      if (branch.row.length === 1) {
        const only = branch.row[0].code
        if (spineIndex.get(only) === i + 1 && mergeCodes.has(only)) {
          droppedDirect = true
          continue
        }
      }
      if (branch.row.length) rows.push(branch.row)
    }

    // 只有一条并列分支且没有合流点可外提时保持线性；否则画出分支框
    if (!rows.length || (rows.length === 1 && !droppedDirect)) {
      used.add(code)
      blocks.push({ stage })
      continue
    }

    rows.forEach((row) => row.forEach((n) => used.add(n.code)))
    blocks.push({ stage, rows })
  }
  return blocks
})
</script>

<style scoped>
.flow .stp {
  min-width: 88px;
  padding-right: 10px;
}

/* 名字不再被压成省略号 */
.flow .stp-name {
  font-size: 12px;
}

/* 紧凑一点，让 5 个字的节点名（裁决/调解）完整显示，避免横向滚动 */
.flow .stp-card {
  padding: 6px;
}

.flow .stp-card .top {
  gap: 3px;
}

.flow .stp-idx {
  width: 12px;
  height: 12px;
  flex: 0 0 12px;
  font-size: 9px;
}

.fork {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 10px 8px;
  margin-right: 10px;
  border: 1px dashed #cfdcf2;
  border-radius: 12px;
  background: #fbfcff;
  flex: 0 0 auto;
}

.fork-row {
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
  padding-left: 16px;
}

/* 分支折线：竖线 + 横线，首行只画下半段、末行只画上半段，形成 ├ / └ 的括号效果 */
.fork-row::before {
  content: '';
  position: absolute;
  left: 5px;
  top: -8px;
  bottom: -8px;
  width: 1px;
  background: #c9d6ec;
}

.fork-row:first-child::before {
  top: 50%;
}

.fork-row:last-child::before {
  bottom: 50%;
}

.fork-row::after {
  content: '';
  position: absolute;
  left: 5px;
  top: 50%;
  width: 11px;
  height: 1px;
  background: #c9d6ec;
}

.fork-stp {
  flex: 0 0 auto;
  min-width: 90px;
  padding-right: 10px;
}

.fork-stp .stp-card {
  height: 100%;
}

.fork-label {
  position: absolute;
  top: -9px;
  left: 14px;
  padding: 0 6px;
  background: #fff;
  color: var(--gsms-text-3);
  font-size: 11px;
  letter-spacing: 1px;
}

.flow-branch {
  align-items: center;
  /* “分支”标签在框体上方 9px，这里留出空间，否则会被 overflow 裁掉 */
  padding-top: 14px;
}

/* 选中节点高亮（父页面 scoped 样式无法穿透子组件，这里自带一份） */
.stp-card.picked {
  border-color: var(--gsms-primary);
  box-shadow: 0 0 0 3px var(--gsms-primary-soft);
}
</style>
