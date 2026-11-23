// 验证「未结案且未归档可编辑」：非草稿案件能改仲裁案号并落库，已结案/已归档按钮禁用
const { chromium } = require('E:/GSMS/frontend/node_modules/playwright-core')

const BASE = 'http://127.0.0.1:4173'
const ARB_ID = 108   // ZS2026110001  RULING
const CLOSED_ID = 110 // ZS2026110003  CLOSED
const ARCHIVED_ID = 109 // ZS2026110002 ARCHIVED

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)

  const openEdit = async (id) => {
    await page.goto(BASE + '/cases/' + id, { waitUntil: 'networkidle' })
    await page.waitForTimeout(1200)
    await page.click('button:has-text("编辑档案")')
    await page.waitForTimeout(700)
    return page.locator('.el-dialog:has-text("编辑案件档案")')
  }

  // --- 1. RULING 案件：改案号 -> 保存 -> 校验落库 -> 改回 ---
  let dialog = await openEdit(ARB_ID)
  const noInput = dialog.locator('.el-form-item', { hasText: '仲裁案号' }).locator('input')
  const original = await noInput.inputValue()
  console.log('原案号:', JSON.stringify(original))

  await noInput.fill(original + '（改）')
  await dialog.locator('button:has-text("保存")').click()
  await page.waitForTimeout(1500)
  const msg1 = await page.locator('.el-message__content').first().innerText().catch(() => '(无提示)')
  console.log('保存提示:', msg1)

  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  const bodyAfter = await page.locator('body').innerText()
  console.log('详情页已出现新值:', bodyAfter.includes('（改）'))

  // 恢复原值
  dialog = await openEdit(ARB_ID)
  const noInput2 = dialog.locator('.el-form-item', { hasText: '仲裁案号' }).locator('input')
  console.log('回填值:', JSON.stringify(await noInput2.inputValue()))
  await noInput2.fill(original)
  await dialog.locator('button:has-text("保存")').click()
  await page.waitForTimeout(1500)
  const msg2 = await page.locator('.el-message__content').first().innerText().catch(() => '(无提示)')
  console.log('恢复提示:', msg2)

  // --- 2. 已结案 / 已归档：按钮应禁用 ---
  for (const [label, id] of [['已结案', CLOSED_ID], ['已归档', ARCHIVED_ID]]) {
    await page.goto(BASE + '/cases/' + id, { waitUntil: 'networkidle' })
    await page.waitForTimeout(1000)
    const disabled = await page.locator('button:has-text("编辑档案")').isDisabled()
    console.log(`${label}(${id}) 编辑按钮禁用:`, disabled)
  }

  console.log('页面错误:', errors.length ? errors.join('; ') : '无')
  await browser.close()
})()
