// 新增案件页移动端布局校验：单列、标签上置、类型分段一行、无横向溢出、底部操作吸底
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/mobile'
const results = []

function log(ok, name, detail = '') {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true
  })
  const page = await ctx.newPage()

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2000)

  await page.goto(BASE + '/cases/create', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: OUT + '/create-01-top.png', fullPage: true })

  // 1. 无横向溢出
  const overflow = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    client: document.documentElement.clientWidth
  }))
  log(overflow.scroll <= overflow.client + 1, '页面无横向溢出', `${overflow.scroll} vs ${overflow.client}`)

  // 2. 标签上置
  const labelPos = await page.evaluate(() => {
    const item = document.querySelector('.create-form .el-form-item')
    return item ? item.className : ''
  })
  log(labelPos.includes('label-top'), '窄屏标签改为上置', labelPos)

  // 3. 单列：所有 el-col 宽度接近容器宽度
  const colWidths = await page.evaluate(() => {
    const cols = [...document.querySelectorAll('.create-form .el-col')]
    return cols.map((c) => Math.round(c.getBoundingClientRect().width))
  })
  const cardWidth = await page.evaluate(() =>
    Math.round(document.querySelector('.create-form').getBoundingClientRect().width))
  const allFull = colWidths.every((w) => w >= cardWidth - 40)
  log(allFull, '字段栅格在窄屏为单列', `col=${Math.min(...colWidths)}~${Math.max(...colWidths)} card=${cardWidth}`)

  // 4. 案件类型三段一行
  const typeRow = await page.evaluate(() => {
    const btns = [...document.querySelectorAll('.type-group .el-radio-button')]
    const tops = btns.map((b) => Math.round(b.getBoundingClientRect().top))
    return { count: btns.length, rows: new Set(tops).size }
  })
  log(typeRow.count === 3 && typeRow.rows === 1, '案件类型三个按钮同一行', `${typeRow.count} 个 / ${typeRow.rows} 行`)

  // 5. 输入框宽度足够（不小于卡片宽度 - 40）
  const inputWidth = await page.evaluate(() => {
    const el = document.querySelector('.create-form .el-input')
    return Math.round(el.getBoundingClientRect().width)
  })
  log(inputWidth >= cardWidth - 40, '输入框占满可用宽度', `${inputWidth} / ${cardWidth}`)

  // 6. 底栏吸底：滚动容器（.content）滚到中部时，操作按钮仍贴在视口底部
  await page.evaluate(() => {
    const box = document.querySelector('.content')
    box.scrollTop = 700
  })
  await page.waitForTimeout(500)
  const sticky = await page.evaluate(() => {
    const bar = document.querySelector('.actions')
    const r = bar.getBoundingClientRect()
    return {
      bottom: Math.round(r.bottom),
      viewport: window.innerHeight,
      position: getComputedStyle(bar).position,
      scrolled: document.querySelector('.content').scrollTop
    }
  })
  log(sticky.scrolled > 300, '页面已滚动', `scrollTop=${sticky.scrolled}`)
  log(sticky.position === 'fixed' && Math.abs(sticky.bottom - sticky.viewport) <= 2,
      '提交操作栏固定在屏幕底部', `bottom=${sticky.bottom} vh=${sticky.viewport} ${sticky.position}`)

  // 6.1 滚到底部时最后一项不被底栏遮挡
  await page.evaluate(() => {
    const box = document.querySelector('.content')
    box.scrollTop = box.scrollHeight
  })
  await page.waitForTimeout(500)
  const overlap = await page.evaluate(() => {
    const bar = document.querySelector('.actions').getBoundingClientRect()
    const items = [...document.querySelectorAll('.create-form .el-form-item')]
    const last = items[items.length - 1].getBoundingClientRect()
    return Math.round(bar.top - last.bottom)
  })
  log(overlap >= 0, '最后一项未被底栏遮挡', `间距 ${overlap}px`)
  await page.screenshot({ path: OUT + '/create-02-scrolled.png' })

  // 6.1 日期控件有占位提示，避免出现空白字段
  const datePlaceholder = await page.evaluate(() =>
    document.querySelector('.create-form .el-date-editor input').getAttribute('placeholder'))
  log(!!datePlaceholder, '日期控件带占位提示', datePlaceholder)

  // 6.2 窄屏去掉与标签重复的分组标题
  const dividerVisible = await page.evaluate(() => {
    const d = document.querySelector('.type-divider')
    return d ? getComputedStyle(d).display : 'none'
  })
  log(dividerVisible === 'none', '重复的分组标题在窄屏隐藏', dividerVisible)

  // 7. 切到仲裁：扩展字段同样单列不溢出
  await page.click('.type-group .el-radio-button:nth-child(2)')
  await page.waitForTimeout(800)
  const overflow2 = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    client: document.documentElement.clientWidth
  }))
  log(overflow2.scroll <= overflow2.client + 1, '仲裁分支同样无横向溢出', `${overflow2.scroll} vs ${overflow2.client}`)
  await page.screenshot({ path: OUT + '/create-03-arbitration.png', fullPage: true })

  await browser.close()
  const failed = results.filter((r) => !r).length
  console.log('='.repeat(46))
  console.log(`  通过: ${results.length - failed}   失败: ${failed}`)
  process.exit(failed ? 1 : 0)
})()
