// 流程总览（Dashboard）移动端布局校验
// 关注点：统计卡 2×2、页头按钮等分、待办行不挤压；流程条按要求保持原样（横向滚动）
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/mobile'
const results = []

function log(ok, name, detail = '') {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true })
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)

  await page.goto(BASE + '/dashboard', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  await page.screenshot({ path: OUT + '/dash-final.png', fullPage: true })

  // 1. 无横向溢出
  const ov = await page.evaluate(() => ({
    s: document.documentElement.scrollWidth,
    c: document.documentElement.clientWidth
  }))
  log(ov.s <= ov.c + 1, '页面无横向溢出', `${ov.s} vs ${ov.c}`)

  // 2. 统计卡 2×2，且高度收敛
  const stat = await page.evaluate(() => {
    const grid = document.querySelector('.stat-grid')
    const cards = [...grid.querySelectorAll('.stat-card')]
    const rects = cards.map((c) => c.getBoundingClientRect())
    return {
      cols: getComputedStyle(grid).gridTemplateColumns.split(' ').length,
      rows: new Set(rects.map((r) => Math.round(r.top))).size,
      widths: [...new Set(rects.map((r) => Math.round(r.width)))],
      height: Math.round(rects[0].height),
      totalH: Math.round(grid.getBoundingClientRect().height)
    }
  })
  log(stat.cols === 2 && stat.rows === 2, '统计卡 2×2 排列', `${stat.cols} 列 / ${stat.rows} 行`)
  log(stat.widths.length === 1, '统计卡等宽', stat.widths.join(','))
  log(stat.height <= 95 && stat.totalH <= 200, '统计区高度压缩到 200px 内', `单卡 ${stat.height}px / 整块 ${stat.totalH}px`)

  // 3. 页头两个按钮等分一行
  const head = await page.evaluate(() => {
    const btns = [...document.querySelectorAll('.head-actions .el-button')]
    return {
      rows: new Set(btns.map((b) => Math.round(b.getBoundingClientRect().top))).size,
      widths: btns.map((b) => Math.round(b.getBoundingClientRect().width)),
      headW: Math.round(document.querySelector('.head-actions').getBoundingClientRect().width),
      gridW: Math.round(document.querySelector('.stat-grid').getBoundingClientRect().width)
    }
  })
  log(head.rows === 1 && new Set(head.widths).size === 1, '页头按钮等分一行', `${head.rows} 行 / ${head.widths.join(',')}`)
  log(Math.abs(head.headW - head.gridW) <= 2, '页头按钮撑满内容宽', `${head.headW}/${head.gridW}`)

  // 4. 流程条保持原样：横向滚动，不做网格化改造
  const flow = await page.evaluate(() => {
    const f = document.querySelector('.flow')
    const card = document.querySelector('.flow .stp-card')
    return {
      display: getComputedStyle(f).display,
      scrollable: f.scrollWidth > f.clientWidth + 1,
      scrollW: f.scrollWidth,
      clientW: f.clientWidth,
      cardW: Math.round(card.getBoundingClientRect().width),
      noteLines: [...document.querySelectorAll('.flow-note span')].filter((s) => getComputedStyle(s).display !== 'none').length,
      h: Math.round(f.getBoundingClientRect().height)
    }
  })
  log(flow.display === 'flex' && flow.scrollable, '流程条保持横向滚动（原样）', `${flow.display} ${flow.scrollW}/${flow.clientW}`)
  log(flow.noteLines >= 2, '流程说明两行仍在', `${flow.noteLines} 行`)

  // 5. 待办行：按钮与文字同一行，不被挤到下一行
  const todo = await page.evaluate(() => {
    const t = document.querySelector('.todo')
    if (!t) return null
    const btn = t.querySelector('.el-button').getBoundingClientRect()
    const txt = t.querySelector('.t').getBoundingClientRect()
    return {
      sameRow: Math.abs(btn.top + btn.height / 2 - (txt.top + txt.height / 2)) < 24,
      btnW: Math.round(btn.width),
      h: Math.round(t.getBoundingClientRect().height),
      lines: t.querySelector('.t').getClientRects().length
    }
  })
  log(!!todo && todo.sameRow && todo.h <= 62, '待办行两行文字 + 按钮同行', todo ? `行高 ${todo.h}px / 按钮 ${todo.btnW}px` : '无待办')

  // 6. 点击流程节点仍可下钻
  await page.locator('.flow .stp-card').first().click()
  await page.waitForTimeout(900)
  log(page.url().includes('/cases'), '点击节点仍可下钻列表', page.url().replace(BASE, ''))

  log(errors.length === 0, '无 JS 报错', errors.join(' | ') || '无')

  await browser.close()
  const pass = results.filter(Boolean).length
  console.log('='.repeat(46))
  console.log(`  通过: ${pass}   失败: ${results.length - pass}`)
  process.exit(pass === results.length ? 0 : 1)
})()
