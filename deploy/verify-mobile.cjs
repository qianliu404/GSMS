const { chromium } = require('E:/GSMS/frontend/node_modules/playwright-core')

const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/mobile'

;(async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await b.newContext({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    userAgent:
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
  })
  const p = await ctx.newPage()

  const overflow = async (label) => {
    const r = await p.evaluate(() => ({
      scrollW: document.documentElement.scrollWidth,
      innerW: window.innerWidth,
    }))
    const bad = r.scrollW > r.innerW + 1
    console.log(`  ${label}: ${r.scrollW}/${r.innerW} ${bad ? '⚠ 横向溢出' : 'OK'}`)
    return bad
  }

  // 1) 登录页
  await p.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1200)
  await overflow('登录页')
  await p.screenshot({ path: OUT + '/01-login.png' })

  await p.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await p.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await p.click('button:has-text("登 录")')
  await p.waitForTimeout(2500)

  // 2) 案件列表 + 抽屉
  await p.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)
  await overflow('案件列表')
  await p.screenshot({ path: OUT + '/02-cases.png' })

  const toggle = p.locator('.nav-toggle')
  console.log('  汉堡按钮可见:', await toggle.isVisible())
  await toggle.click()
  await p.waitForTimeout(500)
  const drawer = await p.locator('.sidebar').boundingBox()
  console.log('  抽屉位置 x=', Math.round(drawer?.x ?? -999), '宽=', Math.round(drawer?.width ?? 0))
  await p.screenshot({ path: OUT + '/03-nav-drawer.png' })

  // 点遮罩关闭
  await p.locator('.nav-mask').click({ position: { x: 350, y: 400 } })
  await p.waitForTimeout(400)
  const closed = await p.locator('.sidebar').boundingBox()
  console.log('  关闭后抽屉 x=', Math.round(closed?.x ?? -999))

  // 3) 案件详情
  await p.goto(BASE + '/cases/108', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)
  await overflow('案件详情')
  await p.screenshot({ path: OUT + '/04-case-detail.png', fullPage: false })

  // 4) 仪表盘
  await p.goto(BASE + '/dashboard', { waitUntil: 'networkidle' })
  await p.waitForTimeout(2200)
  await overflow('工作台')
  await p.screenshot({ path: OUT + '/05-dashboard.png' })

  // 5) 新增案件弹窗宽度
  await p.goto(BASE + '/cases/create', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1500)
  await overflow('新建案件')
  await p.screenshot({ path: OUT + '/06-case-create.png' })

  await b.close()
})().catch((e) => {
  console.error('ERR', e.message)
  process.exit(1)
})
