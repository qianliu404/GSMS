// 操作说明配图：逐页截图（对已部署系统，1440x900）
const { chromium } = require('playwright-core')
const fs = require('fs')
const path = require('path')

const BASE = process.env.GSMS_BASE || 'http://192.168.11.179:8081'
const OUT = process.env.GSMS_SHOT_DIR || 'E:/GSMS/docs/images'
fs.mkdirSync(OUT, { recursive: true })

const PAGES = [
  ['01-login', '/login', 1200],
  ['02-dashboard', '/dashboard', 2500],
  ['03-case-list', '/cases', 2500],
  ['04-case-create', '/cases/create', 2000],
  ['05-case-detail', '/cases/350', 3000],
  ['06-case-relations', '/relations', 2000],
  ['07-ledger', '/ledger', 2500],
  ['08-todos', '/todos', 2000],
  ['09-deadlines', '/deadlines', 2500],
  ['10-reports', '/reports', 2500],
  ['11-archive', '/archive', 2000],
  ['12-workflow', '/system/workflow', 2500],
  ['13-users', '/system/users', 2000],
  ['14-permissions', '/system/permissions', 2500],
  ['15-audit', '/system/audit-logs', 2500],
  ['16-recycle', '/recycle-bin', 2000]
]

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--disable-gpu'] })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder*="工号"]', 'admin')
  await page.fill('input[type="password"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录"), button:has-text("登录")')
  await page.waitForURL('**/dashboard', { timeout: 20000 })
  await page.waitForTimeout(1200)

  for (const [name, route, wait] of PAGES) {
    try {
      if (route === '/login') {
        const p2 = await ctx.newPage()
        await p2.setViewportSize({ width: 1440, height: 900 })
        await p2.goto(BASE + '/login', { waitUntil: 'networkidle' })
        await p2.waitForTimeout(wait)
        await p2.screenshot({ path: path.join(OUT, name + '.png') })
        await p2.close()
        console.log('[OK] ' + name)
        continue
      }
      await page.goto(BASE + route, { waitUntil: 'networkidle' })
      await page.waitForTimeout(wait)
      await page.screenshot({ path: path.join(OUT, name + '.png') })
      console.log('[OK] ' + name)
    } catch (e) {
      console.log('[FAIL] ' + name + ' ' + e.message)
    }
  }

  // 详情页关键区域（右栏争议关联 + 档案）
  try {
    await page.goto(BASE + '/cases/350', { waitUntil: 'networkidle' })
    await page.waitForTimeout(2500)
    const tabs = page.locator('.el-tabs__item, .tab, [role="tab"]')
    const n = await tabs.count()
    for (let i = 0; i < n; i++) {
      const t = (await tabs.nth(i).innerText()).trim()
      if (/档案|材料/.test(t)) {
        await tabs.nth(i).click()
        await page.waitForTimeout(1800)
        await page.screenshot({ path: path.join(OUT, '05b-case-archive.png') })
        console.log('[OK] 05b-case-archive (' + t + ')')
        break
      }
    }
  } catch (e) { console.log('[FAIL] archive tab ' + e.message) }

  console.log('js_errors=' + errs.length)
  await browser.close()
})()
