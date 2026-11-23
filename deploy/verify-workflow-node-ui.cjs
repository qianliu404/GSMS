// 流程节点配置界面校验：规则说明、启停开关、首末节点保护、插入位置不含归档之后
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/workflow'
const results = []
const errors = []

function log(ok, name, detail = '') {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1680, height: 1000 } })).newPage()
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2000)

  await page.goto(BASE + '/system/workflow', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1800)
  await page.screenshot({ path: OUT + '/01-workflow.png', fullPage: true })

  const rule = await page.locator('.rule-body').first().innerText()
  log(rule.includes('自动接在前驱与原后继之间'), '规则说明含新增自动接链')
  log(rule.includes('停用后不再作为流转目标'), '规则说明含停用约束')

  const headers = (await page.locator('.el-table__header th .cell').allInnerTexts()).map((h) => h.trim())
  log(headers.includes('状态'), '表格增加状态列', headers.join('|'))

  const switches = page.locator('.el-table__body .el-switch')
  const switchCount = await switches.count()
  const rows = await page.locator('.el-table__body tr').count()
  log(switchCount > 0 && switchCount === rows, '每行一个启停开关', `switch=${switchCount} rows=${rows}`)

  const firstSwitch = switches.first()
  const firstDisabled = (await firstSwitch.getAttribute('class') || '').includes('is-disabled')
  log(firstDisabled, '草稿节点的开关禁用')

  // 打开第一张卡的新增节点弹窗，检查插入位置选项
  await page.locator('button:has-text("新增节点")').first().click()
  await page.waitForTimeout(700)
  const insertItem = page.locator('.el-dialog .el-form-item').filter({ hasText: '插入位置' }).first()
  log((await insertItem.count()) > 0, '弹窗包含插入位置')
  await insertItem.locator('.el-select').click()
  await page.waitForTimeout(500)
  const options = await page.locator('.el-select-dropdown__item:visible').allInnerTexts()
  log(options.length > 0 && !options.some((t) => t.includes('归档')), '插入位置不含「归档之后」',
    options.join('|'))
  log(options[options.length - 1].includes('结案'), '默认最后一项为归档前一个节点',
    options[options.length - 1])
  await page.screenshot({ path: OUT + '/02-insert-options.png' })
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)
  await page.locator('.el-dialog button:has-text("取消")').click()
  await page.waitForTimeout(400)

  // 停用开关只读确认：末行归档节点同样禁用
  const lastSwitch = switches.nth(switchCount - 1)
  const lastDisabled = (await lastSwitch.getAttribute('class') || '').includes('is-disabled')
  log(lastDisabled, '归档节点的开关禁用')

  log(errors.length === 0, '无 JS 报错', JSON.stringify(errors).slice(0, 250))
  const failed = results.filter((r) => !r).length
  console.log(`\n== ${results.length - failed}/${results.length} passed ==`)
  await browser.close()
  process.exit(failed ? 1 : 0)
})()
