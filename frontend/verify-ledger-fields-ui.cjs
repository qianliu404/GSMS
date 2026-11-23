// 台账字段设置：新增字段（发生日期 / 案件描述 / 休假天数 / 履行款）可选可显示
const { chromium } = require('playwright-core')
const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/ledger-fields'
const fs = require('fs')
const results = []
const log = (ok, name, detail = '') => {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const p = await (await b.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()
  const errs = []
  p.on('pageerror', (e) => errs.push(e.message))
  await p.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await p.fill('input[placeholder="登录名"]', 'admin')
  await p.fill('input[placeholder="密码"]', process.env.GSMS_PASSWORD || '')
  await p.click('button:has-text("登 录")')
  await p.waitForTimeout(2200)

  await p.goto(BASE + '/ledger', { waitUntil: 'networkidle' })
  await p.waitForTimeout(1800)

  const labels = () => p.evaluate(() =>
    [...document.querySelectorAll('.el-dialog .el-checkbox')].map((c) => c.innerText.trim()))

  await p.click('button:has-text("字段设置")')
  await p.waitForTimeout(600)
  let all = await labels()
  log(all.includes('发生日期'), '全部范围有「发生日期」', '')
  log(all.includes('案件描述'), '全部范围有「案件描述」', '')
  await p.screenshot({ path: OUT + '/dialog-all.png' })

  // 切到工伤：休假天数
  await p.click('.el-dialog .el-radio-button:has-text("工伤")')
  await p.waitForTimeout(400)
  let injuryLabels = await labels()
  log(injuryLabels.includes('休假天数'), '工伤范围有「休假天数」', '')

  // 切到劳动仲裁：履行款
  await p.click('.el-dialog .el-radio-button:has-text("劳动仲裁")')
  await p.waitForTimeout(400)
  let arbLabels = await labels()
  log(arbLabels.includes('履行款'), '劳动仲裁范围有「履行款」', '')
  await p.screenshot({ path: OUT + '/dialog-arb.png' })

  // 在「全部」范围勾选四个新字段并保存
  await p.click('.el-dialog .el-radio-button:has-text("全部")')
  await p.waitForTimeout(400)
  for (const label of ['发生日期', '案件描述', '休假天数', '履行款']) {
    await p.locator(`.el-dialog .el-checkbox:has-text("${label}")`).first().click()
  }
  await p.click('.el-dialog button:has-text("保存")')
  await p.waitForTimeout(1500)

  const heads = await p.evaluate(() => [...document.querySelectorAll('.d-table th')].map((t) => t.innerText.trim()))
  log(heads.includes('发生日期'), '表格出现发生日期列', heads.join(' / '))
  log(heads.includes('案件描述'), '表格出现案件描述列', '')
  log(heads.includes('休假天数'), '表格出现休假天数列', '')
  log(heads.includes('履行款'), '表格出现履行款列', '')

  const rows = await p.evaluate(() => {
    const idx = (name) => [...document.querySelectorAll('.d-table th')].findIndex((t) => t.innerText.trim() === name)
    const tr = document.querySelector('.d-table tbody tr')
    if (!tr) return {}
    const cells = tr.querySelectorAll('td')
    return {
      occur: cells[idx('发生日期')]?.innerText.trim(),
      desc: cells[idx('案件描述')]?.innerText.trim(),
      rest: cells[idx('休假天数')]?.innerText.trim(),
      pay: cells[idx('履行款')]?.innerText.trim()
    }
  })
  log(/^\d{4}-\d{2}-\d{2}$/.test(rows.occur || ''), '发生日期有值', JSON.stringify(rows))
  log(!!rows.desc, '案件描述有值', rows.desc)
  await p.screenshot({ path: OUT + '/table-new-cols.png', fullPage: true })

  // 刷新后仍保留（本地配置持久化）
  await p.reload({ waitUntil: 'networkidle' })
  await p.waitForTimeout(1500)
  const heads2 = await p.evaluate(() => [...document.querySelectorAll('.d-table th')].map((t) => t.innerText.trim()))
  log(heads2.includes('案件描述'), '刷新后仍显示新增列', heads2.slice(0, 6).join(' / '))

  log(errs.length === 0, '无 JS 报错', errs.join(' | ') || '无')
  await b.close()
  const pass = results.filter(Boolean).length
  console.log('='.repeat(44))
  console.log(`  通过: ${pass}   失败: ${results.length - pass}`)
  process.exit(pass === results.length ? 0 : 1)
})()
