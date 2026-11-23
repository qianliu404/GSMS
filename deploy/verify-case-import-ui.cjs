// 历史案件批量导入：预检 → 修正提示 → 确认导入 → 列表可见
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/case-import'
const VALID = 'E:/GSMS/deploy/tmp/valid.xlsx'
const BAD = 'E:/GSMS/deploy/tmp/bad.xlsx'
const results = []
const log = (ok, name, detail = '') => {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const p = await (await b.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()
  const errs = []
  p.on('pageerror', (e) => errs.push(e.message))
  await p.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await p.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await p.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await p.click('button:has-text("登 录")')
  await p.waitForTimeout(2200)
  await p.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1500)

  log((await p.locator('button:has-text("批量导入")').count()) > 0, '列表页有批量导入入口')

  await p.click('button:has-text("批量导入")')
  await p.waitForTimeout(600)
  log((await p.locator('.el-dialog:has-text("历史案件批量导入")').count()) > 0, '打开导入弹窗')

  // 上传错误文件：应给出逐行问题且禁止导入
  await p.setInputFiles('.el-dialog input[type=file]', BAD)
  await p.waitForTimeout(1800)
  const badState = await p.evaluate(() => ({
    summary: document.querySelector('.imp-summary')?.innerText.replace(/\n/g, ' | ') || '',
    rows: [...document.querySelectorAll('.el-dialog .el-table__body tr')].length,
    tip: document.querySelector('.imp-tip')?.innerText.trim() || '',
    confirmDisabled: !!document.querySelector('.el-dialog__footer .el-button--primary')?.disabled
  }))
  log(/有问题\s*3\s*行/.test(badState.summary), '错误文件显示问题行数', badState.summary)
  log(badState.rows >= 3, '逐行列出问题', `${badState.rows} 行`)
  log(badState.confirmDisabled, '有问题时禁止导入', badState.tip)
  await p.locator('.el-dialog').screenshot({ path: OUT + '/preview-error.png' })

  // 换正确文件：应全部通过
  await p.setInputFiles('.el-dialog input[type=file]', VALID)
  await p.waitForTimeout(1800)
  const okState = await p.evaluate(() => ({
    summary: document.querySelector('.imp-summary')?.innerText.replace(/\n/g, ' | ') || '',
    tip: document.querySelector('.imp-tip')?.innerText.trim() || '',
    confirmDisabled: !!document.querySelector('.el-dialog__footer .el-button--primary')?.disabled
  }))
  log(/可导入\s*2\s*行/.test(okState.summary), '正确文件全部可导入', okState.summary)
  log(!okState.confirmDisabled, '校验通过后允许导入', okState.tip)
  await p.locator('.el-dialog').screenshot({ path: OUT + '/preview-ok.png' })

  await p.click('.el-dialog__footer .el-button--primary')
  await p.waitForTimeout(2500)
  const dialogGone = (await p.locator('.el-dialog:visible:has-text("历史案件批量导入")').count()) === 0
  log(dialogGone, '导入完成自动关闭弹窗')

  await p.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)
  const rowsText = await p.evaluate(() => document.querySelector('.d-table, .el-table')?.innerText || '')
  log(rowsText.includes('TEST-GY-001'), '列表出现导入的工伤案件')
  log(rowsText.includes('TEST-ZS-001'), '列表出现导入的仲裁案件')
  await p.screenshot({ path: OUT + '/list-after-import.png' })

  // 打开导入案件详情，检查档案与流程条
  await p.click('.el-table__body tr:has-text("TEST-GY-001") td:has-text("测试甲")').catch(() => {})
  const target = await p.evaluate(() => {
    const tr = [...document.querySelectorAll('.el-table__body tr')].find((r) => r.innerText.includes('TEST-GY-001'))
    return tr ? true : false
  })
  log(target, '找到导入的工伤案件行')
  const ids = await p.evaluate(() => {
    const tr = [...document.querySelectorAll('.el-table__body tr')].find((r) => r.innerText.includes('TEST-GY-001'))
    tr?.querySelector('td .link')?.click()
    return true
  })
  await p.waitForTimeout(1800)
  const detail = await p.evaluate(() => ({
    url: location.pathname,
    steps: [...document.querySelectorAll('.steps .sx')].map((s) => s.className).join(','),
    cells: [...document.querySelectorAll('.arc > div')].map((d) => d.innerText.replace(/\n/g, ':')).join(' | ')
  }))
  log(/\/cases\/\d+/.test(detail.url), '进入导入案件详情', detail.url)
  log(detail.cells.includes('90 天'), '档案带出导入的休假天数', detail.cells.slice(0, 120))
  log(detail.cells.includes('冲床挤压伤'), '档案带出受伤原因')
  log(detail.steps.includes('skip'), '未办理的历史节点显示为已跳过', detail.steps)
  await p.screenshot({ path: OUT + '/imported-detail.png', fullPage: true })

  log(errs.length === 0, '无 JS 报错', errs.join(' | ') || '无')
  await b.close()
  const pass = results.filter(Boolean).length
  console.log('='.repeat(44))
  console.log(`  通过: ${pass}   失败: ${results.length - pass}`)
  process.exit(pass === results.length ? 0 : 1)
})()
