const { chromium } = require('playwright-core');
(async () => {
  const b = await chromium.launch({ channel: 'msedge', headless: true });
  const p = await b.newPage();
  const errs = [];
  p.on('response', r => { if (r.url().includes('/api/') && r.status() >= 400) errs.push(`${r.status()} ${r.url()}`); });
  await p.goto('http://192.168.2.209:8080/login', { waitUntil: 'networkidle' });
  await p.fill('input[type="text"], input:not([type="password"])', 'admin');
  await p.fill('input[type="password"]', process.env.GSMS_PASSWORD || '');
  await Promise.all([
    p.waitForResponse(r => r.url().includes('/auth/login'), { timeout: 15000 }).catch(() => null),
    p.click('button:has-text("登"), button[type="submit"]')
  ]);
  await p.waitForTimeout(2500);
  console.log('URL:', p.url());
  console.log('本地存储含 token:', await p.evaluate(() => !!(localStorage.getItem('gsms_token') || localStorage.getItem('token'))));
  console.log('4xx 接口:', errs.length ? errs.join(' | ') : '无');
  await b.close();
})();
