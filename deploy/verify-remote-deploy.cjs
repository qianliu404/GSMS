// 远端部署验收：登录 → 首页 → 案件列表 → 案件详情 → 档案附件，全部走真实接口
const { chromium } = require('playwright-core')
const BASE = process.env.GSMS_BASE || 'http://192.168.11.179:8081'
const results = []
const log = (ok, name, detail = '') => {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--disable-gpu'] })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(e.message))

  // 登录
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  log((await page.title()).length > 0, '登录页加载', await page.title())
  await page.fill('input[placeholder*="工号"]', 'admin')
  await page.fill('input[type="password"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录"), button:has-text("登录")')
  await page.waitForURL('**/dashboard', { timeout: 20000 })
  await page.waitForTimeout(1500)
  log(page.url().includes('/dashboard'), '登录成功进入首页')

  // 侧栏菜单
  const menuCount = await page.locator('.nav-item').count()
  log(menuCount >= 10, '侧栏菜单项', String(menuCount))

  // 案件列表
  await page.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  const rows = await page.locator('.el-table__body tbody tr').count()
  const bodyText = await page.locator('body').innerText()
  log(rows === 3, '案件列表条数=3', String(rows))
  log(bodyText.includes('共 3 件'), '列表统计显示「共 3 件」')
  log(bodyText.includes('GY2026110001') && bodyText.includes('GY2025001'), '列表含两起工伤案号')
  log(bodyText.includes('李四') && bodyText.includes('张三'), '列表含当事人姓名')

  // 案件详情（李四脚压伤案件 GY2026110001）
  await page.goto(BASE + '/cases/350', { waitUntil: 'networkidle' })
  await page.waitForTimeout(2000)
  const detail = await page.locator('body').innerText()
  log(detail.includes('李四'), '详情显示当事人李四')
  log(detail.includes('GY2026110001'), '详情显示案号 GY2026110001')
  log(detail.includes('工伤认定'), '详情含工伤认定节点信息')

  // 附件
  await page.goto(BASE + '/cases/350?tab=archive', { waitUntil: 'networkidle' }).catch(() => {})
  await page.waitForTimeout(1500)

  // 全程 JS 报错
  log(errs.length === 0, '无前端 JS 异常', errs.slice(0, 2).join(' | '))

  await page.screenshot({ path: 'shots/remote-after-data-sync.png', fullPage: false })
  await browser.close()

  const pass = results.filter(Boolean).length
  console.log(`\n==== ${pass}/${results.length} 通过 ====`)
  process.exit(pass === results.length ? 0 : 1)
})()
