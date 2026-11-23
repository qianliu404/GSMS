const { chromium } = require('E:/GSMS/frontend/node_modules/playwright-core')
;(async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const p = await (await b.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()
  await p.goto('http://127.0.0.1:8080/login', { waitUntil: 'networkidle' })
  await p.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await p.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await p.click('button:has-text("登 录")')
  await p.waitForTimeout(2000)
  await p.goto('http://127.0.0.1:8080/cases', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)

  // 打开「全部期限」下拉，选「临近超期」
  await p.locator('.toolbar .el-select').last().click()
  await p.waitForTimeout(500)
  await p.locator('.el-select-dropdown__item:has-text("临近超期")').click()
  await p.waitForTimeout(1800)

  const tags = await p.$$eval('.el-table__row', (els) =>
    els.map((e) => {
      const tds = e.querySelectorAll('td')
      return tds[0]?.innerText.trim() + ' | ' + tds[7]?.innerText.trim()
    })
  )
  console.log('筛选后:', JSON.stringify(tags))
  await p.screenshot({ path: 'E:/GSMS/deploy/shots/deadline-fix/07-filter-duesoon.png', clip: { x: 0, y: 60, width: 1440, height: 420 } })
  await b.close()
})().catch((e) => { console.error('ERR', e.message); process.exit(1) })
