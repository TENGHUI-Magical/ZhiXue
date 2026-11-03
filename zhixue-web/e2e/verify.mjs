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
// misconception 故意使用字面 \n（反斜杠+n 两个字符），与种子实际形态一致：
// seed-kp-ch05.sql 的 KP0501 在 SQL 里写 '\\n'，MySQL 转义后入库即字面 \n，显示层必须归一化
const kpDetail = { kpId: 'KP0012', courseId: 1, chapterId: 3, chapterName: 'SQL', name: '连接查询', conceptText: '连接查询把多张表按连接条件组合。'.repeat(80), misconception: '把笛卡儿积当连接\\n第二段仍应可见', textbookPage: 'P88', dimensionKey: 'sql' }
const portrait = { statDate: '2026-10-29', dimensionScores: { concept: 0.72, sql: 0.55, schema: 0.61, transaction: 0.48, boundary: 0.66 }, weakestKps: [{ kpId: 'KP0012', name: '连接查询', mastery: 0.32, sampleCount: 6 }, { kpId: 'KP0006', name: '函数依赖', mastery: 0.41, sampleCount: 8 }], errorDist: { CONCEPT_CONFUSION: 4, METHOD_MISSING: 3 }, totalPractice: 18, totalError: 12 }
const errorPage = { page: 1, size: 10, total: 3, records: [
  { logId: 'LOG001', qId: 'Q_0201', kpId: 'KP0001', kpName: '关系模型基本概念', status: 'CONFIRMED', errorType: 'CONCEPT_CONFUSION', correctedText: '关系模型由数据结构、关系操作、完整性约束组成', createdAt: '2026-10-29T10:12:00', reviewResult: 'PASS', reviewComment: null, reviewedAt: '2026-11-03 09:00:00' },
  { logId: 'LOG002', qId: null, kpId: 'KP0006', kpName: '函数依赖', status: 'DRAFT', errorType: 'STEP_OMISSION', createdAt: '2026-10-28T19:00:00', reviewResult: 'RETURN', reviewComment: '请补充连接条件的书写', reviewedAt: '2026-11-03 09:10:00' },
  { logId: 'LOG003', qId: 'Q_0303', kpId: 'KP0003', kpName: '连接查询', status: 'CONFIRMED', errorType: 'CONCEPT_CONFUSION', correctedText: '题干 3', createdAt: '2026-10-28T18:00:00', reviewResult: null, reviewComment: null, reviewedAt: null }
] }
const explainLogs = {
  EXP001: { explainId: 'EXP001', traceId: 'T001', model: 'deepseek', degraded: false, degradeLevel: null, content: '讲解正文一', sources: [{ kpId: 'KP0001', textbookPage: 'P34', title: '关系模型' }], blocked: false, createdAt: '2026-11-01 08:00:00', reviewResult: 'PASS', reviewComment: '讲解准确，可继续练习', reviewedAt: '2026-11-03 09:00:00' },
  EXP002: { explainId: 'EXP002', traceId: 'T002', model: 'deepseek', degraded: false, degradeLevel: null, content: '讲解正文二', sources: [{ kpId: 'KP0006', textbookPage: 'P88', title: '函数依赖' }], blocked: false, createdAt: '2026-11-01 08:05:00', reviewResult: 'RETURN', reviewComment: '请补充连接条件的书写', reviewedAt: '2026-11-03 09:10:00' },
  EXP003: { explainId: 'EXP003', traceId: 'T003', model: 'deepseek', degraded: false, degradeLevel: null, content: '讲解正文三', sources: [], blocked: false, createdAt: '2026-11-01 08:10:00', reviewResult: null, reviewComment: null, reviewedAt: null },
  EXP501: { explainId: 'EXP501', traceId: 'T501', model: 'deepseek', degraded: false, degradeLevel: null, content: '待评阅讲解一', sources: [], blocked: false, createdAt: '2026-11-01 08:20:00', reviewResult: null, reviewComment: null, reviewedAt: null },
  EXP502: { explainId: 'EXP502', traceId: 'T502', model: 'deepseek', degraded: false, degradeLevel: null, content: '待评阅讲解二', sources: [], blocked: false, createdAt: '2026-11-01 08:25:00', reviewResult: null, reviewComment: null, reviewedAt: null }
}
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
const reviewsPage = { page: 1, size: 10, total: 4, records: [
  { logPk: 501, explainId: 'EXP501', userName: '张*', kpId: 'KP0012', errorType: 'METHOD_MISSING', model: 'bge', degraded: false, content: '讲解正文……', sources: [{ kpId: 'KP0012', textbookPage: 'P88', title: '连接查询' }], createdAt: '2026-10-29T09:20:00', questionText: '下列连接查询中，能保留左表全部行的是（ ）。', questionType: '单选', options: [{key:'A',text:'INNER JOIN'},{key:'B',text:'LEFT JOIN'},{key:'C',text:'CROSS JOIN'},{key:'D',text:'NATURAL JOIN'}], correctAnswer: 'B', analysisText: 'LEFT JOIN 保留左表全部行。', studentNote: '记错连接类型', waitingSeconds: 5400, reviewStatus: 'PENDING' },
  { logPk: 502, explainId: 'EXP502', userName: '李*', kpId: 'KP0006', errorType: 'CONCEPT_CONFUSION', model: 'fallback', degraded: true, content: '保底讲解……', sources: [], createdAt: '2026-10-29T08:05:00', questionText: null, questionType: null, options: [], correctAnswer: null, analysisText: null, studentNote: null, waitingSeconds: 18000, reviewStatus: 'PENDING' },
  { logPk: 503, explainId: 'EXP001', userName: '王同学', kpId: 'KP0001', errorType: 'CONCEPT_CONFUSION', model: 'deepseek', degraded: false, content: '已通过讲解', sources: [], createdAt: '2026-10-28T08:00:00', questionText: '题干 1', questionType: '单选', options: [], correctAnswer: 'A', analysisText: '解析', studentNote: null, waitingSeconds: 1200, reviewStatus: 'REVIEWED', reviewResult: 'PASS' },
  { logPk: 504, explainId: 'EXP002', userName: '赵同学', kpId: 'KP0006', errorType: 'CONCEPT_CONFUSION', model: 'deepseek', degraded: false, content: '被退回讲解', sources: [], createdAt: '2026-10-28T09:00:00', questionText: '题干 2', questionType: '单选', options: [], correctAnswer: 'B', analysisText: '解析', studentNote: null, waitingSeconds: 2400, reviewStatus: 'REVIEWED', reviewResult: 'RETURN' }
] }
const teacherStudents = { page: 1, size: 20, total: 2, records: [
  { userId: 2, displayName: '张*', practiceCount: 18, correctRate: 0.55, avgCostSeconds: 52, avgMastery: 0.6, errorCount: 6, planDone: 5, planTotal: 21, lastActiveAt: '2026-10-30 21:10:00' },
  { userId: 3, displayName: '李*', practiceCount: 6, correctRate: null, avgCostSeconds: 35, avgMastery: null, errorCount: 2, planDone: 1, planTotal: 21, lastActiveAt: '2026-10-28 12:00:00' }
] }
const studentLearning = { summary: teacherStudents.records[0], practices: { page: 1, size: 10, total: 1, records: [{ practiceId: 1, qId: 'Q_0301', questionText: '题干', kpId: 'KP0001', kpName: '概念', isCorrect: true, myAnswer: 'B', correctAnswer: 'B', costSeconds: 45, errorType: null, source: 'PLAN', createdAt: '2026-10-30 21:10:00' }] }, errors: [], kpMastery: [{ kpId: 'KP0012', name: '连接查询', mastery: 0.32, sampleCount: 6, errorCount: 4 }] }
const questionDetail = { qId: 'Q_0301', courseId: 1, kpId: 'KP0001', kpName: '概念', questionType: '单选', questionText: '题干', options: [{key:'A',text:'A'},{key:'B',text:'B'},{key:'C',text:'C'},{key:'D',text:'D'}], difficulty: 1 }
const answer = { qId: 'Q_0301', answerText: 'A', analysisText: '解析' }
const nextQuestionDetail = { ...questionDetail, qId: 'Q_0302', questionText: '题干 2' }
const nextAnswer = { qId: 'Q_0302', answerText: 'B', analysisText: '解析 2' }
const thirdQuestionDetail = { ...questionDetail, qId: 'Q_0303', questionText: '题干 3' }
const thirdAnswer = { qId: 'Q_0303', answerText: 'C', analysisText: '解析 3' }
const ocrStem = '这是一道足够长度的题干'
const defaultDueReviews = [{ source: 'DUE_REVIEW', kpId: 'KP0006', kpName: '函数依赖', qId: 'Q_0306', questionText: '题干 6', difficulty: 2, daysSinceLastCorrect: 41, mastery: 0.44, sampleCount: 5, reason: '距上次答对 41 天，已过半衰期 + 概念混淆型错误' }]
let dueReviewsMock = defaultDueReviews
let explainRequestCount = 0
let mapRequestCount = 0
let ocrConfidence = 0.95
let recommendationDelayMs = 0
// 打卡判分开关：回归“判错结果态不得出现内部占位文案”时置 false
let checkinIsCorrect = true
let lastPracticePayload = null
let lastStartPayload = null
let lastErrorRecordPayload = null

