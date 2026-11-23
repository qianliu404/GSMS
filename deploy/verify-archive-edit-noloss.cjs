// 校验在非草稿工伤案件上「原样保存」不会丢扩展字段（尤其 treatment_amount）
const { chromium } = require('E:/GSMS/frontend/node_modules/playwright-core')
const BASE = 'http://127.0.0.1:4173'

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)

  await page.goto(BASE + '/cases/124', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.click('button:has-text("编辑档案")')
  await page.waitForTimeout(700)
  const dialog = page.locator('.el-dialog:has-text("编辑案件档案")')
  await dialog.locator('button:has-text("保存")').click()
  await page.waitForTimeout(1500)
  console.log('保存提示:', await page.locator('.el-message__content').first().innerText().catch(() => '(无)'))
  await browser.close()
})()
