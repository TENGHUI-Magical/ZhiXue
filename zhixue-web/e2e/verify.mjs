/**
 * 智学领航前端验收脚本（本地可重复）
 *
 * 用法：
 *   npm run e2e
 *   或 BASE=http://localhost:5174 npm run e2e
 *
 * 依赖：playwright-core + axe-core（devDependencies，不进 bundle）；本机需安装 Edge。
 * 脚本会在 5174 未启动时自动拉起 Vite，结束后关闭。
 */
import { chromium } from 'playwright-core'
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const PORT = process.env.E2E_PORT || '5174'
const BASE = process.env.BASE || `http://localhost:${PORT}`
const OUT = process.env.E2E_OUT || path.resolve('../../spark-output/qa/2026-10-31-e2e')
fs.mkdirSync(OUT, { recursive: true })
const axeSource = fs.readFileSync(path.resolve('node_modules/axe-core/axe.min.js'), 'utf8')

// ---------------- mock 数据（契约形状） ----------------
const courses = [{ courseId: 1, courseName: '数据库系统概论', dimensionConfig: [
  { key: 'concept', name: '概念理解' }, { key: 'sql', name: 'SQL 应用' },
  { key: 'schema', name: '模式设计' }, { key: 'transaction', name: '事务与并发' },
  { key: 'boundary', name: '边界与异常' }
] }]
const kpNames = ['关系模型基本概念','关系代数','元组关系演算','关系完整性','规范化初步','函数依赖','范式判定','模式分解','视图与索引','关系模型小结','SQL 概述','连接查询']
const nodes = kpNames.map((name, i) => ({ id: 'KP' + String(i + 1).padStart(4, '0'), name, chapterId: i < 6 ? 2 : 3 }))
const edges = nodes.slice(1).map((n, i) => ({ source: nodes[i].id, target: n.id, relation: 'PREREQ' }))
const kpDetail = { kpId: 'KP0012', courseId: 1, chapterId: 3, chapterName: 'SQL', name: '连接查询', conceptText: '连接查询把多张表按连接条件组合。', misconception: '把笛卡儿积当连接', textbookPage: 'P88', dimensionKey: 'sql' }
const portrait = { statDate: '2026-10-29', dimensionScores: { concept: 0.72, sql: 0.55, schema: 0.61, transaction: 0.48, boundary: 0.66 }, weakestKps: [{ kpId: 'KP0012', name: '连接查询', mastery: 0.32, sampleCount: 6 }, { kpId: 'KP0006', name: '函数依赖', mastery: 0.41, sampleCount: 8 }], errorDist: { CONCEPT_CONFUSION: 4, METHOD_MISSING: 3 }, totalPractice: 18, totalError: 12 }
const errorPage = { page: 1, size: 10, total: 2, records: [
  { logId: 'LOG001', qId: 'Q_0201', kpId: 'KP0001', kpName: '关系模型基本概念', status: 'CONFIRMED', errorType: 'CONCEPT_CONFUSION', correctedText: '关系模型由数据结构、关系操作、完整性约束组成', createdAt: '2026-10-29T10:12:00' },
  { logId: 'LOG002', qId: null, kpId: 'KP0006', kpName: '函数依赖', status: 'DRAFT', errorType: 'STEP_OMISSION', createdAt: '2026-10-28T19:00:00' }
] }
const fmtDate = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const monday = (() => {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return d
})()
// 用真实当周生成计划，避免跨天后“本周指标”断言失真
const week = Array.from({ length: 7 }, (_, i) => {
  const d = new Date(monday)
  d.setDate(monday.getDate() + i)
  return fmtDate(d)
})
const plans = week.map((d, i) => ({ planId: 100 + i, planDate: d, planCount: 3, doneCount: [3,3,2,1,0,0,0][i], status: i < 3 ? 'DONE' : 'PENDING', items: [1,2,3].map(k => ({ itemId: 1000 + i * 10 + k, qId: 'Q_030' + k, kpId: 'KP000' + k, sortNo: k, isDone: k <= [3,3,2,1,0,0,0][i] })) }))
const recs = [1,2,3].map(i => ({ recommendationId: 900 + i, qId: 'Q_030' + i, layer: ['CONSOLIDATE','VARIANT','ADVANCE'][i-1], reason: '理由', difficulty: i, isAdopted: false, questionText: '题干 ' + i }))
const reviewsPage = { page: 1, size: 10, total: 2, records: [
  { logPk: 501, explainId: 'EXP501', userName: '张*', kpId: 'KP0012', errorType: 'METHOD_MISSING', model: 'bge', degraded: false, content: '讲解正文……', sources: [{ kpId: 'KP0012', textbookPage: 'P88', title: '连接查询' }], createdAt: '2026-10-29T09:20:00', questionText: '下列连接查询中，能保留左表全部行的是（ ）。', questionType: '单选', options: [{key:'A',text:'INNER JOIN'},{key:'B',text:'LEFT JOIN'},{key:'C',text:'CROSS JOIN'},{key:'D',text:'NATURAL JOIN'}], correctAnswer: 'B', analysisText: 'LEFT JOIN 保留左表全部行。', studentNote: '记错连接类型', waitingSeconds: 5400, reviewStatus: 'PENDING' },
  { logPk: 502, explainId: 'EXP502', userName: '李*', kpId: 'KP0006', errorType: 'CONCEPT_CONFUSION', model: 'fallback', degraded: true, content: '保底讲解……', sources: [], createdAt: '2026-10-29T08:05:00', questionText: null, questionType: null, options: [], correctAnswer: null, analysisText: null, studentNote: null, waitingSeconds: 18000, reviewStatus: 'PENDING' }
] }
const teacherStudents = { page: 1, size: 20, total: 2, records: [
  { userId: 2, displayName: '张*', practiceCount: 18, correctRate: 0.67, avgCostSeconds: 52, avgMastery: 0.6, errorCount: 6, planDone: 5, planTotal: 21, lastActiveAt: '2026-10-30 21:10:00' },
  { userId: 3, displayName: '李*', practiceCount: 6, correctRate: null, avgCostSeconds: 35, avgMastery: null, errorCount: 2, planDone: 1, planTotal: 21, lastActiveAt: '2026-10-28 12:00:00' }
] }
const studentLearning = { summary: teacherStudents.records[0], practices: { page: 1, size: 10, total: 1, records: [{ practiceId: 1, qId: 'Q_0301', questionText: '题干', kpId: 'KP0001', kpName: '概念', isCorrect: true, myAnswer: 'B', correctAnswer: 'B', costSeconds: 45, errorType: null, source: 'PLAN', createdAt: '2026-10-30 21:10:00' }] }, errors: [], kpMastery: [{ kpId: 'KP0012', name: '连接查询', mastery: 0.32, sampleCount: 6, errorCount: 4 }] }
const questionDetail = { qId: 'Q_0301', courseId: 1, kpId: 'KP0001', kpName: '概念', questionType: '单选', questionText: '题干', options: [{key:'A',text:'A'},{key:'B',text:'B'},{key:'C',text:'C'},{key:'D',text:'D'}], difficulty: 1 }
const answer = { qId: 'Q_0301', answerText: 'A', analysisText: '解析' }