function reply(method, pathname, role, search = '') {
  if (method === 'POST' && pathname === '/api/practices/start') {
    return { code: 0, data: { clientToken: 'mock-start', startedAt: '2026-11-03 12:00:00' }, timestamp: Date.now() }
  }
  if (method === 'POST' && pathname === '/api/practices') {
    return { code: 0, data: { practiceId: 777, duplicate: false, gradingMode: 'AUTO', isCorrect: true, costSeconds: 42 }, timestamp: Date.now() }
  }
  if (method === 'POST' && /^\/api\/plans\/\d+\/checkin$/.test(pathname)) {
    return { code: 0, data: { practiceId: 778, duplicate: false, gradingMode: 'AUTO', isCorrect: checkinIsCorrect, verified: true }, timestamp: Date.now() }
  }
  if (method === 'POST' && pathname === '/api/error/upload') {
    return { code: 0, data: { taskId: 'OCR1' }, timestamp: Date.now() }
  }
  if (method === 'GET' && pathname === '/api/error/tasks/OCR1') {
    return { code: 0, data: { taskId: 'OCR1', status: 'SUCCESS', confidence: ocrConfidence, fields: { stem: ocrStem } }, timestamp: Date.now() }
  }
  if (method === 'POST' && pathname === '/api/diagnosis/map') {
    mapRequestCount += 1
    return { code: 0, data: { decision: 'AUTO', candidates: [{ kpId: 'KP0001', name: '概念', score: 0.9 }] }, timestamp: Date.now() }
  }
  if (method === 'POST' && pathname === '/api/error/records') {
    return { code: 0, data: { logId: 'LOGAUTO', status: 'DRAFT' }, timestamp: Date.now() }
  }
  if (method === 'PUT' && pathname === '/api/error/records/LOGAUTO/confirm') {
    return { code: 0, data: { logId: 'LOGAUTO', status: 'CONFIRMED' }, timestamp: Date.now() }
  }
  if (pathname === '/api/auth/login') {
    return { code: 0, data: { token: 'mock-login', userId: role === 'TEACHER' ? 1 : 2, username: role === 'TEACHER' ? 'T001' : '2023034743010', role }, timestamp: Date.now() }
  }
  if (method !== 'GET') return { code: 0, data: null, timestamp: Date.now() }
  if (pathname === '/api/auth/me') return { code: 0, data: { userId: role === 'TEACHER' ? 1 : 2, username: 'u', realName: role === 'TEACHER' ? '王老师' : '朱同学', role }, timestamp: Date.now() }
  if (pathname === '/api/courses') return { code: 0, data: courses, timestamp: Date.now() }
  if (/^\/api\/courses\/\d+\/knowledge-graph$/.test(pathname)) return { code: 0, data: { nodes, edges }, timestamp: Date.now() }
  // 章节主键与教材章号故意逆序错位（3→第1章、2→第2章），用于回归“显示章号==教材章号”且选项按教材章号排序
  if (/^\/api\/courses\/\d+\/chapters$/.test(pathname)) return { code: 0, data: [
    { chapterId: 3, chapterNo: 1, title: '绪论' },
    { chapterId: 2, chapterNo: 2, title: '关系模型' }
  ], timestamp: Date.now() }
  if (pathname.startsWith('/api/course/kp/')) return { code: 0, data: kpDetail, timestamp: Date.now() }
  if (pathname === '/api/portraits/me') return { code: 0, data: portrait, timestamp: Date.now() }
  if (/^\/api\/portraits\/class\/\d+$/.test(pathname)) return { code: 0, data: { courseId: 1, studentCount: 2, weakestKpTop: [
    { kpId: 'KP0012', name: '连接查询', avgMastery: 0.32, errorCount: 4 },
    { kpId: 'KP0006', name: '函数依赖', avgMastery: 0.41, errorCount: 3 },
    { kpId: 'KP0003', name: '关系完整性', avgMastery: 0.46, errorCount: 2 },
    { kpId: 'KP0201', name: '关系模型', avgMastery: 0.52, errorCount: 2 },
    { kpId: 'KP0303', name: '事务并发', avgMastery: 0.58, errorCount: 1 }
  ], errorDist: { CONCEPT_CONFUSION: 4, METHOD_MISSING: 3, STEP_OMISSION: 2, CALCULATION_ERROR: 1, BOUNDARY_ERROR: 1 }, activeRate: 0.5 }, timestamp: Date.now() }
  if (pathname === '/api/error/records') return { code: 0, data: errorPage, timestamp: Date.now() }
  if (/^\/api\/error\/records\/[^/]+$/.test(pathname)) {
    const logId = decodeURIComponent(pathname.split('/').pop())
    return { code: 0, data: errorPage.records.find((item) => item.logId === logId) ?? null, timestamp: Date.now() }
  }
  if (/^\/api\/diagnosis\/explain\/[^/]+$/.test(pathname)) {
    const explainId = decodeURIComponent(pathname.split('/').pop())
    return { code: 0, data: explainLogs[explainId] ?? null, timestamp: Date.now() }
  }
  if (pathname === '/api/plans') return { code: 0, data: plans, timestamp: Date.now() }
  if (pathname === '/api/plans/due-reviews') return { code: 0, data: dueReviewsMock, timestamp: Date.now() }
  if (pathname === '/api/recommendations') return { code: 0, data: recs, timestamp: Date.now() }
  if (pathname === '/api/teacher/reviews') return { code: 0, data: reviewsPage, timestamp: Date.now() }
  if (pathname === '/api/teacher/students') return { code: 0, data: teacherStudents, timestamp: Date.now() }
  if (/^\/api\/teacher\/students\/\d+\/learning-records$/.test(pathname)) return { code: 0, data: studentLearning, timestamp: Date.now() }
  if (pathname === '/api/questions') {
    const kpId = new URLSearchParams(search).get('kpId') || ''
    if (kpId === 'KP0001') {
      return { code: 0, data: [{ ...questionDetail, questionText: ocrStem }], timestamp: Date.now() }
    }
    return { code: 0, data: [
      { qId: `Q_MOCK_${kpId}_1`, primaryKpId: kpId, questionType: '单选', questionText: `【mock】${kpId} 基础题题干`, difficulty: 1 },
      { qId: `Q_MOCK_${kpId}_2`, primaryKpId: kpId, questionType: '单选', questionText: `【mock】${kpId} 进阶题题干`, difficulty: 2 }
    ], timestamp: Date.now() }
  }
  if (/^\/api\/questions\/[^/]+\/answer$/.test(pathname)) {
    const qId = decodeURIComponent(pathname.split('/').at(-2))
    return { code: 0, data: qId === 'Q_0302' ? nextAnswer : qId === 'Q_0303' ? thirdAnswer : answer, timestamp: Date.now() }
  }
  if (/^\/api\/questions\/[^/]+$/.test(pathname)) {
    const qId = decodeURIComponent(pathname.split('/').pop())
    return { code: 0, data: qId === 'Q_0302' ? nextQuestionDetail : qId === 'Q_0303' ? thirdQuestionDetail : questionDetail, timestamp: Date.now() }
  }
  return { code: 0, data: null, timestamp: Date.now() }
}

