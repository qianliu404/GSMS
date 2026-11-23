// 浏览器端日期筛选验证：案件列表日期区间、清空、归档库年度、系统审计日期
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:4173'
const OUT = 'E:/GSMS/deploy/shots/date-fix'
const results = []
const errors = []

function log(ok, name, detail = '') {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

async function pickRange(page, startPh, endPh, from, to) {
  const s = page.locator(`input[placeholder="${startPh}"]`)
  const e = page.locator(`input[placeholder="${endPh}"]`)
  await s.click()
  await s.fill(from)
  await s.press('Enter')
  await page.waitForTimeout(300)
  await e.click()
  await e.fill(to)
  await e.press('Enter')
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 950 } })
  const page = await ctx.newPage()
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2000)

  // 1. 案件列表：选择发生日期区间
  await page.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  const [caseResp] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/cases?') && r.url().includes('occurDateFrom=2026-11-03')),
    pickRange(page, '发生日期', '止', '2026-11-03', '2026-11-21')
  ])
  const caseBody = await caseResp.json()
  log(caseResp.status() === 200 && caseBody.code === 0, '案件列表日期筛选接口成功',
    `http=${caseResp.status()} code=${caseBody.code} msg=${caseBody.message}`)
  const rows = (caseBody.data && caseBody.data.list) || []
  const dates = rows.map((r) => r.occurDate).filter(Boolean)
  log(rows.length > 0 && dates.every((d) => d >= '2026-11-03' && d <= '2026-11-21'),
    '表格数据落在所选区间', `rows=${rows.length} dates=${dates.slice(0, 3).join(',')}`)
  await page.waitForTimeout(700)
  log((await page.locator('.el-message--error').count()) === 0, '页面无错误提示')
  log((await page.locator('.el-table__body tr').count()) > 0, '表格正常渲染')
  await page.screenshot({ path: OUT + '/01-cases-date-range.png', fullPage: true })

  // 2. 重置后仍正常（日期参数为空串）
  const [resetResp] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/cases?') && r.url().includes('pageNum=1')),
    page.click('button:has-text("重置")')
  ])
  const resetBody = await resetResp.json()
  log(resetResp.status() === 200 && resetBody.code === 0, '重置日期后接口成功',
    resetResp.url().split('/api/v1/cases?')[1])

  // 3. 归档库按年度筛选（内部拼 yyyy-01-01 ~ yyyy-12-31）
  await page.goto(BASE + '/archive', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  const [arcResp] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/cases?') && r.url().includes('occurDateFrom=2026-01-01')),
    (async () => {
      await page.locator('.el-select').filter({ hasText: '全部年度' }).first().click()
      await page.locator('.el-select-dropdown__item:has-text("2026")').first().click()
    })()
  ])
  const arcBody = await arcResp.json()
  log(arcResp.status() === 200 && arcBody.code === 0, '归档库年度筛选接口成功',
    arcResp.url().split('/api/v1/cases?')[1])
  await page.waitForTimeout(600)
  await page.screenshot({ path: OUT + '/02-archive-year.png', fullPage: true })

  // 4. 系统审计日期区间
  await page.goto(BASE + '/system/audit-logs', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1000)
  if (await page.locator('input[placeholder="开始日期"]').count()) {
    const [auditResp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/audit-logs?') && r.url().includes('dateFrom=2026-01-01')),
      pickRange(page, '开始日期', '结束日期', '2026-01-01', '2026-12-31')
    ])
    const auditBody = await auditResp.json()
    log(auditResp.status() === 200 && auditBody.code === 0, '审计日志日期筛选接口成功',
      auditResp.url().split('/api/v1/audit-logs?')[1])
    await page.screenshot({ path: OUT + '/03-audit-date-range.png', fullPage: true })
  } else {
    log(false, '未找到审计日志日期选择器')
  }

  log(errors.length === 0, '无 JS 报错', JSON.stringify(errors).slice(0, 250))
  const failed = results.filter((r) => !r).length
  console.log(`\n== ${results.length - failed}/${results.length} passed ==`)
  await browser.close()
  process.exit(failed ? 1 : 0)
})()