function reply(method, pathname, role) {
  if (pathname === '/api/auth/login') {
    return { code: 0, data: { token: 'mock-login', userId: role === 'TEACHER' ? 1 : 2, username: role === 'TEACHER' ? 'T001' : '2023034743010', role }, timestamp: Date.now() }
  }
  if (method !== 'GET') return { code: 0, data: null, timestamp: Date.now() }
  if (pathname === '/api/auth/me') return { code: 0, data: { userId: role === 'TEACHER' ? 1 : 2, username: 'u', realName: role === 'TEACHER' ? '王老师' : '朱同学', role }, timestamp: Date.now() }
  if (pathname === '/api/courses') return { code: 0, data: courses, timestamp: Date.now() }
  if (/^\/api\/courses\/\d+\/knowledge-graph$/.test(pathname)) return { code: 0, data: { nodes, edges }, timestamp: Date.now() }
  if (pathname.startsWith('/api/course/kp/')) return { code: 0, data: kpDetail, timestamp: Date.now() }
  if (pathname === '/api/portraits/me') return { code: 0, data: portrait, timestamp: Date.now() }
  if (/^\/api\/portraits\/class\/\d+$/.test(pathname)) return { code: 0, data: { courseId: 1, studentCount: 2, weakestKpTop: [{ kpId: 'KP0012', name: '连接查询', avgMastery: 0.32, errorCount: 4 }], errorDist: { CONCEPT_CONFUSION: 4, METHOD_MISSING: 3 }, activeRate: 0.5 }, timestamp: Date.now() }
  if (pathname === '/api/error/records') return { code: 0, data: errorPage, timestamp: Date.now() }
  if (pathname === '/api/plans') return { code: 0, data: plans, timestamp: Date.now() }
  if (pathname === '/api/recommendations') return { code: 0, data: recs, timestamp: Date.now() }
  if (pathname === '/api/teacher/reviews') return { code: 0, data: reviewsPage, timestamp: Date.now() }
  if (pathname === '/api/teacher/students') return { code: 0, data: teacherStudents, timestamp: Date.now() }
  if (/^\/api\/teacher\/students\/\d+\/learning-records$/.test(pathname)) return { code: 0, data: studentLearning, timestamp: Date.now() }
  if (/^\/api\/questions\/[^/]+\/answer$/.test(pathname)) return { code: 0, data: answer, timestamp: Date.now() }
  if (/^\/api\/questions\/[^/]+$/.test(pathname)) return { code: 0, data: questionDetail, timestamp: Date.now() }
  return { code: 0, data: null, timestamp: Date.now() }
}