const STUDENT = [['today','/today'],['diagnosis','/diagnosis'],['error-book','/error-book'],['portrait','/portrait'],['plan','/plan'],['knowledge-graph','/knowledge-graph'],['practice','/practice'],['profile','/profile']]
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

function contrastRatio(foreground, background) {
  const channels = (value) => {
    const values = value.match(/[\d.]+/g).slice(0, 3).map(Number)
    return value.startsWith('color(') ? values.map((channel) => channel * 255) : values
  }
  const luminance = (value) => {
    const [r, g, b] = channels(value).map((channel) => {
      const normalized = channel / 255
      return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
    })
    return r * 0.2126 + g * 0.7152 + b * 0.0722
  }
  const a = luminance(foreground)
  const b = luminance(background)
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}

/** 实体主按钮必须同时通过默认与 hover 对比度；渐变无法证明最差端点，按失败处理 */
async function primaryButtonContrast(page) {
  const button = page.locator('.el-button--primary:not(.is-plain):not(.is-link):not(.is-text):visible').first()
  if (!await button.count()) return null
  const read = async () => button.evaluate((element) => {
    const style = getComputedStyle(element)
    return { foreground: style.color, background: style.backgroundColor, image: style.backgroundImage }
  })
  const ratio = (style) => style.image === 'none' ? contrastRatio(style.foreground, style.background) : 0
  const normalStyle = await read()
  const normal = ratio(normalStyle)
  await button.hover()
  await sleep(220)
  const hoverStyle = await read()
  const hover = ratio(hoverStyle)
  return { ratio: Math.min(normal, hover), normalStyle, hoverStyle }
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
      if (route.request().method() === 'POST' && u.pathname === '/api/error/records') {
        lastErrorRecordPayload = route.request().postDataJSON()
      }
      if (route.request().method() === 'GET' && u.pathname === '/api/questions') {
        await sleep(300)
      }
      if (route.request().method() === 'GET' && u.pathname === '/api/recommendations' && recommendationDelayMs) {
        await sleep(recommendationDelayMs)
      }
      if (route.request().method() === 'POST' && u.pathname === '/api/practices/start') {
        lastStartPayload = route.request().postDataJSON()
      }
      if (route.request().method() === 'POST' && u.pathname === '/api/practices') {
        lastPracticePayload = route.request().postDataJSON()
      }
      if (route.request().method() === 'POST' && /^\/api\/plans\/\d+\/checkin$/.test(u.pathname)) {
        const payload = route.request().postDataJSON()
        if (payload.answered) {
          for (const plan of plans) {
            const item = plan.items.find((entry) => entry.qId === payload.qId)
            if (item && !item.isDone) {
              item.isDone = true
              plan.doneCount += 1
              plan.status = plan.items.every((entry) => entry.isDone) ? 'DONE' : 'PENDING'
            }
          }
        }
      }
      if (route.request().method() === 'POST' && u.pathname === '/api/diagnosis/explain') {
        explainRequestCount += 1
        const body = [
          'event:meta\ndata:{"explainId":"EXPAUTO"}',
          'event:source\ndata:{"kpId":"KP0001","textbookPage":"P34","title":"概念"}',
          'event:delta\ndata:{"text":"本题讲解：先找连接条件。"}',
          'event:done\ndata:{"explainId":"EXPAUTO","latencyMs":10}',
          ''
        ].join('\n\n')
        await route.fulfill({ status: 200, contentType: 'text/event-stream', body })
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(reply(route.request().method(), u.pathname, role, u.search)) })
      }
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
      const violations = result.violations.filter(v => ['serious', 'critical'].includes(v.impact))
      if (violations.length === 0) return []
      const readiness = await page.evaluate(() => {
        const animations = document.getAnimations()
        return {
          documentReadyState: document.readyState,
          fontsReady: document.fonts.status === 'loaded',
          animationCount: animations.length,
          animationsFinished: animations.every(animation => animation.playState === 'finished'),
          skeletonEnded: !document.querySelector('.el-skeleton')
        }
      })
      const pageName = new URL(page.url()).pathname.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '') || 'root'
      const screenshot = `axe-${pageName}-${Date.now()}.png`
      await page.screenshot({ path: path.join(OUT, screenshot), fullPage: true })
      return violations.map(v => ({
        id: v.id,
        impact: v.impact,
        description: v.description,
        help: v.help,
        helpUrl: v.helpUrl,
        nodes: v.nodes.length,
        details: v.nodes.map(n => ({
          target: n.target,
          html: n.html,
          failureSummary: n.failureSummary,
          any: n.any,
          all: n.all,
          none: n.none
        })),
        evidence: { screenshot, readiness }
      }))
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

