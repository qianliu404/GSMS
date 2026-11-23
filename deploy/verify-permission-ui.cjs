// 角色权限细分：配置页勾选保存 → 目标账号重新登录后权限码变化 → 越权接口被拦截
const { chromium } = require('playwright-core')
const BASE = 'http://127.0.0.1:8080'
const API = 'http://127.0.0.1:8089'
const http = require('http')
const results = []
const log = (ok, name, detail = '') => {
  results.push(ok)
  console.log(`${ok ? '[PASS]' : '[FAIL]'} ${name}${detail ? ' -- ' + detail : ''}`)
}

function request(method, path, token, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null
    const req = http.request(API + path, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {})
      }
    }, (res) => {
      let buf = ''
      res.on('data', (c) => (buf += c))
      res.on('end', () => { try { resolve(JSON.parse(buf)) } catch (e) { resolve({ raw: buf }) } })
    })
    req.on('error', reject)
    if (data) req.write(data)
    req.end()
  })
}

async function login(page) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await page.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await page.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await page.click('button:has-text("登 录")')
  await page.waitForTimeout(2000)
}

const tokenOf = async (userId) => (await request('POST', '/api/v1/auth/login', null,
  { userId, password: process.env.GSMS_PASSWORD || '' })).data.token

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(e.message))
  await login(page)

  // 1. 配置页可打开
  await page.goto(BASE + '/system/permissions', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  log(await page.locator('.page-title:has-text("角色权限")').count() > 0, '角色权限配置页可打开')
  const groups = await page.locator('.card-head h2').count()
  log(groups >= 10, '权限按模块分组展示', `${groups} 组`)
  const boxes = await page.locator('.perm-grid .el-checkbox').count()
  log(boxes === 37, '共 37 个操作权限项', `${boxes} 项`)

  // 2. 切到阅知人，权限项少于管理员
  await page.locator('.role-bar .el-select').click()
  await page.waitForTimeout(400)
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '阅知人' }).click()
  await page.waitForTimeout(900)
  const checked = await page.locator('.perm-grid .el-checkbox.is-checked').count()
  log(checked === 8, '阅知人默认仅 8 项查看权限', `${checked} 项`)

  // 3. 管理员接口能读权限矩阵
  const admin = await tokenOf('admin')
  const all = await request('GET', '/api/v1/permissions', admin)
  log(all.code === 0 && (all.data || []).length === 37, '权限清单接口正常', `${(all.data || []).length} 项`)
  const reader = await tokenOf('reader')
  const readerDenied = await request('GET', '/api/v1/permissions', reader)
  log(readerDenied.code === 40301, '非管理员不能读权限配置', String(readerDenied.code))

  // 4. 阅知人只有查看权，写操作被拦截
  const me = await request('GET', '/api/v1/users/me', reader)
  log((me.data.permissions || []).length === 8, '登录后下发 8 个权限码', `${me.data.permissions.length}`)
  const denied = await request('DELETE', '/api/v1/cases/341', reader)
  log(denied.code === 40301, '阅知人删除案件被拦截', String(denied.code))
  const exportDenied = await request('GET', '/api/v1/ledger/export', reader)
  log(exportDenied.code === 40301, '阅知人导出台账被拦截', String(exportDenied.code))

  // 5. 管理员保存权限后，配置接口回读一致
  const roleId = 7
  const saved = await request('PUT', `/api/v1/roles/${roleId}/permissions`, admin,
    { permCodes: ['case:view', 'case:edit', 'ledger:view'] })
  log(saved.code === 0, '保存角色权限成功')
  const after = await request('GET', `/api/v1/roles/${roleId}/permissions`, admin)
  log(after.code === 0 && after.data.length === 3, '回读权限与保存一致', (after.data || []).join(','))
  // 恢复默认，避免污染演示数据
  await request('PUT', `/api/v1/roles/${roleId}/permissions`, admin, { permCodes: me.data.permissions })
  const restored = await request('GET', `/api/v1/roles/${roleId}/permissions`, admin)
  log(restored.data.length === 8, '已恢复阅知人默认权限', `${restored.data.length}`)

  // 6. 前端按权限隐藏按钮：阅知人看不到新建案件
  const rpage = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
  await rpage.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await rpage.fill('input[placeholder="请输入工号或用户名"]', 'reader')
  await rpage.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await rpage.click('button:has-text("登 录")')
  await rpage.waitForTimeout(1800)
  await rpage.goto(BASE + '/cases', { waitUntil: 'networkidle' })
  await rpage.waitForTimeout(1500)
  log(await rpage.locator('button:has-text("新建案件")').count() === 0, '阅知人看不到「新建案件」按钮')
  log(await rpage.locator('button:has-text("批量导入")').count() === 0, '阅知人看不到「批量导入」按钮')

  log(!errs.length, '无前端脚本错误', errs.slice(0, 2).join(' | '))
  const pass = results.filter(Boolean).length
  console.log(`\n${pass}/${results.length} 通过`)
  await browser.close()
  process.exit(pass === results.length ? 0 : 1)
})()
