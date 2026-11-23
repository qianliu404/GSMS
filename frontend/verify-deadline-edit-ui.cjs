// 案件详情：截止日期可编辑 + 提交按钮位置 界面校验
// 用法：node deploy/verify-deadline-edit-ui.cjs <工伤案件ID> [仲裁案件ID]
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/deadline-edit'
const CASE_ID = process.argv[2]
const ARB_ID = process.argv[3]
const results = []

function log(ok, name, detail = '') {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

const box = (page, sel) =>
  page.evaluate((s) => {
    const el = document.querySelector(s)
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width) }
  }, sel)

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="登录名"]', 'admin')
  await page.fill('input[placeholder="密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)

  await page.goto(`${BASE}/cases/${CASE_ID}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  await page.screenshot({ path: OUT + '/case-detail.png', fullPage: true })

  // ---------- 1. 步骤条不再有提交按钮 ----------
  const stepInfo = await page.evaluate(() => {
    const list = [...document.querySelectorAll('.steps .sx')]
    return {
      total: list.length,
      withButton: list.filter((s) => s.querySelector('.step-go, button')).length
    }
  })
  log(stepInfo.total >= 5, '流程节点正常渲染', `${stepInfo.total} 个`)
  log(stepInfo.withButton === 0, '步骤条已移除提交按钮', `${stepInfo.withButton} 个残留`)

  // ---------- 2. 提交按钮在保存草稿与推进之间 ----------
  const saveBox = await box(page, '.node-actions .act-save')
  const submitBox = await box(page, '.node-actions .act-submit')
  const goBox = await box(page, '.node-actions .act-go')
  log(!!submitBox, '办理区存在提交按钮', submitBox ? JSON.stringify(submitBox) : '未找到')
  log(
    !!saveBox && !!submitBox && !!goBox && saveBox.x < submitBox.x && submitBox.x < goBox.x,
    '提交位于保存草稿与推进按钮之间',
    `保存 ${saveBox?.x} / 提交 ${submitBox?.x} / 推进 ${goBox?.x}`
  )

  // ---------- 3. 截止日期在办理意见上方且可编辑 ----------
  const dueFieldY = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.card-body .el-form-item')]
    const target = items.find((i) => i.innerText.trim().startsWith('截止日期'))
    return target ? Math.round(target.getBoundingClientRect().y) : null
  })
  const opinionY = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.card-body .el-form-item')]
    const target = items.find((i) => i.innerText.includes('办理意见'))
    return target ? Math.round(target.getBoundingClientRect().y) : null
  })
  log(dueFieldY !== null, '办理区有截止日期字段', String(dueFieldY))
  log(dueFieldY !== null && opinionY !== null && dueFieldY < opinionY, '截止日期位于办理意见上方', `截止 ${dueFieldY} / 意见 ${opinionY}`)

  const editable = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.card-body .el-form-item')]
    const target = items.find((i) => i.innerText.trim().startsWith('截止日期'))
    const input = target?.querySelector('input')
    return input ? { disabled: input.disabled, value: input.value } : null
  })
  log(!!editable && !editable.disabled, '截止日期可编辑', JSON.stringify(editable))
  log(/^\d{4}-\d{2}-\d{2}$/.test(editable?.value || ''), '截止日期已带出默认值', editable?.value || '')

  const hint = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.card-body .el-form-item')]
    const target = items.find((i) => i.innerText.trim().startsWith('截止日期'))
    return target?.querySelector('.hint')?.innerText.trim() || ''
  })
  log(/剩余|超期/.test(hint), '截止日期带剩余天数提示', hint)

  // ---------- 4. 改期 -> 落库并刷新提示 ----------
  const original = editable.value
  const picker = page.locator('.el-form-item:has-text("截止日期") input').first()
  await picker.click()
  await picker.fill('2026-12-31')
  await picker.press('Enter')
  await page.waitForTimeout(1600)
  const afterEdit = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.card-body .el-form-item')]
    const target = items.find((i) => i.innerText.trim().startsWith('截止日期'))
    return {
      hint: target?.querySelector('.hint')?.innerText.trim() || '',
      reset: !!document.querySelector('.deadline-reset')
    }
  })
  log(afterEdit.hint.includes('已手动调整'), '调整后提示人工调整', afterEdit.hint)
  log(afterEdit.reset, '调整后出现「按时限重算」入口', String(afterEdit.reset))
  const headPill = await page.evaluate(
    () => document.querySelector('.card-head .pill.due')?.innerText.trim() || ''
  )
  log(headPill.includes('12-31'), '办理区截止日期同步为新值', headPill)
  await page.screenshot({ path: OUT + '/case-detail-edited.png', fullPage: true })

  // ---------- 5. 按时限重算，恢复自动值 ----------
  await page.click('.deadline-reset')
  await page.waitForTimeout(1600)
  const afterReset = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.card-body .el-form-item')]
    const target = items.find((i) => i.innerText.trim().startsWith('截止日期'))
    return {
      value: target?.querySelector('input')?.value || '',
      hint: target?.querySelector('.hint')?.innerText.trim() || ''
    }
  })
  log(afterReset.value === original, '重算后回到按时限推算的日期', `${afterReset.value} / 原 ${original}`)
  log(!afterReset.hint.includes('已手动调整'), '重算后不再标记人工调整', afterReset.hint)

  // ---------- 6. 移动端布局 ----------
  const mobile = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  const m = await mobile.newPage()
  m.on('pageerror', (e) => errors.push('mobile: ' + e.message))
  await m.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await m.fill('input[placeholder="登录名"]', 'admin')
  await m.fill('input[placeholder="密码"]', process.env.GSMS_PASSWORD || '')
  await m.click('button:has-text("登 录")')
  await m.waitForTimeout(2000)
  await m.goto(`${BASE}/cases/${CASE_ID}`, { waitUntil: 'networkidle' })
  await m.waitForTimeout(1600)
  await m.screenshot({ path: OUT + '/mobile-detail.png', fullPage: true })
  const mSubmit = await box(m, '.node-actions .act-submit')
  const mOverflow = await m.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  log(!!mSubmit, '移动端有提交按钮', mSubmit ? JSON.stringify(mSubmit) : '未找到')
  log(mOverflow <= 1, '移动端无横向溢出', `${mOverflow}px`)

  log(errors.length === 0, '无 JS 报错', errors.join(' | ') || '无')

  await browser.close()
  const pass = results.filter(Boolean).length
  console.log('='.repeat(46))
  console.log(`  通过: ${pass}   失败: ${results.length - pass}`)
  process.exit(pass === results.length ? 0 : 1)
})()