// 单例复现模式只用于诊断；正常 npm run e2e 的全量用例与断言不变。
if (process.env.TEACHER_AXE_ONLY === '1') {
  const targetA11y = []
  const teacherAxeCtx = await newContext(browser, 'TEACHER', 'glass', { width: 390, height: 844 })
  const teacherAxePage = await teacherAxeCtx.newPage()
  await open(teacherAxePage, '/teacher')
  await sleep(300)
  const violations = await axeScan(teacherAxePage)
  targetA11y.push({ theme: 'glass', page: 'mobile-teacher-overview', violations })
  check('a11y/mobile/teacher-overview', violations.length === 0, JSON.stringify(violations))
  fs.writeFileSync(path.join(OUT, 'e2e-report.json'), JSON.stringify({ base: BASE, results, a11y: targetA11y }, null, 2), 'utf8')
  const failed = results.filter(r => !r.pass)
  console.log(`E2E: ${results.length - failed.length}/${results.length} passed`)
  console.log(`a11y: ${targetA11y.filter(a => a.violations.length === 0).length}/${targetA11y.length} pages clean`)
  console.log('REPORT:', path.join(OUT, 'e2e-report.json'))
  await teacherAxeCtx.close()
  await browser.close()
  if (server) server.kill()
  process.exit(failed.length ? 1 : 0)
}

