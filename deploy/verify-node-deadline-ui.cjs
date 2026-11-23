// 案件详情：节点截止日期 / 提交按钮 / 鉴定休假天数 / 履行款 界面校验
// 用法：node deploy/verify-node-deadline-ui.cjs <工伤案件ID> <仲裁案件ID>
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/deadline'
const INJ = process.argv[2]
const ARB = process.argv[3]
const results = []

function log(ok, name, detail = '') {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)

  // ---------- 工伤：鉴定节点 ----------
  await page.goto(`${BASE}/cases/${INJ}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  await page.screenshot({ path: OUT + '/case-appraisal.png', fullPage: true })

  const steps = await page.evaluate(() => {
    const list = [...document.querySelectorAll('.steps .sx')]
    const bar = [...document.querySelectorAll('.node-actions .el-button')].map((b) => b.innerText.trim())
    return {
      total: list.length,
      withButton: list.filter((s) => s.querySelector('button')).length,
      currentText: (list.find((s) => s.classList.contains('cur'))?.querySelector('.tm')?.innerText || '').trim(),
      bar
    }
  })
  log(steps.total >= 10, '流程节点全部渲染', `${steps.total} 个`)
  log(steps.withButton === 0, '步骤条不再内置提交按钮', `${steps.withButton} 个残留`)
  log(
    steps.bar.indexOf('提交') > steps.bar.indexOf('保存草稿') && steps.bar.indexOf('提交') < steps.bar.length - 1,
    '提交按钮位于保存草稿与推进按钮之间',
    steps.bar.join(' / ')
  )
  log(/截止\s*\d{2}-\d{2}/.test(steps.currentText), '当前节点显示截止日期', steps.currentText)

  // 当前节点（鉴定）应有休假天数字段
  const appraisalField = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.el-form-item')]
    const target = items.find((i) => i.innerText.includes('休假天数'))
    return target ? target.innerText.replace(/\n/g, ' | ') : ''
  })
  log(!!appraisalField, '鉴定节点有休假天数字段', appraisalField)

  // 档案里应有休假天数（未填时显示待登记）
  const archive = await page.evaluate(() => {
    const cells = [...document.querySelectorAll('.arc > div')]
    const cell = cells.find((c) => c.querySelector('.k')?.innerText.includes('休假天数'))
    return cell ? cell.querySelector('.v').innerText.trim() : ''
  })
  log(archive === '待登记', '档案显示休假天数（待登记）', archive)

  // 填写休假天数并提交
  const numberInput = page.locator('.el-form-item:has-text("休假天数") input').first()
  await numberInput.fill('90')

  await page.click('.node-actions .act-go')
  await page.waitForTimeout(600)
  await page.screenshot({ path: OUT + '/case-appraisal-dialog.png' })
  const dialogVisible = await page.locator('.el-dialog:visible').count()
  log(dialogVisible > 0, '提交后弹出办理确认', dialogVisible)
  await page.locator('.el-dialog:visible .el-button--primary').last().click()
  await page.waitForTimeout(2200)

  const after = await page.evaluate(() => {
    const d = document.querySelector('.detail-head .pill')?.innerText || ''
    const cells = [...document.querySelectorAll('.arc > div')]
    const cell = cells.find((c) => c.querySelector('.k')?.innerText.includes('休假天数'))
    return { status: d, rest: cell ? cell.querySelector('.v').innerText.trim() : '' }
  })
  log(after.status.includes('待遇处理'), '提交后进入下一节点', after.status)
  log(after.rest === '90 天', '档案已记录休假天数', after.rest)
  await page.screenshot({ path: OUT + '/case-after-submit.png', fullPage: true })

  // ---------- 仲裁：履行节点 ----------
  await page.goto(`${BASE}/cases/${ARB}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  await page.screenshot({ path: OUT + '/case-perform.png', fullPage: true })

  const perform = await page.evaluate(() => {
    const items = [...document.querySelectorAll('.el-form-item')]
    const field = items.find((i) => i.innerText.includes('履行款'))
    const cells = [...document.querySelectorAll('.arc > div')]
    const cell = cells.find((c) => c.querySelector('.k')?.innerText.includes('履行款'))
    return {
      field: field ? field.innerText.replace(/\n/g, ' | ') : '',
      archive: cell ? cell.querySelector('.v').innerText.trim() : '',
      due: document.querySelector('.card-head .pill.due')?.innerText.trim() || ''
    }
  })
  log(!!perform.field, '履行节点有履行款字段', perform.field)
  log(perform.archive === '待登记', '档案显示履行款（待登记）', perform.archive)
  log(/截止/.test(perform.due), '办理区提示节点截止日期', perform.due)

  log(errors.length === 0, '无 JS 报错', errors.join(' | ') || '无')

  await browser.close()
  const pass = results.filter(Boolean).length
  console.log('='.repeat(46))
  console.log(`  通过: ${pass}   失败: ${results.length - pass}`)
  process.exit(pass === results.length ? 0 : 1)
})()
