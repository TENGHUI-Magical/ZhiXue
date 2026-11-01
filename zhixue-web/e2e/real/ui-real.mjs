/**
 * 真实前端 + 真实后端联调（不 mock API）
 *
 * 前置：
 *   - 后端 8080、MySQL/Redis 可用；已有种子数据
 *   - Vite 已启动（默认 http://localhost:5174）
 *   - E2E_USER / E2E_PASS 为可登录的种子账号
 *
 * 用法（PowerShell）：
 *   $env:E2E_USER='student01'; $env:E2E_PASS='123456'; npm run e2e:real:ui
 *
 * 注意：会真实提交 1 条教师评阅（若队列有未评阅且有证据的记录）；仅用于本地/测试库。
 */
import fs from 'node:fs'
import path from 'node:path'
import { chromium } from 'playwright-core'

const BASE = process.env.E2E_WEB || 'http://localhost:5174'
const USER = process.env.E2E_USER
const PASS = process.env.E2E_PASS
const EXPLAIN_ID = process.env.EXPLAIN_ID || ''
const OUT =
  process.env.E2E_REAL_OUT ||
  path.resolve('../../spark-output/qa/2026-11-01-real')
fs.mkdirSync(OUT, { recursive: true })

if (!USER || !PASS) {
  console.error('缺少 E2E_USER / E2E_PASS；请先设置种子账号口令。')
  process.exit(2)
}

const results = []
const consoleErrors = []
const httpErrors = []
const pass = (name, detail = '') => { results.push({ name, pass: true, detail }); console.log('PASS', name, detail) }
const fail = (name, detail = '') => { results.push({ name, pass: false, detail }); console.log('FAIL', name, detail) }
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const browser = await chromium.launch({ channel: 'msedge', headless: true })

async function newPage() {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' })
  const page = await ctx.newPage()
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 300)) })
  page.on('pageerror', (e) => consoleErrors.push('PAGEERROR ' + e.message.slice(0, 300)))
  page.on('response', (r) => {
    if (r.url().includes('/api/') && r.status() >= 400) httpErrors.push(`${r.status()} ${r.url()}`)
  })
  return { ctx, page }
}

async function login(page, username, password) {
  await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.login-card input', { timeout: 20000 })
  await page.locator('.login-card input').nth(0).fill(username)
  await page.locator('.login-card input').nth(1).fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL((url) => ['/today', '/teacher'].includes(new URL(url).pathname), { timeout: 20000 })
}

async function open(page, pathname, ready) {
  await page.goto(BASE + pathname, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('#main-content .page, #main-content .state-code', { timeout: 20000 }).catch(() => {})
  if (ready) await page.waitForSelector(ready, { timeout: 15000 }).catch(() => {})
  await sleep(400)
}

async function shot(page, name) {
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: false })
}

