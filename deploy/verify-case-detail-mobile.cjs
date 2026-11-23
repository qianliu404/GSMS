// 案件详情页移动端布局校验：头部按钮、信息两列、步骤条定位、档案行式、办理区按钮
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/mobile'
const CASE_ID = 100 // 待遇处理节点，步骤条足以滚动
const results = []

function log(ok, name, detail = '') {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({
    viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true
  })).newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)

  await page.goto(`${BASE}/cases/${CASE_ID}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1800)

  const ov = await page.evaluate(() => ({
    s: document.documentElement.scrollWidth, c: document.documentElement.clientWidth
  }))
  log(ov.s <= ov.c + 1, '页面无横向溢出', `${ov.s} vs ${ov.c}`)

  // 头部：结案/归档类主操作在窄屏隐藏（办理区已有同名按钮），其余主操作独占整行
  const head = await page.evaluate(() => {
    const btns = [...document.querySelectorAll('.dh-actions .el-button')]
    const go = document.querySelector('.dh-actions .act-go')
    const rest = btns.filter((b) => !b.classList.contains('act-go'))
    const rows = new Set(rest.map((b) => Math.round(b.getBoundingClientRect().top)))
    const widths = rest.map((b) => Math.round(b.getBoundingClientRect().width))
    const nodeGo = document.querySelector('.node-actions .act-go')
    return {
      goText: go ? go.innerText.trim() : '',
      goW: go ? Math.round(go.getBoundingClientRect().width) : 0,
      goHidden: !go || getComputedStyle(go).display === 'none',
      nodeGoVisible: !!nodeGo && getComputedStyle(nodeGo).display !== 'none',
      containerW: Math.round(document.querySelector('.dh-actions').getBoundingClientRect().width),
      rows: rows.size,
      widths
    }
  })
  const isArchiveTop = /归档/.test(head.goText)
  if (isArchiveTop) {
    log(head.goHidden && head.nodeGoVisible, '窄屏隐藏头部归档按钮', `hidden=${head.goHidden} 办理区可归档=${head.nodeGoVisible}`)
  } else {
    log(head.goW >= head.containerW - 2, '主操作按钮独占整行', `${head.goW}/${head.containerW}`)
  }
  log(head.rows === 1 && new Set(head.widths).size === 1, '次要按钮等宽同行', `${head.rows} 行 / ${head.widths.join(',')}`)

  // 信息区两列
  const sub = await page.evaluate(() => {
    const el = document.querySelector('.dh-sub')
    const cols = getComputedStyle(el).gridTemplateColumns.split(' ').length
    const items = [...el.children].map((c) => Math.round(c.getBoundingClientRect().left))
    return { cols, lefts: new Set(items).size }
  })
  log(sub.cols === 2 && sub.lefts === 2, '基础信息两列排布', JSON.stringify(sub))

  // 步骤条：当前节点在可视区内
  const step = await page.evaluate(() => {
    const box = document.querySelector('.steps')
    const cur = box.querySelector('.sx.cur')
    const cr = cur.getBoundingClientRect()
    const br = box.getBoundingClientRect()
    return {
      scrollLeft: Math.round(box.scrollLeft),
      visible: cr.left >= br.left - 1 && cr.right <= br.right + 1,
      name: cur.querySelector('.nm').innerText,
      width: Math.round(box.querySelector('.sx').getBoundingClientRect().width)
    }
  })
  log(step.width <= 70, '步骤条节点紧凑化', `${step.width}px`)
  log(step.scrollLeft > 0 && step.visible, '进入页面自动滚到当前节点', `${step.name} scrollLeft=${step.scrollLeft}`)

  // 办理区按钮
  const acts = await page.evaluate(() => {
    const back = document.querySelector('.node-actions .act-back').getBoundingClientRect()
    const save = document.querySelector('.node-actions .act-save').getBoundingClientRect()
    const go = document.querySelector('.node-actions .act-go')?.getBoundingClientRect()
    return {
      sameRow: Math.abs(Math.round(back.top) - Math.round(save.top)) <= 2,
      equal: Math.abs(Math.round(back.width) - Math.round(save.width)) <= 2,
      goFull: go ? Math.round(go.width) >= Math.round(document.querySelector('.node-actions').getBoundingClientRect().width) - 2 : null
    }
  })
  log(acts.sameRow && acts.equal, '退回/保存草稿等宽同行')
  log(acts.goFull !== false, '主操作占满一行', String(acts.goFull))

  // 档案信息：单列行式
  const arc = await page.evaluate(() => {
    const cells = [...document.querySelectorAll('.arc > div')]
    const first = cells[0]
    const k = first.querySelector('.k').getBoundingClientRect()
    const v = first.querySelector('.v').getBoundingClientRect()
    return {
      oneCol: getComputedStyle(document.querySelector('.arc')).gridTemplateColumns.split(' ').length === 1,
      cellH: Math.round(first.getBoundingClientRect().height),
      sideBySide: Math.abs(Math.round(k.top) - Math.round(v.top)) < 6 && k.left < v.left,
      count: cells.length
    }
  })
  log(arc.oneCol && arc.sideBySide, '档案信息单列行式（标签左、值右）', `单元格高 ${arc.cellH}px / 共 ${arc.count} 项`)
  log(arc.cellH <= 44, '档案行高紧凑', `${arc.cellH}px`)

  // Tab 内表格可横向滚动而非被裁切
  const tabTable = await page.evaluate(() => {
    const w = document.querySelector('.pane .el-scrollbar__wrap')
    return w ? { sw: w.scrollWidth, cw: w.clientWidth, scrollable: w.scrollWidth > w.clientWidth } : null
  })
  log(!!tabTable && tabTable.scrollable, 'Tab 内表格可横向滑动', JSON.stringify(tabTable))

  log(errors.length === 0, '无 JS 报错', errors.join(' | '))

  await page.screenshot({ path: OUT + '/detail-top.png' })
  await page.evaluate(() => document.querySelector('.arc').scrollIntoView({ block: 'start' }))
  await page.waitForTimeout(300)
  await page.screenshot({ path: OUT + '/detail-arc.png' })

  await browser.close()
  const failed = results.filter((r) => !r).length
  console.log('='.repeat(46))
  console.log(`  通过: ${results.length - failed}   失败: ${failed}`)
  process.exit(failed ? 1 : 0)
})()
