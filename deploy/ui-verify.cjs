// 用系统 Edge 驱动前端做端到端界面验证（不下载额外浏览器）
const { chromium } = require('playwright-core')
const fs = require('fs')

const BASE = 'http://127.0.0.1:4173'
const OUT = 'E:/GSMS/deploy/shots'
const errors = []
const results = []

function log(ok, name, detail = '') {
  results.push({ ok, name, detail })
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()

  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text())
  })
  page.on('pageerror', (err) => errors.push('PAGEERROR: ' + err.message))

  // 1. 登录
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.screenshot({ path: OUT + '/01-login.png' })
  log((await page.locator('text=账号登录').count()) > 0, '登录页渲染')

  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)
  log(page.url().includes('/dashboard'), '登录后进入流程总览', page.url())

  // 2. 流程总览
  await page.screenshot({ path: OUT + '/02-dashboard.png', fullPage: true })
  log((await page.locator('.stat-card').count()) === 4, '总览 4 个统计卡')
  log((await page.locator('.card .flow').count()) === 2, '工伤/仲裁两条流程条')
  const injuryStages = await page.locator('.card').first().locator('.stp').count()
  log(injuryStages === 10, '工伤流程 10 个节点', `stages=${injuryStages}`)
  log((await page.locator('.todo').count()) > 0, '待办与预警有数据')
  log((await page.locator('.nav-group').count()) === 3, '侧栏 3 个分组')

  // 3. 流程条下钻 → 案件列表
  await page.locator('.stp-card.has').first().click()
  await page.waitForTimeout(1500)
  await page.screenshot({ path: OUT + '/03-case-list.png', fullPage: true })
  log(page.url().includes('/cases'), '节点下钻进入列表', page.url())
  const rows = await page.locator('.el-table__body tr').count()
  log(rows > 0, '案件列表有数据', `rows=${rows}`)
  log((await page.locator('.seg button').count()) === 3, '类型分段控件 3 项')

  // 3.1 类型分段必须在本页筛选，不得跳转到「工伤案件 / 劳动仲裁」模块
  await page.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/cases?') && r.url().includes('caseType=INJURY')),
    page.click('.seg button:has-text("工伤")')
  ])
  await page.waitForTimeout(500)
  log(!page.url().includes('type='), '工伤筛选不改动路由', page.url())
  log((await page.locator('.nav-item.on').count()) === 1, '侧栏仍只有一项高亮')
  log((await page.locator('.nav-item.on').innerText()).includes('案件列表'), '侧栏仍停留在案件列表')
  const injRows = await page.locator('.el-table__body tr').count()
  const arbInInj = await page.locator('.el-table__body tr .tag.arb').count()
  log(injRows > 0 && arbInInj === 0, '列表已按工伤过滤', `rows=${injRows} 仲裁行=${arbInInj}`)
  log((await page.locator('.seg button.on').innerText()) === '工伤', '分段高亮切到工伤')

  await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/cases?') && r.url().includes('caseType=ARBITRATION')),
    page.click('.seg button:has-text("劳动仲裁")')
  ])
  await page.waitForTimeout(500)
  const arbRows = await page.locator('.el-table__body tr').count()
  const injInArb = await page.locator('.el-table__body tr .tag.injury').count()
  log(arbRows > 0 && injInArb === 0, '列表已按劳动仲裁过滤', `rows=${arbRows} 工伤行=${injInArb}`)
  log(!page.url().includes('type='), '劳动仲裁筛选不改动路由', page.url())

  await page.click('.seg button:has-text("全部")')
  await page.waitForTimeout(1400)
  log((await page.locator('.seg button.on').innerText()) === '全部', '可回到全部案件')

  // 3.2 从侧栏「工伤案件」模块进入时，切类型仍应落到对应模块（避免标题与侧栏错位）
  await page.goto(BASE + '/cases?type=INJURY', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1300)
  await page.click('.seg button:has-text("劳动仲裁")')
  await page.waitForTimeout(1400)
  log(page.url().includes('type=ARBITRATION'), '模块内切类型落到对应模块', page.url())
  log((await page.locator('.nav-item.on').innerText()).includes('劳动仲裁'), '侧栏高亮跟随模块')

  // 4. 案件详情
  await page.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.locator('.el-table__body tr').first().click()
  await page.waitForTimeout(2000)
  await page.screenshot({ path: OUT + '/04-case-detail.png', fullPage: true })
  log((await page.locator('.steps .sx').count()) > 0, '详情节点步骤条渲染')
  log((await page.locator('.cols > div').count()) === 2, '详情双栏布局')
  log((await page.locator('.arc > div').count()) === 9, '档案信息 9 个字段（含休假天数）')
  log((await page.locator('.sub-row').count()) === 3, '三项一次性待遇申领')

  const tabs = await page.locator('.el-tabs__item').count()
  log(tabs >= 7, '协同页签完整', `tabs=${tabs}`)

  await page.click('.el-tabs__item:has-text("文档")')
  await page.waitForTimeout(1200)
  await page.screenshot({ path: OUT + '/05-documents.png' })
  log(true, '文档页签可打开')

  await page.click('.el-tabs__item:has-text("流转日志")')
  await page.waitForTimeout(800)
  await page.screenshot({ path: OUT + '/06-timeline.png' })

  // 5. 台账（全部 / 工伤 / 劳动仲裁三套字段配置隔离）
  await page.goto(BASE + '/ledger', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  await page.screenshot({ path: OUT + '/07-ledger.png', fullPage: true })
  const ledgerCols = await page.locator('.el-table__header th').count()
  log(ledgerCols >= 12, '台账列数完整', `cols=${ledgerCols}`)
  // 三项申领标签属于工伤视图字段，切到工伤分段后检查
  await page.click('.seg button:has-text("工伤")')
  await page.waitForTimeout(1200)
  log((await page.locator('.stat-chip').count()) > 0, '工伤视图申领状态标签可点击')
  await page.click('.seg button:has-text("全部")')
  await page.waitForTimeout(1200)
  const ledgerRows = await page.locator('.el-table__body tr').count()
  log(ledgerRows > 0, '台账有数据', `rows=${ledgerRows}`)
  await page.locator('.el-select__placeholder', { hasText: '全部厂区' }).first().click()
  await page.waitForTimeout(600)
  const areaOpts = await page.locator('.el-select-dropdown__item:visible').count()
  log(areaOpts >= 5, '台账厂区筛选来自字典', `options=${areaOpts}`)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)

  // 6. 归档库
  await page.goto(BASE + '/archive', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: OUT + '/08-archive.png' })
  log((await page.locator('.el-table__body tr').count()) >= 1, '归档库有卷宗')

  // 7. 流程节点配置
  await page.goto(BASE + '/system/workflow', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  await page.screenshot({ path: OUT + '/09-workflow.png', fullPage: true })
  const cfgRows = await page.locator('.el-table__body tr').count()
  log(cfgRows >= 18, '节点配置完整（工伤 10 + 仲裁分支）', `rows=${cfgRows}`)

  // 8. 其余页面
  await page.goto(BASE + '/todos', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: OUT + '/10-todos.png' })
  log((await page.locator('.stat-card').count()) >= 3, '待办统计卡渲染')

  await page.goto(BASE + '/deadlines', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: OUT + '/11-deadline.png' })
  log((await page.locator('.el-table__body tr').count()) > 0, '期限列表有数据')

  await page.goto(BASE + '/reports', { waitUntil: 'networkidle' })
  await page.waitForTimeout(2600)
  await page.screenshot({ path: OUT + '/12-report.png', fullPage: true })
  log((await page.locator('canvas').count()) >= 3, '报表图表渲染')

  await page.goto(BASE + '/system/users', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: OUT + '/13-users.png' })
  log((await page.locator('.el-table__body tr').count()) >= 8, '用户列表加载')
  const adminDelDisabled = await page
    .locator('.el-table__body tr', { hasText: '系统管理员' })
    .first()
    .locator('button:has-text("删除")')
    .isDisabled()
  log(adminDelDisabled, '内置管理员删除按钮禁用')

  // 通过接口造一个临时账号，再从界面删除，验证软删除闭环
  const uniq = 'uicheck' + String(Date.now()).slice(-6)
  const created = await page.evaluate(async (uid) => {
    const token = localStorage.getItem('gsms_token')
    const res = await fetch('/api/v1/users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
      body: JSON.stringify({ userId: uid, userName: uid, deptId: 2, password: 'Test@123', status: 1, roleIds: [7] })
    })
    return res.json()
  }, uniq)
  log(created.code === 0, '接口创建临时账号', `code=${created.code}`)
  await page.goto(BASE + '/system/users', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  const tempRow = page.locator('.el-table__body tr', { hasText: uniq })
  log((await tempRow.count()) === 1, '临时账号出现在列表')
  await tempRow.first().locator('button:has-text("删除")').click()
  await page.waitForTimeout(600)
  log((await page.locator('.el-message-box').isVisible()), '删除确认弹窗出现')
  await page.screenshot({ path: OUT + '/17-user-delete.png' })
  await page.click('.el-message-box__btns button:has-text("确认删除")')
  await page.waitForTimeout(1500)
  log((await page.locator('.el-table__body tr', { hasText: uniq }).count()) === 0, '删除后该行消失')

  await page.goto(BASE + '/system/audit-logs', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: OUT + '/14-audit.png' })
  log((await page.locator('.el-table__body tr').count()) > 0, '审计日志加载')

  // 9. 顶栏用户账号入口（替代原「新建案件」按钮）
  await page.goto(BASE + '/dashboard', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  log((await page.locator('.topbar button:has-text("新建案件")').count()) === 0, '顶栏已移除新建案件按钮')
  log((await page.locator('.topbar .user-chip').count()) === 1, '顶栏显示用户账号入口')
  log((await page.locator('.topbar .uc-name').innerText()).length > 0, '账号入口显示用户名')
  await page.click('.topbar .user-chip')
  await page.waitForTimeout(700)
  await page.screenshot({ path: OUT + '/15-user-menu.png' })
  log((await page.locator('.uc-head').count()) === 1, '下拉显示账号信息')
  log((await page.locator('.el-dropdown-menu__item:has-text("退出登录")').count()) === 1, '下拉含退出登录')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)

  // 10. 完整建档页
  await page.goto(BASE + '/cases/create', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: OUT + '/16-create-full.png', fullPage: true })
  log((await page.locator('.el-radio-button').count()) === 3, '完整建档页类型 3 项')
  log((await page.locator('input[placeholder="如 EMP1001"]').count()) === 0, '已移除员工工号字段')
  log((await page.locator('.el-form-item__label:has-text("所属部门")').count()) === 0, '已移除所属部门字段')
  const descTip = await page.locator('textarea').first().getAttribute('placeholder')
  log((descTip || '').includes('选填'), '案件描述标注为选填', descTip)

  // 10.1 厂区自定义设置
  await page.click('.area-row button')
  await page.waitForTimeout(700)
  log((await page.locator('.el-dialog').isVisible()), '厂区设置弹窗打开')
  const areaCount = await page.locator('.area-item').count()
  log(areaCount >= 1, '厂区选项来自字典', `areas=${areaCount}`)
  await page.fill('.el-dialog input[placeholder="输入厂区名称，如 华东二厂"]', 'UI测试厂区')
  await page.click('.el-dialog button:has-text("添加")')
  await page.waitForTimeout(1000)
  log((await page.locator('.area-item:has-text("UI测试厂区")').count()) === 1, '可新增自定义厂区')
  await page.locator('.area-item:has-text("UI测试厂区") button').click()
  await page.waitForTimeout(500)
  await page.click('.el-message-box__btns button:has-text("确定")')
  await page.waitForTimeout(1000)
  log((await page.locator('.area-item:has-text("UI测试厂区")').count()) === 0, '可删除自定义厂区')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)

  // 10.2 回收站：删除 → 回收站 → 恢复 / 批量永久删除
  await page.goto(BASE + '/recycle-bin', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1400)
  log((await page.locator('.page-title').innerText()) === '回收站', '回收站页面打开')
  log((await page.locator('.seg button').count()) === 2, '案件/用户两个页签')

  const mk = (name) => `(async () => {
    const token = localStorage.getItem('gsms_token')
    const h = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token }
    const res = await fetch('/api/v1/cases', { method: 'POST', headers: h, body: JSON.stringify({
      caseType: 'INJURY', caseName: '${name}', employeeName: '回收测试', occurDate: '2026-11-12', handlerId: 'handler', submit: false
    }) })
    const created = await res.json()
    if (created.code !== 0) return created
    await fetch('/api/v1/cases/' + created.data, { method: 'DELETE', headers: h })
    return created
  })()`

  // 单条：删除 → 回收站 → 恢复
  const one = await page.evaluate(mk('UI回收测试-恢复'))
  log(one.code === 0, '接口造案件并删除', `id=${one.data}`)
  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(1400)
  const restoreRow = page.locator('.el-table__body tr', { hasText: 'UI回收测试-恢复' })
  await restoreRow.first().waitFor({ state: 'visible', timeout: 15000 })
  log((await restoreRow.count()) === 1, '已删除案件出现在回收站')
  await restoreRow.first().locator('button:has-text("恢复")').click()
  await page.waitForTimeout(600)
  await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/v1/recycle-bin/restore') && r.status() === 200),
    page.click('.el-message-box__btns button:has-text("确认恢复")')
  ])
  await page.waitForTimeout(1200)
  log((await page.locator('.el-table__body tr', { hasText: 'UI回收测试-恢复' }).count()) === 0, '恢复后移出回收站')
  const back = await page.evaluate(async (id) => {
    const r = await fetch('/api/v1/cases/' + id, { headers: { Authorization: 'Bearer ' + localStorage.getItem('gsms_token') } })
    return (await r.json()).code
  }, one.data)
  log(back === 0, '恢复后案件可正常打开')

  // 批量：两条一起永久删除
  await page.evaluate(mk('UI回收测试-永久A'))
  const two = await page.evaluate(mk('UI回收测试-永久B'))
  await page.reload({ waitUntil: 'networkidle' })
  await page
    .locator('.el-table__body tr', { hasText: 'UI回收测试-永久B' })
    .first()
    .waitFor({ state: 'visible', timeout: 15000 })
  await page.locator('.el-table__header .el-checkbox').first().click()
  await page.waitForTimeout(400)
  await page.click('.toolbar button:has-text("批量删除")')
  await page.waitForTimeout(600)
  await page.screenshot({ path: OUT + '/18-recycle-purge.png' })
  await Promise.all([
    page.waitForResponse((r) => r.request().method() === 'DELETE' && r.url().includes('/api/v1/recycle-bin') && r.status() === 200),
    page.click('.el-message-box__btns button:has-text("确认永久删除")')
  ])
  await page.waitForTimeout(1200)
  log((await page.locator('.el-table__body tr', { hasText: 'UI回收测试-永久' }).count()) === 0, '批量永久删除后清空')
  const gone = await page.evaluate(async (id) => {
    const r = await fetch('/api/v1/cases/' + id, { headers: { Authorization: 'Bearer ' + localStorage.getItem('gsms_token') } })
    return (await r.json()).code
  }, two.data)
  log(gone !== 0, '永久删除后数据库记录不可再查')

  // 11. 移动端
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(BASE + '/m/todos', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.screenshot({ path: OUT + '/17-mobile.png', fullPage: true })
  log((await page.locator('.mhead').count()) > 0, '移动端待办渲染')

  await browser.close()

  console.log('\n--- 控制台错误 ---')
  if (errors.length === 0) {
    console.log('无 JS 报错')
  } else {
    errors.slice(0, 12).forEach((e) => console.log('! ' + e))
  }

  const failed = results.filter((r) => !r.ok).length
  console.log(`\n通过 ${results.length - failed} / ${results.length}`)
  process.exit(failed === 0 && errors.length === 0 ? 0 : 1)
})().catch((e) => {
  console.error('FATAL', e)
  process.exit(1)
})