const STUDENT = [['today','/today'],['diagnosis','/diagnosis'],['error-book','/error-book'],['portrait','/portrait'],['plan','/plan'],['knowledge-graph','/knowledge-graph'],['profile','/profile']]
const TEACHER = [['teacher-reviews','/teacher/reviews'],['teacher-class','/teacher/class']]

// ---------------- 启动与断言 ----------------
async function ensureServer() {
  try {
    const res = await fetch(BASE, { signal: AbortSignal.timeout(1500) })
    if (res.ok) return null
  } catch { /* not running */ }
  const child = spawn('npm', ['run', 'dev', '--', '--port', PORT, '--strictPort'], {
    cwd: process.cwd(), stdio: 'ignore', shell: true
  })
  for (let i = 0; i < 60; i++) {
    await new Promise(r => setTimeout(r, 500))
    try {
      const res = await fetch(BASE, { signal: AbortSignal.timeout(1000) })
      if (res.ok) return child
    } catch { /* retry */ }
  }
  child.kill()
  throw new Error('Vite 未能在 30s 内启动')
}

const results = []
function check(name, condition, detail = '') {
  results.push({ name, pass: !!condition, detail })
  if (!condition) console.log('FAIL:', name, detail)
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms))

/** 轮询等待条件成立：冷启动/首次编译时固定 sleep 不可靠 */
async function waitFor(fn, timeout = 12000, interval = 250) {
  const start = Date.now()
  let last
  while (Date.now() - start < timeout) {
    try {
      last = await fn()
      if (last) return true
    } catch (e) {
      last = e
    }
    await sleep(interval)
  }
  return false
}

/** 打开页面并等到路由内容挂载、骨架屏结束 */
async function open(page, pathname) {
  await page.goto(BASE + pathname, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('#main-content', { state: 'attached', timeout: 20000 }).catch(() => {})
  const mounted = await page
    .waitForFunction(
      () => {
        const main = document.querySelector('#main-content')
        return !!main && !!main.querySelector('.page, .state-code, .el-result, .el-empty')
      },
      null,
      { timeout: 20000 }
    )
    .then(() => true)
    .catch(() => false)
  if (!mounted) throw new Error(`页面未挂载：${pathname}`)
  await page
    .waitForFunction(() => !document.querySelector('.el-skeleton'), null, { timeout: 10000 })
    .catch(() => {})
  await sleep(150)
}

/** 动态数据断言：轮询到条件成立或超时 */
async function checkNext(name, fn, detail = '', timeout = 12000) {
  const ok = await waitFor(fn, timeout)
  check(name, ok, ok ? detail : `${detail}${detail ? ' | ' : ''}等待超时 ${timeout}ms`)
}

async function newContext(browser, role, theme, viewport, options = {}) {
  const { token = true, storedRole = role, hasTouch = false } = options
  const ctx = await browser.newContext({ viewport, locale: 'zh-CN', hasTouch })
  await ctx.addInitScript(({ role, theme, token, storedRole }) => {
    if (token) localStorage.setItem('satoken', 'mock')
    localStorage.setItem('zhixue_user', JSON.stringify({ userId: role === 'TEACHER' ? 1 : 2, username: 'u', realName: 'x', role: storedRole }))
    localStorage.setItem('zhixue-theme', theme)
  }, { role, theme, token, storedRole })
  await ctx.route('**/*', async (route) => {
    const u = new URL(route.request().url())
    if (u.pathname.startsWith('/api/')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(reply(route.request().method(), u.pathname, role)) })
    } else {
      await route.continue()
    }
  })
  return ctx
}

/** 移动端触控与横向溢出审计；返回越界元素样本 */
async function mobileAudit(page) {
  return page.evaluate(() => {
    const els = Array.from(document.querySelectorAll('button, a, input, select, [role="button"]'))
    let small = 0
    const sample = []
    for (const el of els) {
      // 图谱 SVG 节点随画布缩放，无法稳定达到 44px；由下方含 role=button 的清单提供等价目标
      if (el.matches('.el-select__input, .graph-node')) continue
      const r = el.getBoundingClientRect()
      if (r.width === 0 || r.height === 0) continue
      if (r.width < 44 || r.height < 44) {
        small++
        if (sample.length < 5) {
          const cls = Array.from(el.classList).slice(0, 2).join('.')
          sample.push(`${el.tagName.toLowerCase()}${cls ? '.' + cls : ''}:${Math.round(r.width)}x${Math.round(r.height)}`)
        }
      }
    }
    return { small, sample, overflow: document.documentElement.scrollWidth > window.innerWidth + 1 }
  })
}