try {
  const { ctx: sctx, page: sp } = await newPage()
  await login(sp, USER, PASS)
  pass('真实登录学生', new URL(sp.url()).pathname)

  await open(sp, '/today', '.metric-card')
  const todayText = await sp.locator('#main-content').innerText()
  if (todayText.includes('今日建议')) pass('今日：真实建议卡')
  else fail('今日：真实建议卡', todayText.slice(0, 120))
  if ((await sp.locator('.metric-card').count()) === 3) pass('今日：三张指标卡')
  else fail('今日：三张指标卡')
  await shot(sp, 'student-today')

  await open(sp, '/error-book', '.el-table__row')
  const rows = await sp.locator('.el-table__row').count()
  if (rows >= 2) pass('错题本：真实记录', `rows=${rows}`)
  else fail('错题本：真实记录', `rows=${rows}`)
  await shot(sp, 'student-error-book')

  await open(sp, '/portrait', '.baseline-row')
  if ((await sp.locator('.baseline-row').count()) === 3) pass('画像：三条同基线')
  else fail('画像：三条同基线')
  const portraitText = await sp.locator('#main-content').innerText()
  if (portraitText.includes('掌握度')) pass('画像：真实结论', portraitText.match(/掌握度\s*\d+%/)?.[0] || '')
  else fail('画像：真实结论')
  await shot(sp, 'student-portrait')

  await open(sp, '/plan', '.day-card')
  const days = await sp.locator('.day-card').count()
  if (days === 7) pass('计划：7 天', `days=${days}`)
  else fail('计划：7 天', `days=${days}`)
  await shot(sp, 'student-plan')

  await open(sp, '/knowledge-graph', '.graph-node')
  const nodes = await sp.locator('.graph-node').count()
  if (nodes >= 20) pass('图谱：真实节点', `nodes=${nodes}`)
  else fail('图谱：真实节点', `nodes=${nodes}`)
  await sp.locator('.graph-node').first().click()
  await sp.waitForSelector('.kp-drawer', { state: 'visible', timeout: 10000 }).catch(() => {})
  if (await sp.locator('.kp-drawer').isVisible()) pass('图谱：真实详情抽屉')
  else fail('图谱：真实详情抽屉')
  await sleep(300)
  if ((await sp.locator('.graph-edge.active').count()) >= 1) pass('图谱：真实邻接边高亮')
  else fail('图谱：真实邻接边高亮')
  if ((await sp.locator('.graph-node.dim').count()) >= 1) pass('图谱：真实非邻接变暗')
  else fail('图谱：真实非邻接变暗')
  await shot(sp, 'student-graph')
  await sp.keyboard.press('Escape'); await sleep(300)

  await open(sp, '/diagnosis')
  if (await sp.locator('.ocr-upload').count()) pass('诊断台：真实页面')
  else fail('诊断台：真实页面')
  await shot(sp, 'student-diagnosis')

  await open(sp, '/profile', '.quick-links')
  if ((await sp.locator('.quick-links .el-button').count()) >= 3) pass('个人中心：快捷入口')
  else fail('个人中心：快捷入口')
  await shot(sp, 'student-profile')
  await sctx.close()

  // ---------- 教师 ----------
  const { ctx: tctx, page: tp } = await newPage()
  await login(tp, process.env.E2E_TEACHER_USER || USER, process.env.E2E_TEACHER_PASS || PASS)
  pass('真实登录教师', new URL(tp.url()).pathname)
  await open(tp, '/teacher', '.stat-card')
  const statCards = await tp.locator('.stat-card').count()
  if (statCards >= 4) pass('教学概览：四指标', `cards=${statCards}`)
  else fail('教学概览：四指标', `cards=${statCards}`)
  await shot(tp, 'teacher-overview')

  await open(tp, '/teacher/class', '.el-table__row')
  const studentRows = await tp.locator('.el-table__row').count()
  if (studentRows >= 1) pass('班级学情：真实学生', `rows=${studentRows}`)
  else fail('班级学情：真实学生', `rows=${studentRows}`)
  await shot(tp, 'teacher-class')

  await open(tp, '/teacher/reviews', '.el-table__row')
  const reviewRows = await tp.locator('.el-table__row').count()
  if (reviewRows >= 1) pass('评阅队列：真实记录', `rows=${reviewRows}`)
  else fail('评阅队列：真实记录', `rows=${reviewRows}`)

  let target = tp.locator('.el-table__row').first()
  if (EXPLAIN_ID) {
    const byId = tp.locator('.el-table__row', { hasText: EXPLAIN_ID })
    if (await byId.count()) target = byId.first()
  }
  await target.getByRole('button', { name: '评阅' }).click()
  await tp.waitForSelector('.review-drawer', { state: 'visible', timeout: 10000 }).catch(() => {})
  if (await tp.locator('.review-drawer').isVisible()) pass('评阅：真实抽屉')
  else fail('评阅：真实抽屉')
  await shot(tp, 'teacher-review-drawer')

  const alreadyReviewed = (await tp.getByText('该讲解已评阅，不能重复提交。').count()) >= 1
  if (alreadyReviewed) {
    pass('评阅：已评阅只读')
  } else {
    const passDisabled = await tp.locator('.el-radio-button').first().evaluate((el) => el.className.includes('is-disabled')).catch(() => true)
    await tp.locator('.score-pill').nth(3).click()
    await tp.locator('.review-drawer textarea').fill('真实联调评阅：讲解与错因匹配，引用可核验。')
    if (passDisabled) await tp.locator('.el-radio-button').nth(1).click()
    await tp.getByRole('button', { name: '提交评阅' }).click()
    const submitted = await tp.getByText('评阅已提交').first().waitFor({ state: 'visible', timeout: 10000 }).then(() => true).catch(() => false)
    const drawerClosed = await tp.locator('.review-drawer').waitFor({ state: 'hidden', timeout: 10000 }).then(() => true).catch(() => false)
    if (submitted || drawerClosed) pass('评阅：真实提交成功', passDisabled ? '证据不完整→退回' : '证据完整→通过')
    else fail('评阅：真实提交成功', '未出现“评阅已提交”且抽屉未关闭')
  }
  await sleep(600)
  await shot(tp, 'teacher-review-submitted')
  await tctx.close()
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.pass)
fs.writeFileSync(`${OUT}/ui-real-report.json`, JSON.stringify({ base: BASE, results, consoleErrors, httpErrors }, null, 2))
console.log(`\nUI REAL: ${results.length - failed.length}/${results.length} passed`)
console.log('consoleErrors', consoleErrors.length, 'httpErrors', httpErrors.length)
console.log('REPORT:', `${OUT}/ui-real-report.json`)
if (consoleErrors.length) console.log(consoleErrors.slice(0, 5))
if (httpErrors.length) console.log(httpErrors.slice(0, 5))
if (failed.length) process.exitCode = 1