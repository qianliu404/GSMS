// 流程节点：跳级推进未办理的节点不能显示为已完成
// 用法：node verify-skip-node-ui.cjs <有跳级的仲裁案件ID> <连续办理的工伤案件ID>
const { chromium } = require('playwright-core')
const BASE = 'http://127.0.0.1:8080'
const SKIP_CASE = process.argv[2] || '108'
const LINEAR_CASE = process.argv[3] || '124'
const results = []
const log = (ok, name, detail = '') => {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true })
  const p = await (await b.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()
  const errs = []
  p.on('pageerror', (e) => errs.push(e.message))
  await p.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await p.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await p.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await p.click('button:has-text("登 录")')
  await p.waitForTimeout(2000)

  await p.goto(`${BASE}/cases/${SKIP_CASE}`, { waitUntil: 'networkidle' })
  await p.waitForTimeout(1500)
  const rows = await p.evaluate(() =>
    [...document.querySelectorAll('.steps .sx')].map((s) => ({
      nm: s.querySelector('.nm')?.innerText.trim(),
      mark: s.querySelector('.d')?.innerText.trim(),
      tm: s.querySelector('.tm')?.innerText.trim(),
      cls: s.className
    }))
  )
  const skipped = rows.filter((r) => r.cls.includes('skip'))
  log(skipped.length > 0, '存在被跳过的节点', skipped.map((r) => r.nm).join(' / '))
  log(skipped.every((r) => r.mark === '–' && r.tm === '已跳过'), '跳过节点不显示对勾与完成日期',
    skipped.map((r) => `${r.nm}:${r.mark}/${r.tm}`).join('，'))
  const retrial = rows.find((r) => r.nm.includes('二审'))
  const ruling = rows.find((r) => r.nm === '裁定')
  log(!!retrial?.cls.includes('skip'), '「开庭（二审）」被标为已跳过', JSON.stringify(retrial))
  log(!!ruling?.cls.includes('skip'), '「裁定」被标为已跳过', JSON.stringify(ruling))
  log(rows.filter((r) => r.cls.includes('cur')).length === 1, '当前节点唯一',
    rows.filter((r) => r.cls.includes('cur')).map((r) => r.nm).join())

  await p.goto(`${BASE}/cases/${LINEAR_CASE}`, { waitUntil: 'networkidle' })
  await p.waitForTimeout(1500)
  const linear = await p.evaluate(() => [...document.querySelectorAll('.steps .sx')].map((s) => s.className))
  log(!linear.some((c) => c.includes('skip')), '连续办理的案件没有误标跳过', linear.join(' | '))
  log(errs.length === 0, '无 JS 报错', errs.join('|') || '无')

  await b.close()
  const pass = results.filter(Boolean).length
  console.log('='.repeat(40))
  console.log(`  通过: ${pass}   失败: ${results.length - pass}`)
  process.exit(pass === results.length ? 0 : 1)
})()
