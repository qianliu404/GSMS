const { chromium } = require('playwright-core')
const BASE = 'http://127.0.0.1:8080'
;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2000)
  await page.goto(BASE + '/cases/create', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  await page.click('.create-form .el-form-item:has-text("关联案件") .el-select')
  await page.fill('.create-form .el-form-item:has-text("关联案件") input', 'GY2026110019')
  await page.waitForTimeout(1200)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
  await page.waitForTimeout(900)
  const out = await page.evaluate(() => {
    const item = [...document.querySelectorAll('.create-form .el-form-item')]
      .find((i) => i.querySelector('.el-form-item__label')?.innerText.trim() === '承办人')
    return {
      text: item?.innerText.trim(),
      inputs: [...(item?.querySelectorAll('input') || [])].map((i) => i.value),
      placeholders: [...(item?.querySelectorAll('.el-select__placeholder') || [])].map((e) => e.innerText)
    }
  })
  console.log(JSON.stringify(out, null, 2))
  await browser.close()
})()
