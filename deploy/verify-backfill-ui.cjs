const { chromium } = require('E:/GSMS/frontend/node_modules/playwright-core')
;(async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const p = await (await b.newContext({ viewport: { width: 1440, height: 1100 } })).newPage()
  await p.goto('http://127.0.0.1:8080/login', { waitUntil: 'networkidle' })
  await p.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await p.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await p.click('button:has-text("登 录")')
  await p.waitForTimeout(2000)

  // 108 仲裁 · 立案节点：应出现「受理日期」+ 档案「受理情况」显示已受理
  await p.goto('http://127.0.0.1:8080/cases/100', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)
  const labels = await p.$$eval('.card .el-form-item__label', (els) => els.map((e) => e.textContent.trim()))
  console.log('办理区字段:', JSON.stringify(labels))
  const cells = await p.$$eval('.arc .k', (els) => els.map((e) => e.textContent.trim()))
  const vals = await p.$$eval('.arc .v', (els) => els.map((e) => e.textContent.trim()))
  console.log('档案:', JSON.stringify(cells.map((k, i) => k + '=' + vals[i])))
  await p.screenshot({ path: 'E:/GSMS/deploy/shots/deadline-fix/05-backfill-injury.png', clip: { x: 180, y: 230, width: 1060, height: 620 } })

  await b.close()
})().catch((e) => { console.error('ERR', e.message); process.exit(1) })
