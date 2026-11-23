// 新建案件：关联说明 + 关联案件检索（截断提示、回显卡）
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

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(e.message))

  await login(page)
  await page.goto(BASE + '/cases/create', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)

  const relSelect = page.locator('.create-form .el-form-item').filter({ hasText: '关联案件' }).locator('input')
  // 1. 宽泛关键字：结果被截断并给出提示
  await relSelect.fill('GY2026')
  await page.waitForTimeout(1400)
  const wide = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
  const moreText = await page.locator('.el-select-dropdown:visible .rel-opt-more').innerText().catch(() => '')
  log(wide >= 30, '宽泛关键字返回足量候选', `${wide} 项`)
  log(/仅显示前/.test(moreText), '结果截断时提示收窄关键字', moreText)

  // 2. 精确案号：命中 1 条
  await relSelect.fill('GY2026110019')
  await page.waitForTimeout(1200)
  const narrow = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
  log(narrow === 1, '精确案号只返回 1 条', `${narrow} 条`)
  const optText = await page.locator('.el-select-dropdown:visible .rel-opt-row').first().innerText()
  log(/王五/.test(optText) && /工伤/.test(optText), '候选项展示姓名与类型', optText.replace(/\n/g, ' | '))
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
  await page.waitForTimeout(700)

  // 3. 回显卡 + 关联说明
  const picked = await page.locator('.rel-picked').innerText().catch(() => '')
  log(/王五/.test(picked) && /2026-10-15/.test(picked), '选中后回显关键信息', picked.replace(/\n/g, ' | ').slice(0, 80))
  const descLabel = await page.locator('.create-form .el-form-item').filter({ hasText: '关联说明' }).count()
  log(descLabel > 0, '出现「关联说明」字段')

  const descText = '测试关联说明：不服认定结论提起仲裁，两案合并取证'
  await page.locator('.create-form .el-form-item').filter({ hasText: '关联说明' }).locator('textarea').fill(descText)
  await page.screenshot({ path: 'E:/GSMS/deploy/shots/relations/create-with-desc.png' })

  await page.click('button:has-text("保存草稿")')
  await page.waitForTimeout(2500)
  const m = page.url().match(/\/cases\/(\d+)(?:\?|$)/)
  log(!!m, '带说明保存成功', page.url())
  const newId = m ? m[1] : null

  // 4. 关联页展示该说明
  await page.goto(BASE + '/relations', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  const rowText = await page.evaluate(() => document.querySelector('.rel-table')?.innerText || '')
  log(rowText.includes('不服认定结论提起仲裁'), '关联页显示自定义说明', rowText.includes('不服认定结论提起仲裁') ? '已显示' : rowText.slice(0, 60))
  await page.screenshot({ path: 'E:/GSMS/deploy/shots/relations/relations-with-desc.png' })

  // 5. 移动端布局
  const mp = await (await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })).newPage()
  await login(mp)
  await mp.goto(BASE + '/cases/create', { waitUntil: 'networkidle' })
  await mp.waitForTimeout(900)
  const overflow = await mp.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  log(overflow <= 1, '移动端无横向溢出', `${overflow}px`)
  await mp.screenshot({ path: 'E:/GSMS/deploy/shots/relations/create-desc-mobile.png' })

  log(errs.length === 0, '无 JS 报错', errs.join('|') || '无')
  console.log('NEW_CASE_ID=' + newId)
  await browser.close()
  const pass = results.filter(Boolean).length
  console.log('='.repeat(44))
  console.log(`  通过: ${pass}   失败: ${results.length - pass}`)
  process.exit(pass === results.length ? 0 : 1)
})()
