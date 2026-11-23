// 验证：评论功能已下线（前端无评论 Tab、后端接口 404）
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:4173'
const OUT = 'E:/GSMS/deploy/shots/no-comment'
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
  const token = await page.evaluate(() => localStorage.getItem('gsms_token'))

  // 后端接口应已下线
  const probe = await page.evaluate(async (tk) => {
    const r = await fetch('/api/v1/cases/124/comments', { headers: { Authorization: `Bearer ${tk}` } })
    return r.status
  }, token)
  log(probe === 404, '评论接口已下线（404）', `status=${probe}`)

  // 详情页：评论 Tab 不存在，其余 7 个 Tab 正常
  await page.goto(BASE + '/cases/124', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1800)
  const tabs = await page.locator('.el-tabs__item').allInnerTexts()
  log(!tabs.some((t) => t.includes('评论')), '详情页无评论 Tab', JSON.stringify(tabs))
  log(tabs.length === 7, '详情页 Tab 数量为 7', `count=${tabs.length}`)
  await page.screenshot({ path: OUT + '/01-detail-tabs.png', fullPage: false })

  // 逐个切换 Tab 不应报错
  for (const name of ['文档', '任务', '会签', '期限', '关联', '流转日志']) {
    const item = page.locator('.el-tabs__item', { hasText: name }).first()
    if (await item.count()) {
      await item.click()
      await page.waitForTimeout(700)
    }
  }
  log(true, '各 Tab 切换完成')
  await page.screenshot({ path: OUT + '/02-last-tab.png', fullPage: false })

  // 归档详情同样无评论
  await page.goto(BASE + '/archive/106', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  const tabs2 = await page.locator('.el-tabs__item').allInnerTexts()
  log(!tabs2.some((t) => t.includes('评论')), '归档卷宗详情无评论 Tab', JSON.stringify(tabs2))

  const realErrors = errors.filter((e) => !e.includes('404 (Not Found)'))
  log(realErrors.length === 0, '无 JS 报错（已排除预期的 404 探测）', JSON.stringify(realErrors).slice(0, 300))
  const failed = results.filter((r) => !r.ok).length
  console.log(`\n== ${results.length - failed}/${results.length} passed ==`)
  await browser.close()
  process.exit(failed ? 1 : 0)
})()
