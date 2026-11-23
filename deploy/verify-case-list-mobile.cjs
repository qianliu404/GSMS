// 案件列表移动端布局校验：工具栏折叠、卡片列表、分页精简、无横向溢出
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

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2000)

  await page.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  await page.screenshot({ path: OUT + '/list-01-collapsed.png', fullPage: true })

  // 1. 无横向溢出
  const ov = await page.evaluate(() => ({
    s: document.documentElement.scrollWidth,
    c: document.documentElement.clientWidth
  }))
  log(ov.s <= ov.c + 1, '页面无横向溢出', `${ov.s} vs ${ov.c}`)

  // 2. 表格隐藏、卡片列表显示
  const view = await page.evaluate(() => ({
    table: getComputedStyle(document.querySelector('.d-table')).display,
    cards: getComputedStyle(document.querySelector('.m-list')).display,
    count: document.querySelectorAll('.m-case').length
  }))
  log(view.table === 'none', '窄屏隐藏表格', view.table)
  log(view.cards === 'block' && view.count > 0, '窄屏显示卡片列表', `${view.cards} / ${view.count} 张`)

  // 3. 卡片信息完整：案号、当事人、阶段、进度、操作
  const card = await page.locator('.m-case').first().innerText()
  log(/GY|ZS/.test(card) && card.includes('/'), '卡片含案号与阶段序号', card.split('\n').slice(0, 4).join(' | '))
  log(card.includes('%'), '卡片含流程进度')
  log(card.includes('详情') && card.includes('删除'), '卡片含详情/删除操作')

  // 4. 工具栏：类型分段独占一行，搜索与筛选同排
  const bar = await page.evaluate(() => {
    const tb = document.querySelector('.toolbar')
    const tbPad = parseFloat(getComputedStyle(tb).paddingLeft) + parseFloat(getComputedStyle(tb).paddingRight)
    const seg = document.querySelector('.seg').getBoundingClientRect()
    const kw = document.querySelector('.f-keyword').getBoundingClientRect()
    const btn = document.querySelector('.f-filter').getBoundingClientRect()
    return {
      contentW: Math.round(tb.getBoundingClientRect().width - tbPad),
      segW: Math.round(seg.width),
      kwTop: Math.round(kw.top),
      btnTop: Math.round(btn.top),
      btnVisible: btn.width > 0
    }
  })
  log(Math.abs(bar.segW - bar.contentW) <= 2, '类型分段占满一行', `${bar.segW} / ${bar.contentW}`)
  log(bar.btnVisible && Math.abs(bar.kwTop - bar.btnTop) <= 4, '搜索框与「筛选」按钮同一行', `kw=${bar.kwTop} btn=${bar.btnTop}`)

  // 5. 次要筛选默认收起（含日期区间，Element 自带 display 需覆盖）
  const adv = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.f-adv')]
    return { total: items.length, visible: items.filter((e) => getComputedStyle(e).display !== 'none').length }
  })
  log(adv.visible === 0, '次要筛选默认全部收起', `${adv.visible}/${adv.total} 可见`)

  // 6. 点「筛选」展开，出现重置且不溢出
  await page.click('.f-filter')
  await page.waitForTimeout(500)
  const opened = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.f-adv')].map((e) => getComputedStyle(e).display)
    const reset = document.querySelector('.f-reset')
    const ov2 = document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
    return { shown: items.filter((d) => d !== 'none').length, total: items.length, reset: !!reset, overflow: ov2 }
  })
  log(opened.shown === opened.total, '展开后全部筛选可见', `${opened.shown}/${opened.total}`)
  log(opened.reset, '展开后出现「重置筛选」')
  log(!opened.overflow, '展开状态仍无横向溢出')
  await page.screenshot({ path: OUT + '/list-02-expanded.png', fullPage: true })

  // 7. 用展开后的「全部阶段」筛一次，验证联动
  await page.locator('.f-adv').first().click()
  await page.waitForTimeout(400)
  const opt = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first()
  const optText = await opt.innerText()
  await opt.click()
  await page.waitForTimeout(1500)
  const filteredTotal = await page.locator('.m-case').count()
  log(filteredTotal > 0 && filteredTotal < 40, '按阶段筛选后列表收敛', `选中「${optText.trim()}」→ ${filteredTotal} 张卡片`)

  // 8. 分页精简为上一页/页码/下一页
  const pager = await page.evaluate(() => {
    const el = document.querySelector('.pager .el-pagination')
    return { sizes: !!el.querySelector('.el-pagination__sizes'), jumper: !!el.querySelector('.el-pagination__jump') }
  })
  log(!pager.sizes && !pager.jumper, '窄屏分页隐藏每页条数与跳页', JSON.stringify(pager))

  // 9. 点卡片进入详情
  await page.locator('.m-case').first().click()
  await page.waitForTimeout(1800)
  log(/\/cases\/\d+/.test(page.url()), '点击卡片进入案件详情', page.url())

  await browser.close()
  const failed = results.filter((r) => !r).length
  console.log('='.repeat(46))
  console.log(`  通过: ${results.length - failed}   失败: ${failed}`)
  process.exit(failed ? 1 : 0)
})()
