// 期限中心 / 归档库 / 用户与角色 三页移动端布局校验
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
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true })
  const page = await ctx.newPage()

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)

  const noOverflow = async () => page.evaluate(() =>
    document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)
  const tableHidden = async () => page.evaluate(() =>
    getComputedStyle(document.querySelector('.d-table')).display === 'none')

  /* ---------- 期限中心 ---------- */
  await page.goto(BASE + '/deadlines', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1400)
  log(await noOverflow(), '期限中心：无横向溢出')
  log(await tableHidden(), '期限中心：表格隐藏')
  const dlCards = await page.locator('.m-dl-card').count()
  log(dlCards > 0, '期限中心：卡片列表渲染', `${dlCards} 张`)

  const dlBar = await page.evaluate(() => {
    const s = document.querySelector('.f-status').getBoundingClientRect()
    const t = document.querySelector('.f-type').getBoundingClientRect()
    const o = document.querySelector('.f-overdue').getBoundingClientRect()
    const r = document.querySelector('.m-reset').getBoundingClientRect()
    return {
      firstRow: Math.abs(Math.round(s.top) - Math.round(t.top)) <= 2,
      secondRow: Math.abs(Math.round(o.top) - Math.round(r.top)) <= 2,
      allVisible: [s, t, o, r].every((x) => x.width > 0)
    }
  })
  log(dlBar.firstRow && dlBar.secondRow && dlBar.allVisible, '期限中心：筛选两行全露出（无折叠）', JSON.stringify(dlBar))

  const dlCard = await page.locator('.m-dl-card').first().innerText()
  log(/剩|超期|已完成/.test(dlCard), '期限中心：卡片含剩余/超期天数', dlCard.split('\n').slice(0, 5).join(' | '))
  log(dlCard.includes('督办') && dlCard.includes('登记原因'), '期限中心：卡片含督办/登记原因')

  const statCols = await page.evaluate(() => {
    const tops = [...document.querySelectorAll('.stat-card')].map((c) => Math.round(c.getBoundingClientRect().top))
    return { rows: new Set(tops).size, count: tops.length }
  })
  log(statCols.rows === 1 && statCols.count === 3, '期限中心：统计卡三列一行', JSON.stringify(statCols))
  await page.screenshot({ path: OUT + '/deadline.png', fullPage: true })

  /* ---------- 归档库 ---------- */
  await page.goto(BASE + '/archive', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1400)
  log(await noOverflow(), '归档库：无横向溢出')
  log(await tableHidden(), '归档库：表格隐藏')
  const arcCards = await page.locator('.m-arc-card').count()
  log(arcCards > 0, '归档库：卷宗卡片渲染', `${arcCards} 张`)

  const arcBar = await page.evaluate(() => {
    const kw = document.querySelector('.f-keyword').getBoundingClientRect()
    const f = document.querySelector('.f-filter').getBoundingClientRect()
    const adv = document.querySelector('.f-adv')
    return {
      sameRow: Math.abs(Math.round(kw.top) - Math.round(f.top)) <= 2,
      advHidden: getComputedStyle(adv).display === 'none',
      kwW: Math.round(kw.width)
    }
  })
  log(arcBar.sameRow && arcBar.kwW > 150, '归档库：搜索与筛选同行', JSON.stringify(arcBar))
  log(arcBar.advHidden, '归档库：年度筛选默认收起')

  const arcCard = await page.locator('.m-arc-card').first().innerText()
  log(/GY|ZS/.test(arcCard) && arcCard.includes('查看'), '归档库：卡片含案号与查看入口', arcCard.replace(/\n/g, ' | '))
  log(/卷宗|保管/.test(arcCard), '归档库：卡片含卷宗页数与保管期限')

  const headFull = await page.evaluate(() => {
    const btn = document.querySelector('.head-actions .el-button')
    const card = document.querySelector('.card')
    return Math.round(btn.getBoundingClientRect().width) >= Math.round(card.getBoundingClientRect().width) - 40
  })
  log(headFull, '归档库：页头导出按钮满宽')
  await page.screenshot({ path: OUT + '/archive.png', fullPage: true })

  /* ---------- 用户与角色 ---------- */
  await page.goto(BASE + '/system/users', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1400)
  log(await noOverflow(), '用户与角色：无横向溢出')
  log(await tableHidden(), '用户与角色：表格隐藏')
  const userCards = await page.locator('.m-user-card').count()
  log(userCards > 0, '用户与角色：卡片渲染', `${userCards} 张`)

  const userBar = await page.evaluate(() => {
    const kw = document.querySelector('.f-keyword').getBoundingClientRect()
    const f = document.querySelector('.f-filter').getBoundingClientRect()
    const q = document.querySelector('.f-query').getBoundingClientRect()
    return {
      oneRow: Math.abs(Math.round(kw.top) - Math.round(q.top)) <= 2 && Math.abs(Math.round(kw.top) - Math.round(f.top)) <= 2,
      kwW: Math.round(kw.width)
    }
  })
  log(userBar.oneRow && userBar.kwW > 120, '用户与角色：搜索/筛选/查询同一行', JSON.stringify(userBar))

  const uCard = await page.locator('.m-user-card').first().innerText()
  log(/编辑/.test(uCard) && /重置密码/.test(uCard) && /删除/.test(uCard), '用户与角色：卡片含全部操作')
  log(/启用|停用/.test(uCard), '用户与角色：卡片含账号状态')

  const btnGrid = await page.evaluate(() => {
    const btns = [...document.querySelectorAll('.m-user-card .m-l6 .el-button')].slice(0, 4)
    const w = btns.map((b) => Math.round(b.getBoundingClientRect().width))
    return w
  })
  log(btnGrid.every((w) => w > 100), '用户与角色：操作按钮两列平铺', btnGrid.join('/'))
  await page.screenshot({ path: OUT + '/users.png', fullPage: true })

  await browser.close()
  const failed = results.filter((r) => !r).length
  console.log('='.repeat(46))
  console.log(`  通过: ${results.length - failed}   失败: ${failed}`)
  process.exit(failed ? 1 : 0)
})()