async function axeScan(page) {
  // Vite 首次访问新依赖时可能整页 reload，导致执行上下文被销毁：重试两次
  let lastError
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      await page.waitForLoadState('load').catch(() => {})
      await page.addScriptTag({ content: axeSource })
      const result = await page.evaluate(async () => {
        // @ts-ignore
        return await axe.run(document, { runOnly: ['wcag2a', 'wcag2aa', 'wcag21aa'] })
      })
      return result.violations
        .filter(v => ['serious', 'critical'].includes(v.impact))
        .map(v => ({ id: v.id, impact: v.impact, nodes: v.nodes.length }))
    } catch (e) {
      lastError = e
      // 页面被 reload 后重新等待内容挂载，避免扫到空白页
      await page.waitForSelector('#main-content .page', { timeout: 8000 }).catch(() => {})
      await page.waitForFunction(() => !document.querySelector('.el-skeleton'), null, { timeout: 8000 }).catch(() => {})
      await sleep(800)
    }
  }
  throw lastError
}

const server = await ensureServer()
const browser = await chromium.launch({ channel: 'msedge', headless: true })

try {
  // ---------- 学生桌面 ----------
  const studentCtx = await newContext(browser, 'STUDENT', 'glass', { width: 1440, height: 900 })
  const page = await studentCtx.newPage()

  await open(page, '/today')
  await checkNext('今日：主结论存在', async () => (await page.locator('.hero-title').count()) === 1)
  await checkNext('今日：一个主动作', async () => (await page.locator('.hero-actions .el-button--primary').count()) === 1)
  await checkNext('今日：三张指标卡', async () => (await page.locator('.metric-card').count()) === 3)
  check('今日：无横向溢出', await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1))

  await open(page, '/error-book')
  await checkNext('错题本：两行记录', async () => (await page.locator('.el-table__row').count()) >= 2)
  await checkNext('错题本：缺 qId 显示待关联', async () => (await page.getByText('待关联题目').count()) >= 1)

  await open(page, '/portrait')
  await checkNext('画像：同基线三条', async () => (await page.locator('.baseline-row').count()) === 3)
  check('画像：三环已移除', await page.locator('.rings').count() === 0)
  await checkNext('画像：薄弱点可点击', async () => (await page.locator('button.weak-item').count()) >= 1)
  await checkNext('画像：结论含掌握度', async () => (await page.locator('.hero-title').innerText()).includes('掌握度'))

  await open(page, '/plan')
  await checkNext('计划：7 天卡片', async () => (await page.locator('.day-card').count()) === 7)
  await page.getByRole('button', { name: '上一周' }).click(); await sleep(500)
  await checkNext('计划：非本周使用“该周”文案', async () => (await page.locator('.brief-eyebrow').innerText()).includes('该周'))
  await page.getByRole('button', { name: '本周' }).click(); await sleep(500)
  await page.getByRole('button', { name: '去打卡' }).first().click()
  await checkNext('计划：打卡先打开练习抽屉', async () => await page.locator('.practice-drawer.open').isVisible())
  const practiceBox = await page.locator('.practice-drawer.open').boundingBox()
  check('抽屉：铺满视口', !!practiceBox && practiceBox.height > 800, JSON.stringify(practiceBox))
  await page.keyboard.press('Escape'); await sleep(500)

  await open(page, '/knowledge-graph')
  await checkNext('图谱：缩放控件 3 个', async () => (await page.locator('.graph-controls button').count()) === 3)
  await checkNext('图谱：节点已渲染', async () => (await page.locator('.graph-node').count()) >= 1)
  const before = await page.locator('.graph-viewport').getAttribute('transform')
  await page.locator('.graph-controls button').first().click(); await sleep(400)
  const after = await page.locator('.graph-viewport').getAttribute('transform')
  check('图谱：放大改变视图', before !== after, `${before} -> ${after}`)

  // P0 回归：未到缩放上限时滚轮必须 preventDefault；到上限后必须放行页面滚动
  const wheelProbe = async () => page.evaluate(async () => {
    const svg = document.querySelector('.graph-svg')
    const rect = svg.getBoundingClientRect()
    const ev = new WheelEvent('wheel', {
      deltaY: -120,
      clientX: rect.left + rect.width / 2,
      clientY: rect.top + rect.height / 2,
      bubbles: true,
      cancelable: true
    })
    svg.dispatchEvent(ev)
    await new Promise((resolve) => requestAnimationFrame(() => resolve()))
    const scale = Number(
      (document.querySelector('.graph-viewport').getAttribute('transform') || '')
        .match(/scale\(([^)]+)\)/)?.[1] || 1
    )
    return { prevented: ev.defaultPrevented, scale }
  })
  const wheelAtNormal = await wheelProbe()
  check('图谱：未到上限滚轮拦截页面滚动', wheelAtNormal.prevented && wheelAtNormal.scale > 1,
    JSON.stringify(wheelAtNormal))
  for (let i = 0; i < 8; i++) {
    await page.locator('.graph-controls button').first().click()
  }
  await sleep(500)
  const wheelAtMax = await wheelProbe()
  check('图谱：到缩放上限后滚轮放行页面滚动',
    !wheelAtMax.prevented && wheelAtMax.scale >= 2.4, JSON.stringify(wheelAtMax))

  // 真实滚动：0.6 下限时滚轮必须交给 .main；非边界时必须阻止页面滚动
  await page.locator('.graph-controls button').last().click()
  await sleep(300)
  for (let i = 0; i < 8; i++) {
    await page.locator('.graph-controls button').nth(1).click()
  }
  await sleep(400)
  const minGraphBox = await page.locator('.graph-svg').boundingBox()
  if (!minGraphBox) throw new Error('图谱 SVG 未渲染')
  await page.mouse.move(minGraphBox.x + minGraphBox.width / 2, minGraphBox.y + minGraphBox.height / 2)
  const mainScrollBefore = await page.evaluate(() => document.querySelector('.main')?.scrollTop ?? 0)
  await page.mouse.wheel(0, 300)
  await sleep(500)
  const mainScrollAfter = await page.evaluate(() => document.querySelector('.main')?.scrollTop ?? 0)
  check('图谱：缩放下限滚轮真实滚动页面', mainScrollAfter > mainScrollBefore,
    `${mainScrollBefore}->${mainScrollAfter}`)

  await page.locator('.graph-controls button').last().click()
  await sleep(300)
  await page.locator('.graph-svg').scrollIntoViewIfNeeded()
  const normalGraphBox = await page.locator('.graph-svg').boundingBox()
  if (!normalGraphBox) throw new Error('图谱 SVG 未渲染')
  await page.mouse.move(normalGraphBox.x + normalGraphBox.width / 2, normalGraphBox.y + normalGraphBox.height / 2)
  const mainScrollBefore2 = await page.evaluate(() => document.querySelector('.main')?.scrollTop ?? 0)
  await page.mouse.wheel(0, 300)
  await sleep(500)
  const mainScrollAfter2 = await page.evaluate(() => document.querySelector('.main')?.scrollTop ?? 0)
  check('图谱：非边界滚轮不滚动页面', mainScrollAfter2 === mainScrollBefore2,
    `${mainScrollBefore2}->${mainScrollAfter2}`)
  await page.locator('.graph-controls button').last().click()
  await sleep(300)

  // P0 回归：第二触点不得重置拖拽起点（双指时不能跳图）
  await page.locator('.graph-controls button').last().click() // 重置
  await sleep(300)
  const svgBox = await page.locator('.graph-svg').boundingBox()
  if (!svgBox) throw new Error('图谱 SVG 未渲染')
  const px = svgBox.x + svgBox.width / 2
  const py = svgBox.y + svgBox.height / 2
  await page.mouse.move(px, py)
  await page.mouse.down()
  await page.mouse.move(px + 30, py + 20, { steps: 4 })
  await sleep(250)
  const panTransform = await page.locator('.graph-viewport').getAttribute('transform')
  check('图谱：拖拽平移生效', panTransform !== 'translate(0 0) scale(1)', panTransform)
  await page.evaluate(({ x, y }) => {
    const svg = document.querySelector('.graph-svg')
    const base = {
      bubbles: true, cancelable: true, pointerId: 99, pointerType: 'touch',
      isPrimary: false, button: 0, buttons: 1
    }
    svg.dispatchEvent(new PointerEvent('pointerdown', { ...base, clientX: x, clientY: y }))
    svg.dispatchEvent(new PointerEvent('pointermove', { ...base, clientX: x + 80, clientY: y + 80 }))
  }, { x: px + 40, y: py + 40 })
  await sleep(250)
  const afterSecondPointer = await page.locator('.graph-viewport').getAttribute('transform')
  check('图谱：第二触点不劫持拖拽', panTransform === afterSecondPointer,
    `${panTransform} -> ${afterSecondPointer}`)
  await page.mouse.up()
  await page.locator('.graph-controls button').last().click() // 恢复视图，避免影响后续节点点击
  await sleep(300)

  await page.locator('.graph-node').first().click()
  await checkNext('图谱：节点打开详情', async () => await page.locator('.kp-drawer').isVisible())
  await checkNext('图谱：邻接边高亮', async () => (await page.locator('.graph-edge.active').count()) >= 1)
  await checkNext('图谱：邻接节点高亮', async () => (await page.locator('.graph-node.adjacent').count()) >= 1)
  await checkNext('图谱：非邻接变暗', async () => (await page.locator('.graph-node.dim').count()) >= 1)
  await page.keyboard.press('Escape'); await sleep(400)

  await open(page, '/knowledge-graph?kpId=KP0012')
  await checkNext('图谱：kpId 深链打开详情', async () => await page.locator('.kp-drawer').isVisible())
  await page.keyboard.press('Escape'); await sleep(400)

  await open(page, '/profile')
  await checkNext('个人中心：快捷入口', async () => (await page.locator('.quick-links .el-button').count()) >= 3)

  await open(page, '/not-exist')
  await checkNext('404：未知路径兜底', async () => (await page.locator('.state-code').innerText()).trim() === '404')

  await open(page, '/403')
  await checkNext('403：无权访问页', async () => (await page.locator('.state-code').innerText()).trim() === '403')

  await open(page, '/diagnosis')
  await checkNext('诊断台：图片上传入口', async () => (await page.locator('.ocr-upload').count()) === 1)

  // 未登录：回登录并保留站内 returnUrl
  const anonCtx = await newContext(browser, 'STUDENT', 'glass', { width: 1440, height: 900 }, { token: false })
  const anonPage = await anonCtx.newPage()
  await anonPage.goto(BASE + '/today', { waitUntil: 'domcontentloaded' })
  await anonPage.waitForURL((url) => new URL(url).pathname === '/login', { timeout: 15000 }).catch(() => {})
  const loginUrl = new URL(anonPage.url())
  check('未登录：回登录并带 returnUrl', loginUrl.pathname === '/login' && loginUrl.searchParams.get('redirect') === '/today', anonPage.url())
  await anonCtx.close()

  // 登录：填写账号密码后按 returnUrl 回跳，并落 token
  const loginCtx = await newContext(browser, 'STUDENT', 'glass', { width: 1440, height: 900 }, { token: false })
  const loginPage = await loginCtx.newPage()
  await loginPage.goto(BASE + '/login?redirect=%2Ferror-book', { waitUntil: 'domcontentloaded' })
  await loginPage.waitForSelector('.login-card input', { timeout: 15000 })
  await loginPage.locator('.login-card input').nth(0).fill('2023034743010')
  await loginPage.locator('.login-card input').nth(1).fill('123456')
  await loginPage.getByRole('button', { name: '登录' }).click()
  await loginPage.waitForURL((url) => new URL(url).pathname === '/error-book', { timeout: 15000 }).catch(() => {})
  const tokenStored = await loginPage.evaluate(() => localStorage.getItem('satoken'))
  check(
    '登录：按 returnUrl 回跳并落 token',
    new URL(loginPage.url()).pathname === '/error-book' && tokenStored === 'mock-login',
    `${loginPage.url()} token=${tokenStored}`
  )
  await loginCtx.close()

  // 角色守卫
  const teacherGuard = await newContext(browser, 'TEACHER', 'glass', { width: 1440, height: 900 })
  const tp = await teacherGuard.newPage()
  await tp.goto(BASE + '/today', { waitUntil: 'domcontentloaded' })
  await tp.waitForURL((url) => new URL(url).pathname === '/teacher', { timeout: 15000 }).catch(() => {})
  check('守卫：教师访问学生页回教师首页', new URL(tp.url()).pathname === '/teacher', tp.url())
  await teacherGuard.close()

  const studentGuard = await newContext(browser, 'STUDENT', 'glass', { width: 1440, height: 900 })
  const sp = await studentGuard.newPage()
  await sp.goto(BASE + '/teacher/reviews', { waitUntil: 'domcontentloaded' })
  await sp.waitForURL('**/today', { timeout: 15000 }).catch(() => {})
  check('守卫：学生访问教师页回今日', sp.url().includes('/today'), sp.url())
  await studentGuard.close()

  // 本地缓存缺角色：先放行，/me 补角色后必须纠正到正确首页
  const unknownCtx = await newContext(browser, 'STUDENT', 'glass', { width: 1440, height: 900 }, { storedRole: '' })
  const unknownPage = await unknownCtx.newPage()
  await unknownPage.goto(BASE + '/teacher/reviews', { waitUntil: 'domcontentloaded' })
  await unknownPage.waitForURL((url) => new URL(url).pathname === '/today', { timeout: 15000 }).catch(() => {})
  check('守卫：角色未知时按 /me 纠正', new URL(unknownPage.url()).pathname === '/today', unknownPage.url())
  await unknownCtx.close()

  // ---------- 教师桌面 ----------
  const teacherCtx = await newContext(browser, 'TEACHER', 'glass', { width: 1440, height: 900 })
  const tpage = await teacherCtx.newPage()
  await open(tpage, '/teacher')
  await checkNext('教学概览：四张指标卡', async () => (await tpage.locator('.stat-card').count()) >= 4)
  await open(tpage, '/teacher/reviews')
  await checkNext('评阅：队列已渲染', async () => (await tpage.locator('.el-table__row').count()) >= 2)
  await tpage.getByRole('button', { name: '评阅' }).nth(1).click()
  await checkNext('评阅：无证据提示', async () => (await tpage.getByText('证据不完整').count()) >= 1)
  check('评阅：无证据禁用通过', await tpage.locator('.el-radio-button').first().evaluate(el => el.className.includes('is-disabled')))
  check('评阅：默认退回', await tpage.locator('.el-radio-button').nth(1).evaluate(el => el.className.includes('is-active')))
  const reviewBox = await tpage.locator('.review-drawer').boundingBox()
  check('评阅：抽屉铺满视口', !!reviewBox && reviewBox.height > 800, JSON.stringify(reviewBox))
  await tpage.keyboard.press('Escape'); await sleep(500)

  await open(tpage, '/teacher/class')
  await checkNext('班级：两名学生', async () => (await tpage.locator('.el-table__row').count()) >= 2)
  await tpage.getByRole('button', { name: '学习记录' }).first().click()
  await checkNext('班级：学习记录抽屉', async () => await tpage.locator('.student-drawer').isVisible())
  await tpage.keyboard.press('Escape'); await sleep(400)
  await teacherCtx.close()

  // ---------- 移动端 ----------
  const mobileCtx = await newContext(browser, 'STUDENT', 'glass', { width: 390, height: 844 })
  const mp = await mobileCtx.newPage()
  const MOBILE_READY = {
    today: '.metric-card',
    diagnosis: '.upload-card, .page-head',
    'error-book': '.el-table__row',
    portrait: '.baseline-row',
    plan: '.day-card',
    'knowledge-graph': '.graph-node',
    profile: '.quick-links'
  }
  for (const [name, pathname] of STUDENT) {
    await open(mp, pathname)
    await mp.waitForSelector(MOBILE_READY[name], { timeout: 10000 }).catch(() => {})
    await sleep(150)
    const info = await mobileAudit(mp)
    check(`移动端/${name}：无横向溢出`, !info.overflow)
    check(`移动端/${name}：触控≥44`, info.small === 0, `small=${info.small} ${info.sample.join(' | ')}`)
    if (name === 'knowledge-graph') {
      const tagHeights = await mp.evaluate(() =>
        Array.from(document.querySelectorAll('.node-tag')).map((el) => Math.round(el.getBoundingClientRect().height))
      )
      check(
        '移动端/图谱：清单替代目标≥44',
        tagHeights.length >= 12 && tagHeights.every((h) => h >= 44),
        `tags=${tagHeights.length} min=${tagHeights.length ? Math.min(...tagHeights) : 0}`
      )
      const jumpBox = await mp.locator('.mobile-list-jump').boundingBox()
      check('移动端/图谱：清单跳转≥44', !!jumpBox && jumpBox.height >= 44, JSON.stringify(jumpBox))

      // 真实双指：用 CDP touch 模拟，避免合成 PointerEvent 的自欺
      const pinchCtx = await newContext(browser, 'STUDENT', 'glass',
        { width: 390, height: 844 }, { hasTouch: true })
      const pinchPage = await pinchCtx.newPage()
      await open(pinchPage, '/knowledge-graph')
      await pinchPage.locator('.graph-svg').scrollIntoViewIfNeeded()
      await sleep(300)
      const pinchBox = await pinchPage.locator('.graph-svg').boundingBox()
      if (!pinchBox) throw new Error('移动端图谱未渲染')
      const scaleBefore = await pinchPage.evaluate(() => {
        const t = document.querySelector('.graph-viewport').getAttribute('transform') || ''
        return Number(t.match(/scale\(([^)]+)\)/)?.[1] || 1)
      })
      const scrollBeforePinch = await pinchPage.evaluate(() => document.querySelector('.main')?.scrollTop ?? 0)
      const cdp = await pinchCtx.newCDPSession(pinchPage)
      const cx = pinchBox.x + pinchBox.width / 2
      const cy = pinchBox.y + pinchBox.height / 2
      await cdp.send('Input.dispatchTouchEvent', {
        type: 'touchStart',
        touchPoints: [{ x: cx - 40, y: cy, id: 1 }, { x: cx + 40, y: cy, id: 2 }]
      })
      await sleep(100)
      await cdp.send('Input.dispatchTouchEvent', {
        type: 'touchMove',
        touchPoints: [{ x: cx - 80, y: cy, id: 1 }, { x: cx + 80, y: cy, id: 2 }]
      })
      await sleep(250)
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
      await sleep(300)
      const scaleAfter = await pinchPage.evaluate(() => {
        const t = document.querySelector('.graph-viewport').getAttribute('transform') || ''
        return Number(t.match(/scale\(([^)]+)\)/)?.[1] || 1)
      })
      const scrollAfterPinch = await pinchPage.evaluate(() => document.querySelector('.main')?.scrollTop ?? 0)
      check('移动端/图谱：双指 pinch 放大', scaleAfter >= 1.8, `${scaleBefore}->${scaleAfter}`)
      check('移动端/图谱：双指不滚动页面', scrollAfterPinch === scrollBeforePinch,
        `${scrollBeforePinch}->${scrollAfterPinch}`)
      await pinchCtx.close()
    }
  }
  await mobileCtx.close()

  // ---------- 教师移动端：触控与横向溢出 ----------
  const teacherMobileCtx = await newContext(browser, 'TEACHER', 'glass', { width: 390, height: 844 })
  const tmp = await teacherMobileCtx.newPage()
  const TEACHER_MOBILE_READY = { 'teacher-reviews': '.mobile-card', 'teacher-class': '.mobile-card' }
  for (const [name, pathname] of [['teacher-overview', '/teacher'], ...TEACHER]) {
    await open(tmp, pathname)
    const ready = name === 'teacher-overview' ? '.stat-card' : TEACHER_MOBILE_READY[name]
    await tmp.waitForSelector(ready, { timeout: 10000 }).catch(() => {})
    await sleep(200)
    const info = await mobileAudit(tmp)
    check(`移动端/${name}：无横向溢出`, !info.overflow)
    check(`移动端/${name}：触控≥44`, info.small === 0, `small=${info.small} ${info.sample.join(' | ')}`)
  }
  await teacherMobileCtx.close()

  // ---------- axe（双主题 × 9 页 + 移动端 3 页） ----------
  const a11y = []
  const mobileAxeCtx = await newContext(browser, 'STUDENT', 'glass', { width: 390, height: 844 })
  const mobileAxePage = await mobileAxeCtx.newPage()
  for (const [name, pathname] of [['today', '/today'], ['plan', '/plan'], ['portrait', '/portrait']]) {
    await open(mobileAxePage, pathname)
    await sleep(300)
    const v = await axeScan(mobileAxePage)
    a11y.push({ theme: 'glass', page: `mobile-${name}`, violations: v })
    check(`a11y/mobile/${name}`, v.length === 0, JSON.stringify(v))
  }
  await mobileAxeCtx.close()

  const teacherAxeCtx = await newContext(browser, 'TEACHER', 'glass', { width: 390, height: 844 })
  const teacherAxePage = await teacherAxeCtx.newPage()
  for (const [name, pathname] of [['teacher-overview', '/teacher'], ...TEACHER]) {
    await open(teacherAxePage, pathname)
    await sleep(300)
    const v = await axeScan(teacherAxePage)
    a11y.push({ theme: 'glass', page: `mobile-${name}`, violations: v })
    check(`a11y/mobile/${name}`, v.length === 0, JSON.stringify(v))
  }
  await teacherAxeCtx.close()

  for (const theme of ['glass', 'glass-dark']) {
    const ctx = await newContext(browser, 'STUDENT', theme, { width: 1440, height: 900 })
    const p = await ctx.newPage()
    for (const [name, pathname] of STUDENT) {
      await open(p, pathname)
      await sleep(300)
      const v = await axeScan(p)
      a11y.push({ theme, page: name, violations: v })
      check(`a11y/${theme}/${name}`, v.length === 0, JSON.stringify(v))
    }
    await ctx.close()
    const tctx = await newContext(browser, 'TEACHER', theme, { width: 1440, height: 900 })
    const tp2 = await tctx.newPage()
    for (const [name, pathname] of TEACHER) {
      await open(tp2, pathname)
      await sleep(300)
      const v = await axeScan(tp2)
      a11y.push({ theme, page: name, violations: v })
      check(`a11y/${theme}/${name}`, v.length === 0, JSON.stringify(v))
    }
    await tctx.close()
  }
  await studentCtx.close()

  fs.writeFileSync(path.join(OUT, 'e2e-report.json'), JSON.stringify({ base: BASE, results, a11y }, null, 2), 'utf8')
  const failed = results.filter(r => !r.pass)
  console.log(`\nE2E: ${results.length - failed.length}/${results.length} passed`)
  console.log(`a11y: ${a11y.filter(a => a.violations.length === 0).length}/${a11y.length} pages clean`)
  console.log('REPORT:', path.join(OUT, 'e2e-report.json'))
  if (failed.length) process.exitCode = 1
} finally {
  await browser.close()
  if (server) server.kill()
}
