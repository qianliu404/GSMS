// 案件详情/列表：提交=开启节点计时、未开启不计超期、列表截止日期列
// 用法：node verify-node-open-ui.cjs <案件ID>
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/node-open'
const CASE_ID = process.argv[2]
const results = []

function log(ok, name, detail = '') {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)

  // ---------- 列表：截止日期列 ----------
  await page.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1800)
  const header = await page.evaluate(() => [...document.querySelectorAll('.el-table th')].map((t) => t.innerText.trim()))
  log(header.includes('截止日期'), '列表有截止日期列', header.join(' / '))
  const row = await page.evaluate((caseNo) => {
    const tr = [...document.querySelectorAll('.el-table tbody tr')].find((r) => r.innerText.includes(caseNo))
    return tr ? tr.innerText.replace(/\n/g, ' | ') : ''
  }, 'GY2026110019')
  log(/截止日期|2026-11-26/.test(row), '列表行显示截止日期', row)
  const listIdle = await page.evaluate((caseNo) => {
    const tr = [...document.querySelectorAll('.el-table tbody tr')].find((r) => r.innerText.includes(caseNo))
    return tr ? { idle: !!tr.querySelector('.due-cell.idle'), text: tr.querySelector('.due-cell')?.innerText.trim() || '' } : null
  }, 'GY2026110019')
  log(!!listIdle?.idle && listIdle.text.includes('未开启'), '未开启节点在列表标注「未开启」', JSON.stringify(listIdle))
  await page.screenshot({ path: OUT + '/case-list.png', fullPage: true })

  // ---------- 详情：未开启状态 ----------
  await page.goto(`${BASE}/cases/${CASE_ID}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  const idleState = await page.evaluate(() => ({
    pill: document.querySelector('.card-head .pill.due')?.innerText.trim() || '',
    hint: [...document.querySelectorAll('.el-form-item')]
      .find((i) => i.innerText.trim().startsWith('截止日期'))?.querySelector('.hint')?.innerText.trim() || '',
    submitText: document.querySelector('.node-actions .act-submit')?.innerText.trim() || '',
    submitDisabled: document.querySelector('.node-actions .act-submit')?.disabled,
    stepClass: document.querySelector('.steps .sx.cur .tm')?.className || ''
  }))
  log(idleState.pill.includes('未开启'), '详情提示未开启计时', idleState.pill)
  log(idleState.hint.includes('未开启计时'), '截止日期提示未开启', idleState.hint)
  log(idleState.submitText === '提交' && idleState.submitDisabled === false, '提交按钮可点', JSON.stringify(idleState))
  log(idleState.stepClass.includes('idle'), '步骤条当前节点截止日期置灰', idleState.stepClass)
  await page.screenshot({ path: OUT + '/detail-not-started.png', fullPage: true })

  // ---------- 提交 = 开启当前节点 ----------
  await page.click('.node-actions .act-submit')
  await page.waitForTimeout(1800)
  const opened = await page.evaluate(() => ({
    pill: document.querySelector('.card-head .pill.due')?.innerText.trim() || '',
    submitText: document.querySelector('.node-actions .act-submit')?.innerText.trim() || '',
    submitDisabled: document.querySelector('.node-actions .act-submit')?.disabled,
    status: document.querySelector('.detail-head .pill')?.innerText.trim() || ''
  }))
  log(opened.submitText === '已开启' && opened.submitDisabled === true, '提交后按钮变为已开启且不可重复点', JSON.stringify(opened))
  log(/剩余|超期/.test(opened.pill) && !opened.pill.includes('未开启'), '开启后开始按截止日期计时', opened.pill)
  log(/申报/.test(opened.status), '提交不会流转到下一节点', opened.status)
  await page.screenshot({ path: OUT + '/detail-opened.png', fullPage: true })

  // ---------- 列表同步 ----------
  await page.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  const listAfter = await page.evaluate((caseNo) => {
    const tr = [...document.querySelectorAll('.el-table tbody tr')].find((r) => r.innerText.includes(caseNo))
    return tr ? { idle: !!tr.querySelector('.due-cell.idle'), cls: tr.querySelector('.due-cell')?.className || '' } : null
  }, 'GY2026110019')
  log(listAfter && !listAfter.idle, '开启后列表不再显示未开启', JSON.stringify(listAfter))

  // ---------- 移动端 ----------
  const mctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  const m = await mctx.newPage()
  m.on('pageerror', (e) => errors.push('mobile: ' + e.message))
  await m.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await m.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await m.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await m.click('button:has-text("登 录")')
  await m.waitForTimeout(2000)
  await m.goto(`${BASE}/cases`, { waitUntil: 'networkidle' })
  await m.waitForTimeout(1600)
  const mCard = await m.evaluate(() => {
    const c = document.querySelector('.m-case')
    return c ? c.innerText.replace(/\n/g, ' | ') : ''
  })
  log(/截止/.test(mCard), '移动端卡片显示截止日期', mCard)
  const mOverflow = await m.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  log(mOverflow <= 1, '移动端无横向溢出', `${mOverflow}px`)
  await m.screenshot({ path: OUT + '/mobile-list.png', fullPage: true })

  log(errors.length === 0, '无 JS 报错', errors.join(' | ') || '无')
  await browser.close()
  const pass = results.filter(Boolean).length
  console.log('='.repeat(46))
  console.log(`  通过: ${pass}   失败: ${results.length - pass}`)
  process.exit(pass === results.length ? 0 : 1)
})()
