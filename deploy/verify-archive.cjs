// 验证：归档库中筛选留在本页，点击卷宗进入归档库内的卷宗详情并可返回
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:4173'
const OUT = 'E:/GSMS/deploy/shots/archive-fix'
const results = []
const errors = []
function log(ok, name, detail = '') {
  results.push({ ok, name })
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2000)

  // 1. 归档库 + 工伤筛选
  await page.goto(BASE + '/archive', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.click('.seg button:has-text("工伤")')
  await page.waitForTimeout(1200)
  log(page.url().includes('/archive') && page.url().includes('caseType=INJURY'), '工伤筛选留在归档库', page.url())
  await page.screenshot({ path: OUT + '/10-archive-injury.png', fullPage: true })

  // 2. 点击卷宗 → 归档库内卷宗详情
  const row = page.locator('.el-table__body tr').first()
  log((await row.count()) > 0, '归档列表有卷宗')
  await row.locator('td').first().click()
  await page.waitForTimeout(1800)
  log(/\/archive\/\d+$/.test(page.url()), '点击卷宗进入 /archive/:id', page.url())
  log((await page.locator('.dh-top .no').count()) > 0, '卷宗详情已渲染')
  const navOn = await page.locator('.nav-item.on').allInnerTexts()
  log(navOn.some((t) => t.includes('归档库')), '侧栏高亮仍在归档库', JSON.stringify(navOn))
  log((await page.locator('button:has-text("返回归档库")').count()) > 0, '返回按钮文案为返回归档库')
  await page.screenshot({ path: OUT + '/11-archive-detail.png', fullPage: true })

  // 3. 返回 → 回到归档库且保留工伤筛选
  await page.click('button:has-text("返回归档库")')
  await page.waitForTimeout(1500)
  log(page.url().includes('/archive') && page.url().includes('caseType=INJURY'), '返回后保留工伤筛选', page.url())
  const onSeg = await page.locator('.seg button.on').innerText()
  log(onSeg.trim() === '工伤', '分段仍选中工伤', onSeg)
  await page.screenshot({ path: OUT + '/12-archive-back.png', fullPage: true })

  // 4. 仲裁筛选 + 空态
  await page.click('.seg button:has-text("仲裁")')
  await page.waitForTimeout(1200)
  log(page.url().includes('caseType=ARBITRATION'), '仲裁筛选留在归档库', page.url())

  // 5. 案件列表进入详情仍正常
  await page.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  const r2 = page.locator('.el-table__body tr').first()
  if (await r2.count()) {
    await r2.locator('td').first().click()
    await page.waitForTimeout(1600)
    log(/\/cases\/\d+$/.test(page.url()), '案件列表 → 案件详情', page.url())
    log((await page.locator('button:has-text("返回列表")').count()) > 0, '案件详情返回按钮为返回列表')
  }

  log(errors.length === 0, '无 JS 报错', JSON.stringify(errors).slice(0, 300))
  const failed = results.filter((r) => !r.ok).length
  console.log(`\n== ${results.length - failed}/${results.length} passed ==`)
  await browser.close()
  process.exit(failed ? 1 : 0)
})()
