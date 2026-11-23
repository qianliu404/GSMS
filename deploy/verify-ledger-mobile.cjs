// 台账页移动端布局校验：统计卡 2×2、工具栏折叠、卡片列表、字段展开、分页精简
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
  await page.waitForTimeout(2200)

  await page.goto(BASE + '/ledger', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  await page.screenshot({ path: OUT + '/ledger-01.png', fullPage: true })

  const ov = await page.evaluate(() => ({
    s: document.documentElement.scrollWidth,
    c: document.documentElement.clientWidth
  }))
  log(ov.s <= ov.c + 1, '页面无横向溢出', `${ov.s} vs ${ov.c}`)

  // 统计卡 2 列
  const stat = await page.evaluate(() => {
    const cards = [...document.querySelectorAll('.stat-card')].map((c) => Math.round(c.getBoundingClientRect().top))
    const cols = getComputedStyle(document.querySelector('.stat-grid')).gridTemplateColumns.split(' ').length
    return { cols, rows: new Set(cards).size, count: cards.length }
  })
  log(stat.cols === 2 && stat.rows === 2, '统计卡改为 2×2', `${stat.cols} 列 / ${stat.rows} 行 / ${stat.count} 张`)

  // 页头按钮一行等分
  const head = await page.evaluate(() => {
    const btns = [...document.querySelectorAll('.head-actions .el-button')]
    return { count: btns.length, rows: new Set(btns.map((b) => Math.round(b.getBoundingClientRect().top))).size }
  })
  log(head.rows === 1, '页头按钮同一行', `${head.count} 个 / ${head.rows} 行`)

  // 表格隐藏、卡片显示
  const view = await page.evaluate(() => ({
    table: getComputedStyle(document.querySelector('.d-table')).display,
    cards: getComputedStyle(document.querySelector('.m-led')).display,
    count: document.querySelectorAll('.m-led-card').length
  }))
  log(view.table === 'none', '窄屏隐藏表格', view.table)
  log(view.cards === 'block' && view.count > 0, '窄屏显示台账卡片', `${view.cards} / ${view.count} 张`)

  // 卡片核心信息（全部视图默认不含三项待遇列，切到工伤视图再验）
  const card = await page.locator('.m-led-card').first().innerText()
  log(/GY|ZS/.test(card), '卡片含案号', card.split('\n')[0])

  // 切到工伤视图：该视图默认包含三项待遇与班长等字段
  await page.locator('.seg button', { hasText: '工伤' }).click()
  await page.waitForTimeout(1600)
  const injCard = await page.locator('.m-led-card').first().innerText()
  log(injCard.includes('伤残金'), '工伤视图卡片含三项待遇入口', injCard.replace(/\n/g, ' | ').slice(0, 90))
  const moreCount = await page.locator('.m-more').count()
  log(moreCount > 0, '有内容的卡片提供字段展开入口', `${moreCount} 张卡片可展开`)
  await page.screenshot({ path: OUT + '/ledger-01-injury.png', fullPage: true })

  // 次要筛选默认收起
  const adv = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.f-adv')]
    return { total: items.length, visible: items.filter((e) => getComputedStyle(e).display !== 'none').length }
  })
  log(adv.visible === 0, '次要筛选默认收起', `${adv.visible}/${adv.total} 可见`)

  // 展开筛选
  await page.click('.f-filter')
  await page.waitForTimeout(400)
  const opened = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.f-adv')]
    return {
      visible: items.filter((e) => getComputedStyle(e).display !== 'none').length,
      total: items.length,
      reset: !!document.querySelector('.f-reset'),
      overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
    }
  })
  log(opened.visible === opened.total, '展开后筛选全部可见', `${opened.visible}/${opened.total}`)
  log(opened.reset, '展开后出现「重置筛选」')
  log(!opened.overflow, '展开状态无横向溢出')

  // 字段展开：展开后追加 kv 行
  const before = await page.evaluate(() => document.querySelectorAll('.m-kv').length)
  await page.locator('.m-more').first().click()
  await page.waitForTimeout(400)
  const after = await page.evaluate(() => document.querySelectorAll('.m-kv').length)
  log(before === 0 && after > 0, '点「展开字段」后显示其余字段', `${before} → ${after} 行`)
  await page.screenshot({ path: OUT + '/ledger-02-expanded.png', fullPage: true })

  // 分页精简
  const pager = await page.evaluate(() => {
    const el = document.querySelector('.pager .el-pagination')
    return { sizes: !!el.querySelector('.el-pagination__sizes'), jumper: !!el.querySelector('.el-pagination__jump') }
  })
  log(!pager.sizes && !pager.jumper, '窄屏分页精简', JSON.stringify(pager))

  // 点待遇标签打开申领弹窗
  await page.locator('.m-sub').first().click()
  await page.waitForTimeout(600)
  const dlg = await page.locator('.el-dialog:visible').count()
  log(dlg > 0, '点待遇标签弹出申领编辑', `${dlg} 个弹窗`)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)

  await browser.close()
  const failed = results.filter((r) => !r).length
  console.log('='.repeat(46))
  console.log(`  通过: ${results.length - failed}   失败: ${failed}`)
  process.exit(failed ? 1 : 0)
})()
