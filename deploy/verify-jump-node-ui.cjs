// 节点自选流转界面校验：推进下拉可跳过中间节点、退回下拉可回到更早节点
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:8080'
const API = BASE + '/api/v1'
const OUT = 'E:/GSMS/deploy/shots/jump'
const results = []

function log(ok, name, detail = '') {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1680, height: 1000 } })).newPage()

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2000)

  // 用页面内 token 建临时案件，避免界面建档流程干扰本次校验
  const token = await page.evaluate(() => localStorage.getItem('gsms_token') || localStorage.getItem('token'))
  const api = async (method, path, body) => page.evaluate(async ({ api, method, path, body, token }) => {
    const res = await fetch(api + path, {
      method,
      headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : undefined
    })
    return res.json()
  }, { api: API, method, path, body, token })

  const created = await api('POST', '/cases', {
    caseType: 'INJURY', caseName: '节点自选流转界面校验', employeeName: '界面校验',
    occurDate: '2026-11-02', handlerId: 'admin', plantArea: '华东二厂', workshopTeam: '冲压一班', submit: false
  })
  const caseId = created.data
  try {
    await api('POST', `/cases/${caseId}/submit`, { opinion: '提交立案' })
    await page.goto(`${BASE}/cases/${caseId}`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(1500)

    const pill = await page.locator('.detail-head .pill').first().innerText()
    log(pill.includes('立案'), '案件已进入立案节点', pill)

    // 推进弹窗：下拉含认定，选择后出现跳过提示
    await page.click('.node-actions .right button:has-text("推进至下一节点")')
    await page.waitForTimeout(700)
    const dialogTitle = await page.locator('.el-dialog__title').last().innerText()
    log(dialogTitle.includes('推进节点'), '推进弹窗标题正确', dialogTitle)

    await page.locator('.el-dialog .el-select').first().click()
    await page.waitForTimeout(500)
    const options = (await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').allInnerTexts()).map((t) => t.trim())
    log(options.includes('认定'), '推进下拉可选「认定」（跳过中间节点）', options.join('|'))
    log(options.length >= 5, '推进下拉列出全部下游节点', `${options.length} 个`)

    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item', { hasText: '认定' }).first().click()
    await page.waitForTimeout(400)
    const tip = await page.locator('.skip-tip').innerText().catch(() => '')
    log(tip.includes('调查取证') && tip.includes('申报'), '跳级提示列出被跳过的节点', tip)
    await page.screenshot({ path: OUT + '/01-transition-dialog.png', fullPage: true })

    await page.fill('.el-dialog textarea', '材料齐全，直接进入认定')
    await page.click('.el-dialog__footer button:has-text("确定")')
    await page.waitForTimeout(2000)
    const pill2 = await page.locator('.detail-head .pill').first().innerText()
    log(pill2.includes('认定'), '跳级推进后节点为认定', pill2)

    // 步骤条：被跳过的节点标记为「已跳过」而不是「已完成」
    const steps = await page.locator('.steps .sx').allInnerTexts()
    const skipped = steps.filter((s) => s.includes('已跳过'))
    log(skipped.length >= 2, '步骤条标记被跳过的节点', skipped.map((s) => s.split('\n')[1]).join('|'))

    // 退回弹窗：可选更早节点（立案）
    await page.click('.node-actions button:has-text("退回节点")')
    await page.waitForTimeout(600)
    const backTitle = await page.locator('.el-dialog__title').last().innerText()
    log(backTitle.includes('退回节点'), '退回弹窗标题正确', backTitle)
    await page.locator('.el-dialog .el-select').first().click()
    await page.waitForTimeout(500)
    const backOptions = (await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').allInnerTexts()).map((t) => t.trim())
    log(backOptions.includes('立案'), '退回下拉可选「立案」（跨节点）', backOptions.join('|'))
    await page.screenshot({ path: OUT + '/02-rollback-dialog.png', fullPage: true })

    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item', { hasText: '立案' }).first().click()
    await page.waitForTimeout(300)
    await page.fill('.el-dialog textarea', '认定材料不足，退回立案重办')
    await page.click('.el-dialog__footer button:has-text("确定")')
    await page.waitForTimeout(2000)
    const pill3 = await page.locator('.detail-head .pill').first().innerText()
    log(pill3.includes('立案'), '退回后节点回到立案', pill3)
    await page.screenshot({ path: OUT + '/03-after-rollback.png', fullPage: true })
  } finally {
    await api('DELETE', `/cases/${caseId}`)
    await api('DELETE', '/recycle-bin', { target: 'CASE', ids: [Number(caseId)] })
    await browser.close()
  }

  const failed = results.filter((r) => !r).length
  console.log('='.repeat(46))
  console.log(`  通过: ${results.length - failed}   失败: ${failed}`)
  process.exit(failed ? 1 : 0)
})()