try {
  // ---------- 学生桌面 ----------
  const studentCtx = await newContext(browser, 'STUDENT', 'glass', { width: 1440, height: 900 })
  const page = await studentCtx.newPage()
  const runtimeLogs = []
  page.on('pageerror', (error) => runtimeLogs.push(`pageerror: ${error.message}`))
  page.on('framenavigated', (frame) => {
    if (frame === page.mainFrame()) runtimeLogs.push(`nav: ${frame.url()}`)
  })
  page.on('console', (message) => {
    if (message.type() === 'error') runtimeLogs.push(`console: ${message.text()}`)
  })

  // 首次进入懒加载诊断路由可能触发 Vite 整页 reload；先让路由与依赖就绪，再开始业务动线。
  await open(page, '/diagnosis')
  await open(page, '/today')
  await checkNext('今日：主结论存在', async () => {
    const titleCount = await page.locator('.hero-title').count()
    const reason = await page.locator('.due-card .due-reason').innerText().catch(() => '')
    const duePrimary = await page.locator('.due-card .el-button--primary').count()
    return titleCount === 1 && reason.includes('距上次答对 41 天，已过半衰期 + 概念混淆型错误') && duePrimary === 0
  })
  await checkNext('今日：一个主动作', async () => {
    const heroPrimary = await page.locator('.hero-actions .el-button--primary').count()
    const recommendationPrimary = await page.locator('.rec-card .el-button--primary').count()
    const visiblePrimary = await page.locator('.el-button--primary:visible').count()
    return heroPrimary === 1 && recommendationPrimary === 0 && visiblePrimary === 1
  })
  await page.locator('.rec-card').first().getByRole('button', { name: '开始练习' }).click()
  await page.locator('.practice-drawer.open').waitFor()
  await page.locator('.practice-drawer .el-radio').first().click()
  await page.getByRole('button', { name: '查看参考答案' }).click()
  await page.locator('.answer-panel').waitFor()
  recommendationDelayMs = 500
  await page.getByRole('button', { name: '提交本题（系统判分）' }).click()
  await sleep(300)
  const explainEntry = page.getByRole('button', { name: '查看本题讲解' })
  const resultVisible = await page.locator('.submission-result').isVisible().catch(() => false)
  const drawerStillOpen = await page.locator('.practice-drawer.open').isVisible().catch(() => false)
  check(
    '练习提交：原地保留结果与讲解入口',
    drawerStillOpen && resultVisible && await explainEntry.count() === 1,
    JSON.stringify({ drawerStillOpen, resultVisible, explainEntries: await explainEntry.count() })
  )
  const answerComparison = await page.locator('.answer-compare').innerText().catch(() => '')
  const reasonText = await page.locator('.result-reason').innerText().catch(() => '')
  const progressText = await page.locator('.result-progress').innerText().catch(() => '')
  check(
    '练习结果：展示答案对照与本次进步证据',
    answerComparison.includes('你的答案') && answerComparison.includes('正确答案')
      && !!reasonText && progressText.includes('本次用时 42 秒（服务端计时）'),
    JSON.stringify({ answerComparison, reasonText, progressText })
  )
  check(
    '练习提交：使用服务端计时令牌',
    lastStartPayload?.qId === 'Q_0301'
      && lastPracticePayload?.startToken === lastStartPayload?.clientToken
      && lastPracticePayload?.costSeconds === undefined,
    JSON.stringify({ lastStartPayload, lastPracticePayload })
  )
  const explainRequestsBeforeEntry = explainRequestCount
  const mapRequestsBeforeEntry = mapRequestCount
  let autoExplainOk = false
  let autoExplainDetail = '讲解入口不存在'
  if (await explainEntry.count()) {
    await page.evaluate(() => document.addEventListener('click', (event) => {
      window.__lastClickedButton = event.target.closest('button')?.innerText || event.target.textContent || ''
    }, true))
    await explainEntry.click()
    await page.waitForURL((url) => new URL(url).pathname === '/diagnosis', { timeout: 10000 }).catch(() => {})
    await page.locator('.explain-text').waitFor({ timeout: 10000 }).catch(() => {})
    const url = new URL(page.url())
    autoExplainDetail = JSON.stringify({ url: page.url(), explainRequestCount, mapRequestCount })
    autoExplainOk = url.searchParams.get('qId') === 'Q_0301'
      && url.searchParams.get('kpId') === 'KP0001'
      && url.searchParams.get('autoExplain') === '1'
      && explainRequestCount === explainRequestsBeforeEntry + 1
      && mapRequestCount === mapRequestsBeforeEntry
      && (await page.locator('.explain-text').innerText().catch(() => '')).includes('本题讲解')
  }
  if (!autoExplainOk) {
    const screenshot = 'auto-explain-failure.png'
    await page.screenshot({ path: path.join(OUT, screenshot), fullPage: true })
    autoExplainDetail = JSON.stringify({
      url: page.url(),
      explainRequestCount,
      mapRequestCount,
      runtimeLogs,
      screenshot,
      lastClickedButton: await page.evaluate(() => window.__lastClickedButton || ''),
      navigationType: await page.evaluate(() => performance.getEntriesByType('navigation')[0]?.type || ''),
      drawerText: await page.locator('.practice-drawer.open').innerText().catch(() => '')
    })
  }
  check('练习讲解：入口带题自动开始生成', autoExplainOk, autoExplainDetail)
  recommendationDelayMs = 0

  await page.locator('.recommend-card button.question').nth(1).waitFor()
  await page.locator('.recommend-card button.question').nth(1).click()
  await page.locator('.practice-drawer .el-radio').first().click()
  await page.getByRole('button', { name: '查看参考答案' }).click()
  await page.getByRole('button', { name: '提交本题（系统判分）' }).click()
  await page.locator('.submission-result').waitFor()
  const secondExplainRequests = explainRequestCount
  await page.getByRole('button', { name: '查看本题讲解' }).click()
  const secondAutoOk = await waitFor(async () => {
    const url = new URL(page.url())
    return url.searchParams.get('qId') === 'Q_0302'
      && explainRequestCount === secondExplainRequests + 1
      && (await page.locator('.explain-text').innerText().catch(() => '')).includes('本题讲解')
  }, 3000)
  check(
    '练习讲解：同页第二次入口按新题重新生成',
    secondAutoOk,
    JSON.stringify({ url: page.url(), before: secondExplainRequests, after: explainRequestCount })
  )

  await open(page, '/today')
  await page.locator('.rec-card').first().getByRole('button', { name: '开始练习' }).click()
  await page.locator('.practice-drawer .el-radio').first().click()
  await page.getByRole('button', { name: '查看参考答案' }).click()
  await page.getByRole('button', { name: '提交本题（系统判分）' }).click()
  await page.locator('.submission-result').waitFor()
  const nextButton = page.getByRole('button', { name: '下一题' })
  await waitFor(() => nextButton.isEnabled())
  await nextButton.click()
  await page.waitForFunction(() => document.querySelector('.practice-drawer .stem')?.textContent?.includes('题干 2'))
  check(
    '练习结果：下一题在原抽屉加载',
    await page.locator('.practice-drawer.open').isVisible()
      && (await page.locator('.practice-drawer .stem').innerText()).includes('题干 2')
      && await page.locator('.submission-result').count() === 0,
    await page.locator('.practice-drawer').innerText()
  )
  await page.keyboard.press('Escape')

  dueReviewsMock = []
  await open(page, '/today')
  await checkNext('今日：三张指标卡', async () => {
    const metricCount = await page.locator('.metric-card').count()
    const emptyText = await page.locator('.due-card .due-empty').innerText().catch(() => '')
    return metricCount === 3 && emptyText.includes('暂无到期复习')
  })
  dueReviewsMock = defaultDueReviews
  check('今日：无横向溢出', await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1))

  await open(page, '/error-book')
  await checkNext('错题本：两行记录', async () => {
    const rowCount = await page.locator('.el-table__row').count()
    const passText = await page.locator('.el-table__row').filter({ hasText: 'LOG001' }).locator('.review-status').innerText().catch(() => '')
    const returnText = await page.locator('.el-table__row').filter({ hasText: 'LOG002' }).locator('.review-status').innerText().catch(() => '')
    const pendingCount = await page.locator('.el-table__row').filter({ hasText: 'LOG003' }).locator('.review-status').count()
    return rowCount >= 2 && passText === '老师已复核这条讲解' && returnText === '需要补充：请补充连接条件的书写' && pendingCount === 0
  })
  let detailsOk = false
  const detailLinksReady = await page.locator('.error-record-link').count() >= 3
  if (detailLinksReady) {
    detailsOk = true
    for (const [logId, result, expected] of [['LOG001', 'PASS', '老师已复核这条讲解'], ['LOG002', 'RETURN', '需要补充：请补充连接条件的书写'], ['LOG003', null, '']]) {
      await page.locator('.el-table__row').filter({ hasText: logId }).locator('.error-record-link').click()
      await sleep(250)
      const drawer = page.locator('.error-record-drawer')
      const status = drawer.locator('.review-status')
      if (!await drawer.isVisible() || (result === null ? await status.count() !== 0 : (await status.innerText().catch(() => '')) !== expected)) {
        detailsOk = false
      }
      await page.keyboard.press('Escape')
      await sleep(200)
      if (!detailsOk) break
    }
  }
  await checkNext('错题本：缺 qId 显示待关联', async () => (await page.getByText('待关联题目').count()) >= 1 && detailsOk)

  await open(page, '/portrait')
  await checkNext('画像：同基线三条', async () => (await page.locator('.baseline-row').count()) === 3)
  await checkNext('画像：正确率标注估算', async () => (await page.locator('.baseline-name').nth(1).innerText()).includes('估算'))
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

  await page.locator('.day-card').nth(3).getByRole('button', { name: '去打卡' }).first().click()
  await page.locator('.practice-drawer .el-radio').first().click()
  await page.getByRole('button', { name: '查看参考答案' }).click()
  await page.getByRole('button', { name: '提交本题（系统判分）' }).click()
  await page.locator('.submission-result').waitFor()
  await page.getByRole('button', { name: '下一题' }).click()
  await page.waitForFunction(() => document.querySelector('.practice-drawer .stem')?.textContent?.includes('题干 3'))
  check(
    '计划打卡：下一题进入当天未完成项',
    await page.locator('.practice-drawer.open').isVisible()
      && (await page.locator('.practice-drawer .stem').innerText()).includes('题干 3'),
    await page.locator('.practice-drawer.open').innerText()
  )
  await page.locator('.practice-drawer .el-radio').first().click()
  await page.getByRole('button', { name: '查看参考答案' }).click()
  await page.getByRole('button', { name: '提交本题（系统判分）' }).click()
  await page.locator('.submission-result').waitFor()
  await page.getByRole('button', { name: '下一题' }).click()
  await sleep(300)
  check(
    '计划打卡：连续下一题不回跳已提交项',
    !await page.locator('.practice-drawer.open').isVisible().catch(() => false),
    await page.locator('.practice-drawer.open').innerText().catch(() => '已全部完成并关闭')
  )
  await page.keyboard.press('Escape'); await sleep(300)

  // 结果态占位文案回归：打卡判错时不得渲染“待接口返回”等内部术语（契约本就不回传错因）
  checkinIsCorrect = false
  await page.locator('.day-card').nth(4).getByRole('button', { name: '去打卡' }).first().click()
  await page.locator('.practice-drawer .el-radio').first().click()
  await page.getByRole('button', { name: '查看参考答案' }).click()
  await page.getByRole('button', { name: '提交本题（系统判分）' }).click()
  await page.locator('.submission-result').waitFor()
  const wrongResultText = await page.locator('.submission-result').innerText()
  check(
    '计划打卡：判错结果态无内部占位文案',
    !/待接口返回|待接入|TODO|占位/.test(wrongResultText) && wrongResultText.includes('错因分析详见错题本'),
    wrongResultText
  )
  await page.keyboard.press('Escape'); await sleep(300)
  checkinIsCorrect = true

  await open(page, '/knowledge-graph')
  await checkNext('图谱：缩放控件 3 个', async () => (await page.locator('.graph-controls button').count()) === 3)
  await checkNext('图谱：节点已渲染', async () => {
    const nodeCount = await page.locator('.graph-node').count()
    const readableCodes = await page.locator('.graph-node .node-code').evaluateAll((elements) =>
      elements.filter((element) => {
        const style = getComputedStyle(element)
        return style.display !== 'none' && style.visibility !== 'hidden' && !!element.textContent.trim()
      }).length
    )
    return nodeCount >= 1 && readableCodes === nodeCount
  })
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
  // 合成第二触点必须收尾；否则残留 pointerId 会污染后续节点点击
  await page.evaluate(({ x, y }) => {
    const svg = document.querySelector('.graph-svg')
    svg.dispatchEvent(new PointerEvent('pointerup', {
      bubbles: true, cancelable: true, pointerId: 99, pointerType: 'touch',
      isPrimary: false, button: 0, buttons: 0, clientX: x + 80, clientY: y + 80
    }))
  }, { x: px + 40, y: py + 40 })
  await page.mouse.up()
  await page.locator('.graph-controls button').last().click() // 恢复视图，避免影响后续节点点击
  await sleep(300)

  const firstNode = page.locator('.graph-node').first()
  await firstNode.hover()
  const hoverStroke = await firstNode.locator('.node-circle').evaluate((element) => parseFloat(getComputedStyle(element).strokeWidth))
  await firstNode.click()
  await checkNext('图谱：节点打开详情', async () => {
    const activeStroke = await firstNode.locator('.node-circle').evaluate((element) => parseFloat(getComputedStyle(element).strokeWidth))
    const selected = await firstNode.evaluate((element) => element.classList.contains('active'))
    return await page.locator('.kp-drawer').isVisible() && hoverStroke >= 3 && activeStroke >= 3 && selected
  })
  await checkNext('图谱：邻接边高亮', async () => (await page.locator('.graph-edge.active').count()) >= 1)
  await checkNext('图谱：邻接节点高亮', async () => (await page.locator('.graph-node.adjacent').count()) >= 1)
  await checkNext('图谱：非邻接变暗', async () => (await page.locator('.graph-node.dim').count()) >= 1)
  await page.keyboard.press('Escape'); await sleep(400)

  await open(page, '/knowledge-graph?kpId=KP0012')
  let practiceClicked = false
  await checkNext('图谱：kpId 深链打开详情', async () => {
    if (!practiceClicked) {
      if (!await page.locator('.kp-drawer').isVisible()) return false
      const practiceButton = page.locator('.kp-drawer').getByRole('button', { name: '开始练习' })
      if (!await practiceButton.count()) return false
      await practiceButton.click()
      practiceClicked = true
    }
    return await page.locator('.practice-drawer.open').isVisible()
  })
  await page.keyboard.press('Escape'); await sleep(400)

  // 回归：种子误区含字面 \n（如 seed-kp-ch05.sql 的 KP0501），显示层必须归一化为真实换行
  await open(page, '/knowledge-graph?kpId=KP0012')
  await waitFor(async () => await page.locator('.kp-drawer .kp-text.muted').isVisible())
  const misconceptionText = await page.locator('.kp-drawer .kp-text.muted').innerText().catch(() => '')
  check('图谱：误区字面换行符已归一化',
    misconceptionText.includes('把笛卡儿积当连接') && !misconceptionText.includes('\\n'),
    JSON.stringify(misconceptionText.slice(0, 160)))
  const misconceptionLines = misconceptionText.split('\n').map((line) => line.trim())
  check('图谱：误区分段多行可见',
    misconceptionLines.some((line) => line.includes('把笛卡儿积当连接')) && misconceptionLines.includes('第二段仍应可见'),
    JSON.stringify(misconceptionLines))
  await page.keyboard.press('Escape'); await sleep(400)

  // 回归：章节下拉与筛选说明必须显示教材章号（mock 中 chapterId 2→第1章、3→第2章）
  await open(page, '/knowledge-graph')
  const chapterSelect = page.locator('.el-select').filter({ has: page.locator('input[aria-label="按章节筛选知识点"]') })
  await chapterSelect.click(); await sleep(300)
  const chapterOptionTexts = await page.locator('.el-select-dropdown__item:visible').allTextContents()
  check('图谱：章节选项章号等于教材章号（两章）',
    chapterOptionTexts.length >= 2
      && chapterOptionTexts[0].startsWith('第 1 章 · 绪论')
      && chapterOptionTexts[1].startsWith('第 2 章 · 关系模型'),
    JSON.stringify(chapterOptionTexts))
  await page.locator('.el-select-dropdown__item:visible').first().click(); await sleep(400)
  check('图谱：筛选说明使用教材章号',
    (await page.locator('.graph-note').innerText()).includes('已筛选第 1 章'),
    (await page.locator('.graph-note').innerText()).slice(0, 120))

  // ---------- 找题练习 ----------
  await open(page, '/practice')
  await checkNext('找题：搜索命中知识点', async () => {
    await page.locator('.finder-input input').fill('连接')
    await sleep(400)
    const first = page.locator('.kp-result').first()
    return (await page.locator('.kp-result').count()) >= 1 && (await first.innerText()).includes('连接查询')
  })
  await checkNext('找题：结果含题量与难度', async () => {
    const text = await page.locator('.kp-result').first().innerText()
    return text.includes('题量 2') && text.includes('难度 1—2')
  })
  await checkNext('找题：点题打开练习抽屉', async () => {
    await page.locator('.kp-result').first().click()
    await sleep(300)
    if (!await page.locator('.question-row').count()) return false
    await page.locator('.question-row').first().click()
    await sleep(300)
    return await page.locator('.practice-drawer.open').isVisible()
  })
  await page.keyboard.press('Escape'); await sleep(400)
  await checkNext('找题：空结果给换词建议', async () => {
    await page.locator('.finder-input input').fill('不存在的知识点zzz')
    await sleep(400)
    return (await page.locator('.finder-empty').count()) === 1
      && (await page.locator('.finder-empty').innerText()).includes('换个词试试')
  })

  await open(page, '/profile')
  await checkNext('个人中心：快捷入口', async () => (await page.locator('.quick-links .el-button').count()) >= 3)

  await open(page, '/not-exist')
  await checkNext('404：未知路径兜底', async () => (await page.locator('.state-code').innerText()).trim() === '404')

  await open(page, '/403')
  await checkNext('403：无权访问页', async () => (await page.locator('.state-code').innerText()).trim() === '403')

  await open(page, '/diagnosis')
  await page.locator('textarea').first().fill('题干')
  await page.getByRole('button', { name: '匹配知识点' }).click()
  await page.getByRole('button', { name: '生成启发式讲解' }).click()
  await page.locator('.recommend-card .question').first().waitFor({ timeout: 10000 })
  const recommendationButton = page.locator('.recommend-card button.question').first()
  let recommendationOpened = false
  if (await recommendationButton.count()) {
    await recommendationButton.click()
    recommendationOpened = await page.locator('.practice-drawer.open').isVisible().catch(() => false)
      && (await page.locator('.practice-drawer .stem').innerText().catch(() => '')).includes('题干')
    await page.keyboard.press('Escape')
  }
  check(
    '诊断推荐：题目可进入对应练习',
    recommendationOpened,
    JSON.stringify({ clickableQuestions: await recommendationButton.count(), recommendationOpened })
  )

  for (const testCase of [
    { name: 'OCR：高置信识别自动匹配并生成讲解', confidence: 0.95, automatic: true },
    { name: 'OCR：低置信识别明确等待人工核对', confidence: 0.5, automatic: false }
  ]) {
    ocrConfidence = testCase.confidence
    await open(page, '/diagnosis')
    const mapBeforeOcr = mapRequestCount
    const explainBeforeOcr = explainRequestCount
    lastErrorRecordPayload = null
    await page.locator('.ocr-upload input[type="file"]').setInputFiles({
      name: 'question.png', mimeType: 'image/png', buffer: Buffer.from('mock-image')
    })
    await waitFor(async () => (await page.locator('textarea').first().inputValue()) === ocrStem)
    await sleep(300)
    const followUp = await page.locator('.ocr-follow-up').innerText().catch(() => '')
    const generated = (await page.locator('.explain-text').innerText().catch(() => '')).includes('本题讲解')
    const actual = {
      mapRequests: mapRequestCount - mapBeforeOcr,
      explainRequests: explainRequestCount - explainBeforeOcr,
      followUp,
      generated,
      matchDisabled: await page.getByRole('button', { name: '匹配知识点' }).isDisabled()
    }
    check(
      testCase.name,
      testCase.automatic
        ? actual.mapRequests === 1 && actual.explainRequests === 1 && actual.generated && /自动|生成/.test(followUp)
        : actual.mapRequests === 0 && actual.explainRequests === 0 && !actual.generated && actual.matchDisabled && /核对/.test(followUp),
      JSON.stringify(actual)
    )
    if (testCase.automatic) {
      check(
        'OCR：自动讲解保留关联题目',
        lastErrorRecordPayload?.qId === 'Q_0301',
        JSON.stringify(lastErrorRecordPayload)
      )
    }
  }
  ocrConfidence = 0.95
  let explainReviewOk = true
  for (const [explainId, result, expected] of [['EXP001', 'PASS', '老师已复核这条讲解'], ['EXP002', 'RETURN', '需要补充：请补充连接条件的书写'], ['EXP003', null, '']]) {
    await open(page, `/diagnosis?explainId=${explainId}`)
    const status = page.locator('.explain-detail .review-status')
    if (result === null ? await status.count() !== 0 : (await status.innerText().catch(() => '')) !== expected) {
      explainReviewOk = false
      break
    }
  }
  await checkNext('诊断台：图片上传入口', async () => (await page.locator('.ocr-upload').count()) === 1 && explainReviewOk)

  const verifyReviewAcrossOutlets = async ({ logId, explainId, result, expected }) => {
    await open(page, '/error-book')
    const row = page.locator('.el-table__row').filter({ hasText: logId })
    const listStatus = row.locator('.review-status')
    if (result === null ? await listStatus.count() !== 0 : (await listStatus.innerText().catch(() => '')) !== expected) return false
    await row.locator('.error-record-link').click()
    await sleep(250)
    const detailStatus = page.locator('.error-record-drawer .review-status')
    const detailOk = result === null ? await detailStatus.count() === 0 : (await detailStatus.innerText().catch(() => '')) === expected
    await page.keyboard.press('Escape')
    await sleep(200)
    if (!detailOk) return false
    await open(page, `/diagnosis?explainId=${explainId}`)
    const explainStatus = page.locator('.explain-detail .review-status')
    return result === null ? await explainStatus.count() === 0 : (await explainStatus.innerText().catch(() => '')) === expected
  }
  await checkNext('评阅回流：未评阅不渲染', async () => verifyReviewAcrossOutlets({ logId: 'LOG003', explainId: 'EXP003', result: null, expected: '' }))
  await checkNext('评阅回流：PASS 三处一致', async () => verifyReviewAcrossOutlets({ logId: 'LOG001', explainId: 'EXP001', result: 'PASS', expected: '老师已复核这条讲解' }))
  await checkNext('评阅回流：RETURN 三处一致', async () => verifyReviewAcrossOutlets({ logId: 'LOG002', explainId: 'EXP002', result: 'RETURN', expected: '需要补充：请补充连接条件的书写' }))

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
  await tpage.locator('.error-row').nth(4).waitFor({ timeout: 10000 })
  const teacherOverviewLayout = await tpage.evaluate(() => {
    const rows = Array.from(document.querySelectorAll('.error-row'))
    const last = rows.at(-1)?.getBoundingClientRect()
    return {
      statCards: document.querySelectorAll('.stat-card').length,
      errorRows: rows.length,
      allErrorRowsVisible: rows.every((row) => {
        const style = getComputedStyle(row)
        return style.display !== 'none' && style.visibility !== 'hidden' && row.getBoundingClientRect().height > 0
      }),
      lastErrorBottom: last ? Math.ceil(last.bottom) : null,
      viewportHeight: window.innerHeight
    }
  })
  check(
    '教学概览：900px 保留四卡与全部错因',
    teacherOverviewLayout.statCards === 4
      && teacherOverviewLayout.errorRows === 5
      && teacherOverviewLayout.allErrorRowsVisible
      && teacherOverviewLayout.lastErrorBottom !== null
      && teacherOverviewLayout.lastErrorBottom <= 900,
    JSON.stringify(teacherOverviewLayout)
  )

  // 1080p 守卫：答辩投影即 1920x1080，该高度不得命中压缩媒体查询（当前阈值 max-height:900px）。
  // 断言用计算样式数值（基础间距 20/16/16/10，压缩后会变 12/8/8/6），不用像素截图，
  // 避免 CI runner 字体/系统渲染差异造成抖动；与上方 900px 检查互为两侧守卫。
  const guardCtx = await newContext(browser, 'TEACHER', 'glass', { width: 1920, height: 1080 })
  const guardPage = await guardCtx.newPage()
  await open(guardPage, '/teacher')
  await waitFor(async () => (await guardPage.locator('.stat-card').count()) >= 4)
  await guardPage.locator('.error-row').nth(4).waitFor({ timeout: 10000 }).catch(() => {})
  const guardLayout = await guardPage.evaluate(() => {
    const px = (selector, property) => {
      const element = document.querySelector(selector)
      return element ? parseFloat(getComputedStyle(element)[property]) : null
    }
    return {
      viewportHeight: window.innerHeight,
      statRowMarginTop: px('.stat-row', 'marginTop'),
      statRowPaddingTop: px('.stat-row', 'paddingTop'),
      pageHeadMarginBottom: px('.page-head', 'marginBottom'),
      errorBarsGap: px('.error-bars', 'rowGap')
    }
  })
  check(
    '教学概览：1080p 保持未压缩间距',
    guardLayout.viewportHeight === 1080
      && guardLayout.statRowMarginTop === 20
      && guardLayout.statRowPaddingTop === 16
      && guardLayout.pageHeadMarginBottom === 16
      && guardLayout.errorBarsGap === 10,
    JSON.stringify(guardLayout)
  )
  await guardCtx.close()

  await open(tpage, '/teacher/reviews')
  await checkNext('评阅：队列已渲染', async () => (await tpage.locator('.el-table__row').count()) >= 2)
  await tpage.getByRole('button', { name: '评阅' }).nth(1).click()
  await checkNext('评阅：无证据提示', async () => (await tpage.getByText('证据不完整').count()) >= 1)
  check('评阅：无证据禁用通过', await tpage.locator('.el-radio-button').first().evaluate(el => el.className.includes('is-disabled')))
  check('评阅：默认退回', await tpage.locator('.el-radio-button').nth(1).evaluate(el => el.className.includes('is-active')))
  const reviewBox = await tpage.locator('.review-drawer').boundingBox()
  check('评阅：抽屉铺满视口', !!reviewBox && reviewBox.height > 800, JSON.stringify(reviewBox))
  await tpage.keyboard.press('Escape'); await sleep(500)

  await tpage.locator('.el-table__row').first().getByRole('button', { name: '评阅' }).click()
  await checkNext('评阅：待评阅可填写评语', async () => {
    const textarea = tpage.locator('.review-drawer textarea')
    return await textarea.inputValue() === '' && await textarea.isEditable()
  })
  await tpage.keyboard.press('Escape'); await sleep(300)
  await tpage.locator('.status-select').click()
  await tpage.locator('.el-select-dropdown__item:visible').filter({ hasText: '已评阅' }).click()
  await sleep(300)
  await tpage.locator('.el-table__row').filter({ hasText: '王同学' }).getByRole('button', { name: '评阅' }).click()
  await checkNext('评阅：已通过显示教师评语', async () => {
    const textarea = tpage.locator('.review-drawer textarea')
    return await textarea.inputValue() === '讲解准确，可继续练习' && !await textarea.isEditable()
  })
  await tpage.keyboard.press('Escape'); await sleep(300)
  await tpage.locator('.el-table__row').filter({ hasText: '赵同学' }).getByRole('button', { name: '评阅' }).click()
  await checkNext('评阅：已退回显示教师意见', async () => {
    const textarea = tpage.locator('.review-drawer textarea')
    return await textarea.inputValue() === '请补充连接条件的书写' && !await textarea.isEditable()
  })
  await tpage.keyboard.press('Escape'); await sleep(300)

  await open(tpage, '/teacher/class')
  await checkNext('班级：两名学生', async () => (await tpage.locator('.el-table__row').count()) >= 2)
  await checkNext('班级：正确率取后端字段', async () => (await tpage.locator('.el-table').innerText()).includes('55%'))
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
    practice: '.finder-card',
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
  const originalStem = questionDetail.questionText
  const originalOptions = questionDetail.options
  questionDetail.questionText = '请根据给定的学生表、课程表与选课表，分析连接条件并选择能够保留全部学生信息的查询结果。'.repeat(8)
  questionDetail.options = originalOptions.map((option) => ({ ...option, text: `${option.text}：需要同时说明连接类型、连接条件以及空值保留范围。`.repeat(3) }))
  await mp.setViewportSize({ width: 360, height: 740 })
  await open(mp, '/today')
  await mp.locator('.rec-card').first().getByRole('button', { name: '开始练习' }).click()
  await mp.locator('.practice-drawer .el-radio').first().click()
  await mp.getByRole('button', { name: '查看参考答案' }).click()
  await mp.locator('.answer-panel').waitFor()
  const mobileSubmit = mp.getByRole('button', { name: '提交本题（系统判分）' })
  const mobileSubmitBox = await mobileSubmit.boundingBox()
  check(
    '移动端/练习：360x740 提交按钮首屏可见',
    !!mobileSubmitBox && mobileSubmitBox.y >= 0 && mobileSubmitBox.y + mobileSubmitBox.height <= 740,
    JSON.stringify(mobileSubmitBox)
  )
  await mp.keyboard.press('Escape')
  questionDetail.questionText = originalStem
  questionDetail.options = originalOptions
  await mp.setViewportSize({ width: 390, height: 844 })
  await open(mp, '/knowledge-graph')
  await open(mp, '/knowledge-graph?kpId=KP0012')
  await checkNext('移动端/图谱：换行与主动作可见', async () => {
    const drawerText = await mp.locator('.kp-drawer').innerText()
    const buttonBox = await mp.locator('.kp-drawer .kp-practice').boundingBox()
    return !drawerText.includes('\\n') && !!buttonBox && buttonBox.y >= 0 && buttonBox.y + buttonBox.height <= 844
  })
  await mp.keyboard.press('Escape'); await sleep(300)
  await open(mp, '/portrait')
  await checkNext('移动端/画像：薄弱点名称完整换行', async () =>
    mp.locator('.weak-name').evaluateAll((elements) => elements.every((element) => getComputedStyle(element).whiteSpace === 'normal'))
  )
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
      const buttonContrast = theme === 'glass-dark' ? await primaryButtonContrast(p) : null
      a11y.push({ theme, page: name, violations: v, buttonContrast })
      check(`a11y/${theme}/${name}`, v.length === 0 && (buttonContrast === null || buttonContrast.ratio >= 4.5), JSON.stringify({ violations: v, buttonContrast }))
    }
    await ctx.close()
    const tctx = await newContext(browser, 'TEACHER', theme, { width: 1440, height: 900 })
    const tp2 = await tctx.newPage()
    for (const [name, pathname] of TEACHER) {
      await open(tp2, pathname)
      await sleep(300)
      const v = await axeScan(tp2)
      const buttonContrast = theme === 'glass-dark' ? await primaryButtonContrast(tp2) : null
      a11y.push({ theme, page: name, violations: v, buttonContrast })
      check(`a11y/${theme}/${name}`, v.length === 0 && (buttonContrast === null || buttonContrast.ratio >= 4.5), JSON.stringify({ violations: v, buttonContrast }))
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
