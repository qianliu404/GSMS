// 工伤档案：伤害日期可编辑 + 说明文字精简 + 详情页布局（档案整行、办理记录与待遇并排）
const { chromium } = require('playwright-core')
const BASE = 'http://127.0.0.1:8080'
const CASE_ID = process.argv[2] || '341'
const OUT = 'E:/GSMS/deploy/shots/archive-injury'
const fs = require('fs')
const results = []
const log = (ok, name, detail = '') => {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

async function login(page) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)
}

/** 档案网格里某个标签对应的值 */
const cellValue = (page, key) =>
  page.evaluate((k) => {
    const cells = [...document.querySelectorAll('.arc > div')]
    const hit = cells.find((c) => c.querySelector('.k')?.innerText.trim() === k)
    return hit ? hit.querySelector('.v')?.innerText.trim() : null
  }, key)

const boxOf = (page, sel) =>
  page.evaluate((s) => {
    const el = document.querySelector(s)
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) }
  }, sel)

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1500, height: 1000 } })).newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(e.message))

  await login(page)
  await page.goto(`${BASE}/cases/${CASE_ID}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(2000)

  // ---------- 1. 档案含伤害日期且取值独立 ----------
  const injuryDate = await cellValue(page, '伤害日期')
  const occurDate = await cellValue(page, '发生日期')
  log(!!injuryDate && /^\d{4}-\d{2}-\d{2}$/.test(injuryDate), '工伤档案显示伤害日期', injuryDate)
  log(!!occurDate, '发生日期仍保留（两字段独立）', occurDate)

  // ---------- 2. 编辑档案可改伤害日期 ----------
  await page.click('.card-head button:has-text("编辑档案")')
  await page.waitForTimeout(800)
  const dlgHas = await page.evaluate(() => {
    const labels = [...document.querySelectorAll('.arc-form .el-form-item__label')].map((e) => e.innerText.trim())
    return { labels, has: labels.includes('伤害日期') }
  })
  log(dlgHas.has, '编辑档案弹窗有伤害日期字段', dlgHas.labels.join('/'))
  const dlgVal = await page.evaluate(() => {
    const item = [...document.querySelectorAll('.arc-form .el-form-item')]
      .find((i) => i.querySelector('.el-form-item__label')?.innerText.trim() === '伤害日期')
    return item?.querySelector('input')?.value || ''
  })
  log(dlgVal === injuryDate, '弹窗带出原伤害日期', `${dlgVal} / 档案 ${injuryDate}`)

  const newDate = '2026-10-20'
  const input = page.locator('.arc-form .el-form-item:has-text("伤害日期") input').first()
  await input.click()
  await input.fill(newDate)
  await input.press('Enter')
  await page.waitForTimeout(400)
  await page.screenshot({ path: OUT + '/edit-dialog.png' })
  await page.locator('.el-dialog:visible .el-button:has-text("保存")').click()
  await page.waitForTimeout(2000)
  const saved = await cellValue(page, '伤害日期')
  log(saved === newDate, '保存后档案显示新伤害日期', saved)

  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(1800)
  const persisted = await cellValue(page, '伤害日期')
  log(persisted === newDate, '刷新后伤害日期已落库', persisted)

  // 还原原值，避免污染演示数据
  await page.click('.card-head button:has-text("编辑档案")')
  await page.waitForTimeout(700)
  const back = page.locator('.arc-form .el-form-item:has-text("伤害日期") input').first()
  await back.click()
  await back.fill(injuryDate)
  await back.press('Enter')
  await page.waitForTimeout(300)
  await page.locator('.el-dialog:visible .el-button:has-text("保存")').click()
  await page.waitForTimeout(1800)
  log((await cellValue(page, '伤害日期')) === injuryDate, '已还原伤害日期', injuryDate)

  // ---------- 3. 多余说明文字已去掉 ----------
  const body = await page.evaluate(() => document.body.innerText)
  const removed = [
    '字段与「台账」一致',
    '点击右侧状态标签可更新申领进度',
    '完整列表见「文档」页签',
    '支持 PDF / JPG / DOCX',
    '按节点时限倒序排列',
    '点击节点筛选',
    '滚动查看全部',
    '按流程配置',
    '未开启计时 · 提交后开始计算是否超期',
    '提交后自动回填',
    '待启动 · 依赖当前节点完成',
    '（将记入流转日志与审计）'
  ]
  const left = removed.filter((t) => body.includes(t))
  log(!left.length, '详情页多余说明文字已清除', left.join(' | ') || '无残留')
  log(body.includes('责任人'), '右栏保留「责任人」卡片')

  // ---------- 4. 布局：档案在左栏、记录与待遇上下排列 ----------
  const layout = await page.evaluate(() => {
    const cards = [...document.querySelectorAll('.card')]
    const find = (t) => cards.find((c) => c.querySelector('.card-head h2')?.innerText.includes(t))
    const arch = find('档案信息') || find('争议基本信息')
    const rec = find('办理记录')
    const sub = find('一次性待遇')
    const box = document.querySelector('.content')
    const r = (el) => {
      const b = el.getBoundingClientRect()
      return { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height) }
    }
    return { arch: r(arch), rec: r(rec), sub: sub ? r(sub) : null, contentW: Math.round(box.clientWidth) }
  })
  log(
    layout.arch.w < layout.contentW * 0.8 && layout.arch.x === layout.rec.x,
    '档案信息回到左栏、与办理记录左对齐',
    JSON.stringify(layout.arch)
  )
  log(
    layout.sub && layout.sub.x === layout.rec.x && layout.sub.y > layout.rec.y,
    '办理记录与一次性待遇上下排列',
    layout.sub ? `记录 y=${layout.rec.y} / 待遇 y=${layout.sub.y}` : '无待遇卡'
  )

  await page.screenshot({ path: OUT + '/detail-layout.png', fullPage: false })

  // ---------- 5. 列表页说明文字 ----------
  await page.goto(BASE + '/cases?type=INJURY', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  const listText = await page.evaluate(() => document.body.innerText)
  log(!listText.includes('按节点时限倒序排列') && !listText.includes('点击节点筛选'), '列表页副标题与提示已精简')

  log(!errs.length, '无前端脚本错误', errs.slice(0, 3).join(' | '))

  const pass = results.filter(Boolean).length
  console.log(`\n${pass}/${results.length} 通过`)
  await browser.close()
  process.exit(pass === results.length ? 0 : 1)
})()
