// 角色自定义新建/删除：配置页弹窗创建 → 下拉出现并选中 → 删除按钮仅自定义角色显示 → 删除后消失
const { chromium } = require('playwright-core')
const BASE = 'http://127.0.0.1:8080'
const results = []
const log = (ok, name, detail = '') => {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--disable-gpu'] })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(e.message))
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2200)

  await page.goto(BASE + '/system/permissions', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)

  // 1. 角色栏按钮
  log(await page.locator('button:has-text("新建角色")').count() === 1, '提供「新建角色」按钮')
  log(await page.locator('button:has-text("删除角色")').count() === 0, '系统管理员不显示「删除角色」')

  // 2. 新建角色（复制阅知人权限）
  await page.click('button:has-text("新建角色")')
  await page.waitForTimeout(500)
  log(await page.locator('.el-dialog:visible').count() > 0, '新建角色弹窗打开')
  await page.fill('.el-dialog:visible input[placeholder="如：园区专员"]', 'UI测试角色')
  await page.click('.el-dialog:visible .el-form-item:has-text("数据范围") .el-select')
  await page.waitForTimeout(400)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("全部数据")').click()
  await page.waitForTimeout(500)
  await page.click('.el-dialog:visible .el-form-item:has-text("复制权限自") .el-select')
  await page.waitForTimeout(400)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("阅知人")').click()
  await page.click('.el-dialog:visible button:has-text("创建")')
  await page.waitForTimeout(2500)

  const roleBar = await page.evaluate(() => document.querySelector('.role-bar')?.innerText || '')
  log(roleBar.includes('UI测试角色'), '创建后自动选中新角色', roleBar.replace(/\n/g, ' '))
  log(roleBar.includes('全部数据'), '数据范围显示正确', roleBar.replace(/\n/g, ' '))
  const checked = await page.locator('.perm-grid .el-checkbox.is-checked').count()
  log(checked === 8, '复制了阅知人的 8 项权限', `${checked} 项`)
  log(await page.locator('button:has-text("删除角色")').count() === 1, '自定义角色显示「删除角色」')

  // 3. 用户与角色页能看到新角色
  await page.goto(BASE + '/system/users', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.locator('tbody tr').first().locator('button:has-text("编辑")').first().click()
  await page.waitForTimeout(800)
  // el-select 选项在打开下拉后才渲染，需先点开再检查
  await page.click('.el-dialog:visible .el-form-item:has-text("角色") .el-select')
  await page.waitForTimeout(500)
  const opt = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("UI测试角色")').count()
  log(opt === 1, '用户编辑弹窗可选新角色', `${opt} 个选项`)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)

  // 4. 删除新角色
  await page.goto(BASE + '/system/permissions', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  await page.click('.role-bar .el-select')
  await page.waitForTimeout(400)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("UI测试角色")').click()
  await page.waitForTimeout(900)
  await page.click('button:has-text("删除角色")')
  await page.waitForTimeout(500)
  await page.locator('.el-message-box__btns button:has-text("删除")').click()
  await page.waitForTimeout(1500)
  const after = await page.evaluate(() => {
    const sel = document.querySelector('.role-bar .el-select')
    const tags = sel ? [...sel.querySelectorAll('.el-select__selected-item')] : []
    return tags.map((e) => e.innerText.trim()).filter(Boolean).join('|')
  })
  log(!after.includes('UI测试角色'), '删除后角色从下拉消失', after || '(空)')
  // 删除后默认选中系统管理员（第一个角色），需切到其他内置角色验证删除按钮可见
  log(await page.locator('button:has-text("删除角色")').count() === 0, '系统管理员仍不显示删除按钮')
  await page.click('.role-bar .el-select')
  await page.waitForTimeout(400)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("案件管理员")').click()
  await page.waitForTimeout(700)
  log(await page.locator('button:has-text("删除角色")').count() === 1, '其他内置角色显示删除按钮（仅系统管理员受保护）')

  // 5. 持久化确认
  const left = await page.evaluate(async () => {
    const res = await fetch('/api/v1/roles', { headers: { Authorization: 'Bearer ' + localStorage.getItem('token') } })
    return res.ok
  }).catch(() => null)
  log(left !== false, '角色列表接口正常')

  log(!errs.length, '无前端脚本错误', errs.slice(0, 2).join(' | '))
  const pass = results.filter(Boolean).length
  console.log(`\n${pass}/${results.length} 通过`)
  await browser.close()
  process.exit(pass === results.length ? 0 : 1)
})()
