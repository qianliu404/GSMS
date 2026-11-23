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
  await p.waitForTimeout(2000)
  const rows = await p.$$eval('.el-table__row', (els) =>
    els.slice(0, 12).map((e) => {
      const t = e.querySelectorAll('td')
      return { no: t[0]?.innerText.trim(), status: t[8]?.innerText.trim() }
    })
  )
  console.log(JSON.stringify(rows))
  await p.screenshot({ path: 'E:/GSMS/deploy/shots/deadline-fix/06-duesoon-list.png', clip: { x: 0, y: 60, width: 1440, height: 520 } })
  await b.close()
})().catch((e) => { console.error('ERR', e.message); process.exit(1) })
