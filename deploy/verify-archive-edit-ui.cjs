// 档案：发生日期 + 案件描述；休假天数 / 履行款可自定义修改
// 用法：node verify-archive-edit-ui.cjs <工伤案件ID> <仲裁案件ID>
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/archive-edit'
const INJ = process.argv[2]
const ARB = process.argv[3]
const results = []

function log(ok, name, detail = '') {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

const cells = (page) =>
  page.evaluate(() =>
    [...document.querySelectorAll('.arc > div')].map((d) => ({
      k: d.querySelector('.k')?.innerText.trim() || '',
      v: d.querySelector('.v')?.innerText.trim() || '',
      span: d.classList.contains('span')
    }))
  )

async function login(page) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2000)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  await login(page)

  // ---------- 工伤档案 ----------
  await page.goto(`${BASE}/cases/${INJ}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  let list = await cells(page)
  const keys = list.map((c) => c.k)
  log(keys.includes('发生日期'), '工伤档案有发生日期', keys.join(' / '))
  log(keys.includes('案件描述'), '工伤档案有案件描述', list.find((c) => c.k === '案件描述')?.v || '')
  log(list.find((c) => c.k === '案件描述')?.span === true, '案件描述独占整行', '')
  await page.screenshot({ path: OUT + '/injury-archive.png', fullPage: true })

  // 编辑休假天数
  await page.click('.card-head button:has-text("编辑档案")')
  await page.waitForTimeout(600)
  const hasRest = await page.locator('.el-dialog:visible .el-form-item:has-text("休假天数")').count()
  log(hasRest > 0, '编辑弹窗有休假天数', String(hasRest))
  const restInput = page.locator('.el-dialog:visible .el-form-item:has-text("休假天数") input').first()
  const before = await restInput.inputValue()
  await restInput.fill('120')
  const descInput = page.locator('.el-dialog:visible .el-form-item:has-text("案件描述") textarea').first()
  await descInput.fill('设备故障导致右手挤压伤，已送医治疗，需观察恢复情况。')
  await page.click('.el-dialog:visible button:has-text("保存")')
  await page.waitForTimeout(2000)

  list = await cells(page)
  const restCell = list.find((c) => c.k === '休假天数')?.v || ''
  const descCell = list.find((c) => c.k === '案件描述')?.v || ''
  log(restCell === '120 天', '档案休假天数可修改并保存', `${before} -> ${restCell}`)
  log(descCell.includes('设备故障导致右手挤压伤'), '案件描述保存后回显', descCell)
  await page.screenshot({ path: OUT + '/injury-archive-edited.png', fullPage: true })

  // 再次打开，确认落库不是只有前端
  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  list = await cells(page)
  log((list.find((c) => c.k === '休假天数')?.v || '') === '120 天', '刷新后休假天数仍为 120', '')

  await page.click('.card-head button:has-text("编辑档案")')
  await page.waitForTimeout(600)
  const restVal = await page.locator('.el-dialog:visible .el-form-item:has-text("休假天数") input').first().inputValue()
  log(restVal === '120', '编辑弹窗回填休假天数', restVal)
  await page.click('.el-dialog:visible button:has-text("取消")')
  await page.waitForTimeout(400)

  // ---------- 仲裁档案 ----------
  await page.goto(`${BASE}/cases/${ARB}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  list = await cells(page)
  const arbKeys = list.map((c) => c.k)
  log(arbKeys.includes('发生日期'), '仲裁档案有发生日期', arbKeys.join(' / '))
  log(arbKeys.includes('案件描述'), '仲裁档案有案件描述', '')
  log(arbKeys.includes('履行款'), '仲裁档案有履行款', list.find((c) => c.k === '履行款')?.v || '')
  await page.screenshot({ path: OUT + '/arb-archive.png', fullPage: true })

  await page.click('.card-head button:has-text("编辑档案")')
  await page.waitForTimeout(600)
  const payInput = page.locator('.el-dialog:visible .el-form-item:has-text("履行款") input').first()
  const payBefore = await payInput.inputValue()
  await payInput.fill('8888')
  await page.click('.el-dialog:visible button:has-text("保存")')
  await page.waitForTimeout(2000)
  list = await cells(page)
  const payCell = list.find((c) => c.k === '履行款')?.v || ''
  log(payCell.includes('8,888'), '履行款可修改并保存', `${payBefore} -> ${payCell}`)
  await page.screenshot({ path: OUT + '/arb-archive-edited.png', fullPage: true })

  // ---------- 移动端 ----------
  const mctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  const m = await mctx.newPage()
  m.on('pageerror', (e) => errors.push('mobile: ' + e.message))
  await login(m)
  await m.goto(`${BASE}/cases/${INJ}`, { waitUntil: 'networkidle' })
  await m.waitForTimeout(1600)
  const mDesc = await m.evaluate(() => {
    const d = [...document.querySelectorAll('.arc > div')].find((x) => x.innerText.includes('案件描述'))
    return d ? d.innerText.replace(/\n/g, ' | ') : ''
  })
  log(!!mDesc, '移动端档案显示案件描述', mDesc)
  const mOverflow = await m.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  log(mOverflow <= 1, '移动端无横向溢出', `${mOverflow}px`)
  await m.screenshot({ path: OUT + '/mobile-archive.png', fullPage: true })

  log(errors.length === 0, '无 JS 报错', errors.join(' | ') || '无')
  await browser.close()
  const pass = results.filter(Boolean).length
  console.log('='.repeat(46))
  console.log(`  通过: ${pass}   失败: ${results.length - pass}`)
  process.exit(pass === results.length ? 0 : 1)
})()
