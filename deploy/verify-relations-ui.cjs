// 争议关联：新建案件的关联带入 + 关联页展示/解除/导出
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
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(e.message))

  await login(page)
  await page.goto(BASE + '/cases/create', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)

  // 1. 类型里去掉了关联争议
  const typeText = await page.evaluate(() =>
    [...document.querySelectorAll('.create-form .el-radio-button')].map((e) => e.innerText.trim()).join(',')
  )
  log(!typeText.includes('关联争议'), '新建案件类型不含「关联争议案件」', typeText)

  // 2. 选中关联案件并回填基本信息
  await page.click('.create-form .el-form-item:has-text("关联案件") .el-select')
  await page.fill('.create-form .el-form-item:has-text("关联案件") input', 'GY2026110019')
  await page.waitForTimeout(1200)
  const optCount = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
  log(optCount > 0, '关联案件可远程搜索到目标案件', `${optCount} 个候选`)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
  await page.waitForTimeout(700)

  const filled = await page.evaluate(() => ({
    name: document.querySelector('.create-form input[placeholder="员工姓名"]')?.value || '',
    date: document.querySelector('.create-form input[placeholder="请选择发生日期"]')?.value || '',
    handler: ([...document.querySelectorAll('.create-form .el-form-item')]
      .find((i) => i.querySelector('.el-form-item__label')?.innerText.trim() === '承办人')
      ?.querySelector('.el-select__placeholder')?.innerText.trim()) || '',
    hint: document.querySelector('.create-form .rel-hint')?.innerText.trim() || ''
  }))
  log(filled.name === '王五', '当事人已带入', filled.name)
  log(!!filled.date, '发生日期已带入', filled.date)
  log(!!filled.handler, '承办人已带入', filled.handler)
  log(filled.hint.includes('已带入'), '显示回填说明', filled.hint.slice(0, 30))

  // 3. 建为仲裁案件并保存
  await page.click('.create-form .el-radio-button:has-text("劳动仲裁案件")')
  await page.waitForTimeout(400)
  const nameInput = page.locator('.create-form input[placeholder*="如：张三"]')
  if (!(await nameInput.inputValue())) {
    await nameInput.fill('关联回填测试案件')
  }
  await page.click('button:has-text("保存草稿")')
  await page.waitForTimeout(2500)
  const m = page.url().match(/\/cases\/(\d+)(?:\?|$)/)
  log(!!m, '带关联保存成功并跳转详情', page.url())
  const newCaseId = m ? m[1] : null
  const newCaseNo = await page.evaluate(() => document.querySelector('.detail-head .no')?.innerText.trim() || '')
  log(/^ZS\d{10}$/.test(newCaseNo), '新案件按仲裁规则自动编号', newCaseNo)

  // 4. 关联页展示这对关联
  await page.goto(BASE + '/relations', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  const statVals = await page.evaluate(() =>
    [...document.querySelectorAll('.stat-card .value')].map((e) => e.innerText.trim())
  )
  log(Number(statVals[0]) >= 1, '关联关系统计包含新建的关联', statVals.join(' / '))
  const headers = await page.evaluate(() =>
    [...document.querySelectorAll('.rel-table th')].map((e) => e.innerText.trim())
  )
  log(!headers.includes('关联类型'), '表格不展示关联类型列', headers.join(','))
  const bodyText = await page.evaluate(() => document.querySelector('.rel-table')?.innerText || '')
  log(bodyText.includes('GY2026110019') && bodyText.includes(newCaseNo), '主案与关联案成对展示',
    `${bodyText.includes('GY2026110019')} / ${bodyText.includes(newCaseNo)}`)
  await page.screenshot({ path: 'E:/GSMS/deploy/shots/relations/relations-list.png' })

  // 5. 导出清单
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 15000 }).catch(() => null),
    page.click('button:has-text("导出清单")')
  ])
  log(!!download, '导出清单可下载', download ? download.suggestedFilename() : '未触发下载')

  // 6. 解除本轮新建的关联（不清空既有演示关联）
  const newRow = page.locator('.rel-table tbody tr', { hasText: newCaseNo }).first()
  await newRow.getByText('解除').click()
  await page.waitForTimeout(600)
  await page.locator('.el-message-box__btns button:has-text("解除")').click()
  await page.waitForTimeout(1600)
  const afterText = await page.evaluate(() => document.querySelector('.rel-table')?.innerText || '')
  log(!afterText.includes(newCaseNo), '解除后关联行消失', afterText.includes(newCaseNo) ? '仍存在' : '已移除')

  // 7. 移动端不溢出
  const mctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
  const mp = await mctx.newPage()
  await login(mp)
  await mp.goto(BASE + '/relations', { waitUntil: 'networkidle' })
  await mp.waitForTimeout(1200)
  const overflow = await mp.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  log(overflow <= 1, '移动端页面无横向溢出', `${overflow}px`)
  await mp.screenshot({ path: 'E:/GSMS/deploy/shots/relations/relations-mobile.png' })

  log(errs.length === 0, '无 JS 报错', errs.join('|') || '无')
  console.log('NEW_CASE_ID=' + newCaseId)
  await browser.close()
  const pass = results.filter(Boolean).length
  console.log('='.repeat(44))
  console.log(`  通过: ${pass}   失败: ${results.length - pass}`)
  process.exit(pass === results.length ? 0 : 1)
})()
