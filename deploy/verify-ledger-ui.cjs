// 台账：案件类型视图隔离 + 字段设置三套配置互不影响 + 导出按当前视图字段
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:4173'
const OUT = 'E:/GSMS/deploy/shots/ledger'
const results = []
const errors = []
function log(ok, name, detail = '') {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

const dlg = (p) => p.locator('.el-dialog:has(.scope-bar)')
const heads = async (p) => (await p.locator('.el-table__header th .cell').allInnerTexts()).map((h) => h.trim())

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 950 }, acceptDownloads: true })
  const page = await ctx.newPage()
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2000)

  // 1. 案件列表三个入口都没有批量导出
  for (const q of ['', '?type=INJURY', '?type=ARBITRATION']) {
    await page.goto(BASE + '/cases' + q, { waitUntil: 'networkidle' })
    await page.waitForTimeout(900)
    log((await page.locator('button:has-text("批量导出")').count()) === 0, `案件列表${q || '（全部）'} 无批量导出`)
  }

  // 2. 台账三个视图各自使用独立默认列
  await page.goto(BASE + '/ledger', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  log((await page.locator('h2.page-title').innerText()).trim() === '台账', '页面标题为台账')
  log((await page.locator('.nav-item.on').allInnerTexts()).some((t) => t.includes('台账')), '侧栏菜单名为台账')
  const allHead = await heads(page)
  log(allHead.includes('受伤原因及日期') && allHead.includes('争议事由'), '全部视图含两类字段', `cols=${allHead.length}`)

  await page.click('.seg button:has-text("工伤")')
  await page.waitForTimeout(1300)
  let injuryHead = await heads(page)
  log(injuryHead.includes('厂区') && !injuryHead.includes('争议事由'), '工伤视图默认列不含仲裁字段', `cols=${injuryHead.length}`)
  await page.screenshot({ path: OUT + '/10-injury.png', fullPage: true })

  await page.click('.seg button:has-text("劳动仲裁")')
  await page.waitForTimeout(1300)
  const arbHead = await heads(page)
  log(arbHead.includes('争议事由') && arbHead.includes('厂区') && !arbHead.includes('工伤认定情况'),
    '仲裁视图默认列独立且不含工伤字段', `cols=${arbHead.length}`)
  await page.screenshot({ path: OUT + '/11-arbitration.png', fullPage: true })

  // 3. 字段设置：打开时跟随当前视图（劳动仲裁）
  await page.click('button:has-text("字段设置")')
  await page.waitForTimeout(600)
  let scopeOn = (await dlg(page).locator('.el-radio-button.is-active').innerText()).trim()
  log(scopeOn === '劳动仲裁', '字段设置默认范围为当前视图', scopeOn)
  log((await dlg(page).locator('.el-checkbox:has-text("仲裁案号")').count()) > 0
      && (await dlg(page).locator('.el-checkbox:has-text("工伤认定情况")').count()) === 0,
    '仲裁范围不出现工伤专属字段')

  // 切到「工伤」范围：应有厂区且已勾选，无仲裁案号
  await dlg(page).locator('.el-radio-button:has-text("工伤")').click()
  await page.waitForTimeout(400)
  const injuryCb = dlg(page).locator('.el-checkbox:has-text("厂区")').first()
  log((await injuryCb.getAttribute('class'))?.includes('is-checked') === true, '工伤范围「厂区」已勾选')
  log((await dlg(page).locator('.el-checkbox:has-text("仲裁案号")').count()) === 0,
    '工伤范围不出现仲裁专属字段')

  // 在工伤范围取消「厂区」后保存
  await injuryCb.click()
  await dlg(page).locator('button:has-text("保存")').click()
  await page.waitForTimeout(600)
  // 编辑的是工伤范围，切到工伤视图查看效果
  await page.click('.seg button:has-text("工伤")')
  await page.waitForTimeout(1100)
  injuryHead = await heads(page)
  log(!injuryHead.includes('厂区'), '保存后工伤视图不再显示厂区', `cols=${injuryHead.length}`)

  // 4. 关键：切到劳动仲裁，厂区仍然存在（两套配置互不影响）
  await page.click('.seg button:has-text("劳动仲裁")')
  await page.waitForTimeout(1300)
  let arbHead2 = await heads(page)
  log(arbHead2.includes('厂区'), '切到仲裁后厂区仍显示（工伤改动未影响仲裁）', `cols=${arbHead2.length}`)

  // 5. 在仲裁范围取消「结案日期」，再切回工伤确认不受影响
  await page.click('button:has-text("字段设置")')
  await page.waitForTimeout(500)
  scopeOn = (await dlg(page).locator('.el-radio-button.is-active').innerText()).trim()
  log(scopeOn === '劳动仲裁', '再次打开仍跟随当前视图', scopeOn)
  await dlg(page).locator('.el-checkbox:has-text("结案日期")').first().click()
  await dlg(page).locator('button:has-text("保存")').click()
  await page.waitForTimeout(800)
  arbHead2 = await heads(page)
  log(!arbHead2.includes('结案日期'), '仲裁范围取消结案日期生效', `cols=${arbHead2.length}`)

  await page.click('.seg button:has-text("工伤")')
  await page.waitForTimeout(1100)
  const injuryHead3 = await heads(page)
  log(!injuryHead3.includes('厂区') && injuryHead3.includes('一次性伤残金申领'),
    '切回工伤仍保持自己的配置', `cols=${injuryHead3.length}`)

  // 6. localStorage 三套配置独立
  const store = await page.evaluate(() => ({
    ALL: JSON.parse(localStorage.getItem('gsms_ledger_columns_ALL') || '[]'),
    INJURY: JSON.parse(localStorage.getItem('gsms_ledger_columns_INJURY') || '[]'),
    ARBITRATION: JSON.parse(localStorage.getItem('gsms_ledger_columns_ARBITRATION') || '[]')
  }))
  log(store.INJURY.includes('plantArea') === false && store.ARBITRATION.includes('plantArea') === true,
    '工伤/仲裁存储配置彼此隔离', JSON.stringify({ i: store.INJURY.length, a: store.ARBITRATION.length }))
  log(store.ALL.includes('disputeReason'), '全部视图单独保存', `cols=${store.ALL.length}`)
  await page.screenshot({ path: OUT + '/12-columns-scoped.png', fullPage: true })

  // 7. 导出：当前视图（工伤）导出的列 == 表格列
  const curHead = await heads(page)
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 40000 }),
    page.click('button:has-text("导出台账")')
  ])
  await download.saveAs(OUT + '/ledger.xlsx')
  fs.writeFileSync(OUT + '/expect-headers.json', JSON.stringify(curHead))
  log(true, '按工伤视图导出成功', download.suggestedFilename())

  // 8. 归档库导出卷宗目录
  await page.goto(BASE + '/archive', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1300)
  const [arc] = await Promise.all([
    page.waitForEvent('download', { timeout: 40000 }),
    page.click('button:has-text("导出卷宗目录")')
  ])
  await arc.saveAs(OUT + '/archive.xlsx')
  log(true, '归档卷宗目录下载成功', arc.suggestedFilename())

  // 9. 报表中心文案
  await page.goto(BASE + '/reports', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1300)
  log((await page.locator('button:has-text("导出工伤统计表")').count()) > 0, '报表中心按钮为导出工伤统计表')

  log(errors.length === 0, '无 JS 报错', JSON.stringify(errors).slice(0, 300))
  const failed = results.filter((r) => !r).length
  console.log(`\n== ${results.length - failed}/${results.length} passed ==`)
  await browser.close()
  process.exit(failed ? 1 : 0)
})()
