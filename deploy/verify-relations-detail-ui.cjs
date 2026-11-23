// 案件详情「争议关联案件」卡片：关联案详情展示、双向可见、跳转与移动端
const { chromium } = require('playwright-core')
const BASE = 'http://127.0.0.1:8080'
const OUT = 'E:/GSMS/deploy/shots/relations'
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

const cardText = (page) =>
  page.evaluate(() => {
    const cards = [...document.querySelectorAll('.card')]
    const card = cards.find((c) => c.querySelector('.card-head h2')?.innerText.trim() === '争议关联案件')
    return card ? card.innerText.replace(/\s+/g, ' ').trim() : null
  })

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1500, height: 1000 } })
  const page = await ctx.newPage()
  const errs = []
  page.on('pageerror', (e) => errs.push(e.message))

  await login(page)

  // 1. 工伤案件详情：争议关联卡片展示关联仲裁案详情
  await page.goto(BASE + '/cases/124', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1800)
  const t1 = await cardText(page)
  log(!!t1, '详情页出现「争议关联案件」卡片', t1 ? t1.slice(0, 40) : '无卡片')
  for (const [label, value] of [
    ['类型标签', '仲裁'],
    ['关联案号', 'ZS2026110005'],
    ['案件名称', '劳动仲裁测试'],
    ['当事人', '李四'],
    ['厂区', '五期'],
    ['班组', '生产车间'],
    ['关联说明', '1111'],
    ['当前阶段', '当前阶段 结案'],
    ['关联时间', '关联于 2026-11-21']
  ]) {
    log(!!t1 && t1.includes(value), `${label}正确展示`, value)
  }
  log(!!t1 && t1.includes('解除') && !t1.includes('添加关联'), '保留解除入口、已移除添加关联按钮')

  // 2. 关联页签已不再重复出现
  const tabNames = await page.evaluate(() =>
    [...document.querySelectorAll('.detail-tabs .el-tabs__item')].map((e) => e.innerText.trim()).join('|')
  )
  log(!tabNames.includes('关联'), '重复的「关联」页签已移除', tabNames)

  // 3. 点案号跳转到关联案件详情，并保留来源
  const relNo = page.locator('.rel-no').first()
  await relNo.scrollIntoViewIfNeeded()
  await relNo.click()
  await page.waitForTimeout(1800)
  log(/\/cases\/223\?from=/.test(page.url()), '点击关联案号跳转到该案详情', page.url())
  const back = await page.evaluate(() => document.querySelector('.mb-12 button')?.innerText.trim() || '')
  log(back === '‹ 返回上一案件', '返回文案按来源显示', back)
  await page.click('.mb-12 button')
  await page.waitForTimeout(1600)
  log(page.url().includes('/cases/124'), '返回原案件详情', page.url())

  // 4. 反向可见：仲裁案详情也能看到这张工伤案关联
  const t2 = await cardText(page)
  await page.goto(BASE + '/cases/223', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1800)
  const reverse = await cardText(page)
  log(!!reverse && reverse.includes('GY2026110019') && reverse.includes('工伤'), '关联双向可见', reverse)
  log(!!t2 && t2.includes('ZS2026110005'), '原案件关联未受影响')

  // 5. 无关联案件：只显示空状态，没有添加入口
  await page.goto(BASE + '/cases/120', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1600)
  const empty = await cardText(page)
  log(!!empty && empty.includes('暂无关联案件'), '无关联时显示空状态', empty)
  const addBtn = await page.locator('.rel-add').count()
  log(addBtn === 0, '详情页不存在「添加关联」按钮', `${addBtn} 个`)

  // 6. 移动端无横向溢出、卡片可读
  const mobile = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const mp = await mobile.newPage()
  mp.on('pageerror', (e) => errs.push(e.message))
  await mp.goto(BASE + '/login', { waitUntil: 'networkidle' })
  await mp.fill('input[placeholder="请输入工号或用户名"]', 'admin')
  await mp.fill('input[placeholder="请输入登录密码"]', process.env.GSMS_PASSWORD || '')
  await mp.click('button:has-text("登 录")')
  await mp.waitForTimeout(2200)
  await mp.goto(BASE + '/cases/124', { waitUntil: 'networkidle' })
  await mp.waitForTimeout(1600)
  const overflow = await mp.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
  log(!overflow, '移动端无横向溢出')
  const mt = await cardText(mp)
  log(!!mt && mt.includes('ZS2026110005'), '移动端争议关联卡片可见')
  await mp.screenshot({ path: OUT + '/detail-rel-mobile.png', fullPage: false })

  log(!errs.length, '无前端脚本错误', errs.slice(0, 3).join(' | '))

  const pass = results.filter(Boolean).length
  console.log(`\n${pass}/${results.length} 通过`)
  await browser.close()
  process.exit(pass === results.length ? 0 : 1)
})()
