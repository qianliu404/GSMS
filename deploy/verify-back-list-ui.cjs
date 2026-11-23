// 案件详情「返回列表」：按来源列表返回 + 侧栏保持原模块高亮 + 列表筛选/滚动复原
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

/** 高亮菜单文本（告警角标也是文字，取首行比对） */
const navOn = (page) =>
  page.evaluate(() =>
    [...document.querySelectorAll('.nav-item.on')]
      .map((e) => e.innerText.trim().split('\n')[0])
      .join('|')
  )
const title = (page) => page.evaluate(() => document.querySelector('.page-title')?.innerText.trim() || '')
const backText = (page) => page.evaluate(() => document.querySelector('.mb-12 button')?.innerText.trim() || '')

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(e.message))

  await login(page)

  // 1. 侧栏进「工伤案件」列表
  await page.click('.nav-item:has-text("工伤案件")')
  await page.waitForTimeout(1200)
  log(page.url().includes('type=INJURY'), '侧栏进入工伤案件列表', page.url())
  log((await title(page)) === '工伤案件', '工伤列表标题正确', await title(page))
  log((await navOn(page)) === '工伤案件', '工伤列表侧栏高亮工伤', await navOn(page))

  // 2. 进详情：URL 带来源，侧栏不跑到案件列表
  const firstNo = await page.evaluate(
    () => document.querySelector('.d-table .el-table__body tr .cell-no')?.innerText.trim() || ''
  )
  await page.locator('.d-table .el-table__body tr').first().click()
  await page.waitForTimeout(1500)
  log(/\/cases\/\d+/.test(page.url()), '点击行进入案件详情', page.url())
  log(/from=.*cases/.test(page.url()) && /INJURY/.test(page.url()), '详情 URL 记录来源为工伤列表', page.url())
  log((await navOn(page)) === '工伤案件', '详情页侧栏仍高亮工伤', await navOn(page))
  log((await backText(page)) === '‹ 返回列表', '返回按钮文案', await backText(page))

  // 3. 返回：回到工伤列表而非全部案件
  await page.click('.mb-12 button')
  await page.waitForTimeout(1400)
  log(page.url().includes('/cases?type=INJURY'), '返回后回到工伤列表', page.url())
  log((await title(page)) === '工伤案件', '返回后标题为工伤案件', await title(page))
  log((await navOn(page)) === '工伤案件', '返回后侧栏仍高亮工伤', await navOn(page))
  const firstNoBack = await page.evaluate(
    () => document.querySelector('.d-table .el-table__body tr .cell-no')?.innerText.trim() || ''
  )
  log(!!firstNo && firstNoBack === firstNo, '返回后列表数据一致', `${firstNo} / ${firstNoBack}`)

  // 4. 列表筛选（关键字）随返回复原
  const kw = firstNo.slice(0, 6)
  await page.fill('.f-keyword input', kw)
  await page.press('.f-keyword input', 'Enter')
  await page.waitForTimeout(1200)
  await page.locator('.d-table .el-table__body tr').first().click()
  await page.waitForTimeout(1500)
  await page.click('.mb-12 button')
  await page.waitForTimeout(1400)
  const kwBack = await page.inputValue('.f-keyword input')
  log(kwBack === kw, '返回后关键字筛选复原', `${kw} / ${kwBack}`)
  log((await navOn(page)) === '工伤案件', '带筛选返回仍高亮工伤', await navOn(page))

  // 5. 清掉筛选，测滚动位置复原
  await page.fill('.f-keyword input', '')
  await page.press('.f-keyword input', 'Enter')
  await page.waitForTimeout(1200)
  await page.evaluate(() => {
    const box = document.querySelector('.content')
    if (box) box.scrollTop = 900
  })
  await page.waitForTimeout(300)
  const rows = page.locator('.d-table .el-table__body tr')
  const lastRow = rows.last()
  await lastRow.scrollIntoViewIfNeeded()
  await page.waitForTimeout(300)
  const scrollBefore = await page.evaluate(() => document.querySelector('.content')?.scrollTop || 0)
  await page.locator('.d-table .el-table__body tr .el-button:has-text("详情")').last().click()
  await page.waitForTimeout(1600)
  await page.click('.mb-12 button')
  await page.waitForTimeout(1800)
  const scrollAfter = await page.evaluate(() => document.querySelector('.content')?.scrollTop || 0)
  log(
    scrollBefore > 200 && Math.abs(scrollAfter - scrollBefore) <= 8,
    '返回后列表滚回原位置',
    `${scrollBefore} -> ${scrollAfter}`
  )

  // 6. 全部案件列表：进详情高亮「案件列表」，返回全部案件
  await page.click('.nav-item:has-text("案件列表")')
  await page.waitForTimeout(1200)
  log(!page.url().includes('type='), '侧栏进入全部案件列表', page.url())
  await page.locator('.d-table .el-table__body tr').first().click()
  await page.waitForTimeout(1500)
  log((await navOn(page)) === '案件列表', '全部案件详情高亮案件列表', await navOn(page))
  await page.click('.mb-12 button')
  await page.waitForTimeout(1400)
  log(!page.url().includes('type='), '返回全部案件列表', page.url())
  log((await navOn(page)) === '案件列表', '返回后高亮案件列表', await navOn(page))

  // 7. 台账进详情：返回到台账，侧栏不跑
  await page.click('.nav-item:has-text("台账")')
  await page.waitForTimeout(1500)
  const ledgerRows = page.locator('.d-table .el-table__body tr')
  log((await ledgerRows.count()) > 0, '台账有数据行', `${await ledgerRows.count()} 行`)
  if ((await ledgerRows.count()) > 0) {
    await ledgerRows.first().dblclick()
    await page.waitForTimeout(1600)
    log((await navOn(page)) === '台账', '台账进详情侧栏高亮台账', await navOn(page))
    log((await backText(page)) === '‹ 返回台账', '按钮文案为返回台账', await backText(page))
    await page.click('.mb-12 button')
    await page.waitForTimeout(1400)
    log(page.url().includes('/ledger'), '台账进详情返回台账', page.url())
    log((await navOn(page)) === '台账', '返回后高亮台账', await navOn(page))
  }

  // 8. 工作量：无 JS 报错
  log(!errs.length, '无前端脚本错误', errs.slice(0, 3).join(' | '))

  const pass = results.filter(Boolean).length
  console.log(`\n${pass}/${results.length} 通过`)
  await browser.close()
  process.exit(pass === results.length ? 0 : 1)
})()
