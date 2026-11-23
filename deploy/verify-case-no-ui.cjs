// 立案案号可自定义：填了用填的，留空自动生成，重复要拦
const { chromium } = require('playwright-core')
const BASE = 'http://127.0.0.1:8080'
const results = []
const log = (ok, name, detail = '') => {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

async function login(page) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2000)
}

async function fillBase(page, name) {
  await page.fill('.create-form input[placeholder*="如：张三"]', name)
  await page.fill('.create-form input[placeholder="员工姓名"]', '界面测试')
  await page.fill('.create-form input[placeholder="请选择发生日期"]', '2026-11-05')
  await page.keyboard.press('Enter')
  await page.waitForTimeout(300)
  // 承办人
  await page.click('.create-form .el-form-item:has-text("承办人") .el-select')
  await page.waitForTimeout(400)
  await page.click('.el-select-dropdown:visible .el-select-dropdown__item:has-text("admin")')
  await page.waitForTimeout(300)
}

(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(e.message))

  await login(page)
  await page.goto(BASE + '/cases/create', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)

  const ph = await page.getAttribute('.create-form input[placeholder*="留空自动生成"]', 'placeholder')
  log(!!ph && /留空自动生成/.test(ph), '新建页有「案号」输入框且提示自动生成', ph || '未找到')

  // ---- 1. 自定义案号 ----
  const custom = 'UI-TEST-' + Date.now().toString().slice(-6)
  await page.fill('.create-form input[placeholder*="留空自动生成"]', custom)
  await fillBase(page, '自定义案号界面测试')
  await page.click('button:has-text("保存草稿")')
  await page.waitForTimeout(2200)
  const url1 = page.url()
  log(/\/cases\/\d+$/.test(url1), '保存后跳转到详情页', url1)
  const headText = await page.evaluate(() => document.body.innerText)
  log(headText.includes(custom), '详情页显示自定义案号', custom)

  // ---- 2. 留空自动生成 ----
  await page.goto(BASE + '/cases/create', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  const tip = await page.getAttribute('.create-form input[placeholder*="留空自动生成"]', 'placeholder')
  log(/GY\d{10}/.test(tip || ''), '占位提示带自动编号示例', tip || '')
  await fillBase(page, '自动案号界面测试')
  await page.click('button:has-text("保存草稿")')
  await page.waitForTimeout(2200)
  const autoNo = await page.evaluate(() => {
    const t = document.body.innerText
    const m = t.match(/[GZ]Y\d{10}|ZS\d{10}|GL\d{10}/)
    return m ? m[0] : ''
  })
  log(/^GY\d{10}$/.test(autoNo), '留空时系统自动生成案号', autoNo || '未找到')

  // ---- 3. 重复案号被拦 ----
  await page.goto(BASE + '/cases/create', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  await page.fill('.create-form input[placeholder*="留空自动生成"]', custom)
  await fillBase(page, '重复案号界面测试')
  await page.click('button:has-text("保存草稿")')
  await page.waitForTimeout(2000)
  const toast = await page.evaluate(() =>
    [...document.querySelectorAll('.el-message, .el-message-box')].map((e) => e.innerText.trim()).join(' | ')
  )
  log(/已存在/.test(toast), '重复案号被拦截并提示', toast || '无提示')
  log(/\/cases\/create/.test(page.url()), '重复案号时停留在新建页', page.url())

  await page.screenshot({ path: 'E:/GSMS/deploy/shots/case-no/create-dup.png' })

  // 移动端布局
  const mctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  const mp = await mctx.newPage()
  await login(mp)
  await mp.goto(BASE + '/cases/create', { waitUntil: 'networkidle' })
  await mp.waitForTimeout(900)
  const overflow = await mp.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  log(overflow <= 1, '移动端无横向溢出', `${overflow}px`)
  const mLabel = await mp.evaluate(() => {
    const item = [...document.querySelectorAll('.create-form .el-form-item')].find((i) =>
      i.innerText.startsWith('案号')
    )
    return item ? item.innerText.split('\n')[0] : ''
  })
  log(mLabel.includes('案号'), '移动端可见「案号」字段', mLabel)
  await mp.screenshot({ path: 'E:/GSMS/deploy/shots/case-no/create-mobile.png', fullPage: false })

  log(errs.length === 0, '无 JS 报错', errs.join('|') || '无')

  await browser.close()
  const pass = results.filter(Boolean).length
  console.log('='.repeat(44))
  console.log(`  通过: ${pass}   失败: ${results.length - pass}`)
  process.exit(pass === results.length ? 0 : 1)
})()
