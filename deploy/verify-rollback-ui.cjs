const { chromium } = require('E:/GSMS/frontend/node_modules/playwright-core')
;(async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const p = await (await b.newContext({ viewport: { width: 1440, height: 1100 } })).newPage()
  await p.goto('http://127.0.0.1:8080/login', { waitUntil: 'networkidle' })
  await p.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await p.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await p.click('button:has-text("登 录")')
  await p.waitForTimeout(2000)

  // 108：回退到「立案」，后续节点应全部显示未完成
  await p.goto('http://127.0.0.1:8080/cases/108', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)
  await p.screenshot({ path: 'E:/GSMS/deploy/shots/deadline-fix/01-rollback-steps.png', clip: { x: 0, y: 0, width: 1440, height: 560 } })

  const steps = await p.$$eval('.sx', (els) =>
    els.map((e) => ({
      name: e.querySelector('.nm')?.textContent?.trim(),
      done: e.classList.contains('done'),
      cur: e.classList.contains('cur'),
      idx: e.querySelector('.d')?.textContent?.trim(),
    }))
  )
  console.log('108 steps:', JSON.stringify(steps, null, 0))

  // 卡片顺序
  const cards = await p.$$eval('.cols .card h2', (els) => els.map((e) => e.textContent.trim()))
  console.log('cards:', JSON.stringify(cards))

  await p.locator('.cols').scrollIntoViewIfNeeded()
  await p.waitForTimeout(500)
  await p.screenshot({ path: 'E:/GSMS/deploy/shots/deadline-fix/02-card-order.png' })

  await b.close()
})().catch((e) => {
  console.error('ERR', e.message)
  process.exit(1)
})
