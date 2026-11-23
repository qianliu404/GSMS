// 登录页改版验证：精简后只保留品牌标题 + 账号密码表单，红框元素全部移除
const { chromium } = require('playwright-core')
const BASE = 'http://127.0.0.1:8080'
const results = []
const log = (ok, name, detail = '') => {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--disable-gpu'] })
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(e.message))
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)

  // 1. 保留的元素
  log(await page.locator('.brand-icon').count() === 0, '「法」字图标已去除')
  log(await page.locator('.brand-name').innerText() === '案件管理中心', '「案件管理中心」保留')
  log(await page.locator('.brand-en').innerText() === 'LEGAL CASE SYSTEM', 'LEGAL CASE SYSTEM 保留')
  log((await page.locator('h1').innerText()).includes('工伤与劳动仲裁'), '左侧大标题保留')
  log((await page.locator('.hero-desc').innerText()).includes('全流程管理'), '左侧说明段保留')
  log(await page.locator('.rings').count() === 1, '右下角同心圆装饰保留')
  log(await page.locator('h2').innerText() === '欢迎登录', '右侧「欢迎登录」标题')
  log(await page.locator('input[placeholder="请输入工号或用户名"]').count() === 1, '工号输入框')
  log(await page.locator('input[placeholder="请输入登录密码"]').count() === 1, '密码输入框')
  log(await page.locator('.el-checkbox:has-text("记住登录状态")').count() === 1, '「记住登录状态」保留')
  log(await page.locator('.submit').count() === 1, '登录按钮')
  log(await page.locator('.el-checkbox.is-checked').count() === 1, '「记住登录状态」默认勾选')
  log((await page.locator('.el-form-item__label').allInnerTexts()).every((t) => !t.includes('*')), '表单标签无必填星号')

  // 2. 红框内元素全部移除
  const gone = await page.evaluate(() => {
    const text = document.body.innerText
    const q = (s) => document.querySelectorAll(s).length
    return {
      flow: q('.flow, .chip') || text.includes('草案') || text.includes('结案归档'),
      foot: text.includes('华东厂区') || text.includes('仅限授权人员使用'),
      tabs: text.includes('短信验证码登录') || text.includes('企业微信扫码'),
      captcha: q('.captcha-img, .captcha-row') || text.includes('图形验证码'),
      forgot: text.includes('忘记密码'),
      others: text.includes('其他登录方式') || text.includes('统一身份认证'),
      demo: text.includes('演示账号') || text.includes('点击填入')
    }
  })
  for (const [key, present] of Object.entries(gone)) {
    log(!present, `红框元素已移除：${key}`)
  }

  // 3. 登录功能
  const before = await page.evaluate(() => localStorage.getItem('gsms-login-remember'))
  await page.locator('.submit').click()
  await page.waitForTimeout(2200)
  log(page.url().includes('/dashboard'), '直接登录并跳转工作台', page.url())
  log(before !== '0', '记住登录状态已写入本地')

  // 4. 移动端
  const mp = await (await browser.newContext({ viewport: { width: 390, height: 844 } })).newPage()
  mp.on('pageerror', (e) => errs.push(e.message))
  await mp.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await mp.waitForTimeout(600)
  log(await mp.locator('.left').isHidden(), '移动端隐藏左侧品牌区')
  const overflow = await mp.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)
  log(!overflow, '移动端无横向溢出')
  log(await mp.locator('.submit').isVisible(), '移动端登录按钮可见')

  log(!errs.length, '无前端脚本错误', errs.slice(0, 2).join(' | '))
  const pass = results.filter(Boolean).length
  console.log(`\n${pass}/${results.length} 通过`)
  await browser.close()
  process.exit(pass === results.length ? 0 : 1)
})()
