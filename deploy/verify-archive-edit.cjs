// 验证「编辑案件档案」弹窗的两列表单样式
const { chromium } = require('E:/GSMS/frontend/node_modules/playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:4173'
const OUT = 'E:/GSMS/deploy/shots/archive-edit'

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message))

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)

  // 取一件工伤案件
  const cases = await page.evaluate(async () => {
    const t = localStorage.getItem('gsms_token')
    const r = await fetch('/api/v1/cases?pageNum=1&pageSize=50', { headers: { Authorization: 'Bearer ' + t } })
    const j = await r.json()
    return (j.data.list || []).map((x) => ({ id: x.id, type: x.caseType, status: x.status, no: x.caseNo }))
  })
  const injury = cases.find((c) => c.type === 'INJURY')
  const arb = cases.find((c) => c.type === 'ARBITRATION')
  console.log('候选工伤:', JSON.stringify(injury), '候选仲裁:', JSON.stringify(arb))

  for (const [tag, c] of [['injury', injury], ['arbitration', arb]]) {
    if (!c) continue
    await page.goto(BASE + '/cases/' + c.id, { waitUntil: 'networkidle' })
    await page.waitForTimeout(1200)
    await page.click('button:has-text("编辑档案")')
    await page.waitForTimeout(900)
    await page.screenshot({ path: `${OUT}/${tag}.png` })

    const dialog = page.locator('.el-dialog:has-text("编辑案件档案")')
    const labels = await dialog.locator('.el-form-item__label').allInnerTexts()
    const items = await dialog.locator('.arc-grid > .el-form-item').evaluateAll((els) =>
      els.map((el) => {
        const r = el.getBoundingClientRect()
        return { l: el.querySelector('.el-form-item__label')?.innerText, x: Math.round(r.x), w: Math.round(r.width) }
      })
    )
    const cols = [...new Set(items.map((i) => i.x))]
    console.log(`[${tag}] 标签: ${labels.join(' | ')}`)
    console.log(`[${tag}] 列数=${cols.length} 宽度: ${items.map((i) => `${i.l}=${i.w}`).join(', ')}`)
    await page.keyboard.press('Escape')
    await page.waitForTimeout(500)
  }
  console.log('页面错误:', errors.length ? errors.join('; ') : '无')
  await browser.close()
})()
