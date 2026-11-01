/**
 * 真实后端 API 冒烟（本地/测试环境，不 mock）
 *
 * 前置：
 *   - 后端 http://127.0.0.1:8080 已启动，MySQL/Redis 可用
 *   - 已有课程/知识点/题库种子（见 zhixue-server/sql/seed-*.sql）
 *   - 账号口令通过环境变量传入，禁止写死
 *
 * 用法（PowerShell）：
 *   $env:E2E_USER='student01'; $env:E2E_PASS='123456'; npm run e2e:real
 *
 * 注意：会写入 1 条错题、1 周计划、1 次打卡；仅用于本地/测试库。
 */
import fs from 'node:fs'
import path from 'node:path'

const BASE = process.env.E2E_API || 'http://127.0.0.1:8080'
const USER = process.env.E2E_USER
const PASS = process.env.E2E_PASS
const TEACHER_USER = process.env.E2E_TEACHER_USER || USER
const TEACHER_PASS = process.env.E2E_TEACHER_PASS || PASS
const OUT =
  process.env.E2E_REAL_OUT ||
  path.resolve('../../spark-output/qa/2026-11-01-real/api-smoke-report.json')
fs.mkdirSync(path.dirname(OUT), { recursive: true })

if (!USER || !PASS) {
  console.error('缺少 E2E_USER / E2E_PASS；请先设置种子账号口令。')
  process.exit(2)
}

const results = []
const pass = (name, detail = '') => { results.push({ name, pass: true, detail }); console.log('PASS', name, detail) }
const fail = (name, detail = '') => { results.push({ name, pass: false, detail }); console.log('FAIL', name, detail) }

async function call(method, pathname, token, body, timeout = 15000) {
  const res = await fetch(BASE + pathname, {
    method,
    headers: {
      Accept: pathname.includes('/diagnosis/explain') ? 'text/event-stream' : 'application/json',
      'Content-Type': 'application/json',
      ...(token ? { satoken: token } : {})
    },
    body: body == null ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(timeout)
  })
  const text = await res.text()
  let json
  try { json = JSON.parse(text) } catch { json = null }
  return { status: res.status, json, text, headers: res.headers }
}

const okData = (label, r) => {
  if (r.json?.code !== 0) {
    fail(label, `http=${r.status} code=${r.json?.code} msg=${r.json?.message}`)
    return null
  }
  pass(label)
  return r.json.data
}

/** 解析 SSE：返回 [{event, data}]，要求 done 事件出现 */
function parseSse(text) {
  return text
    .split(/\r?\n\r?\n/)
    .map((block) => {
      const lines = block.split(/\r?\n/)
      const event = lines.find((l) => l.startsWith('event:'))?.slice(6).trim()
      const data = lines.filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trim()).join('\n')
      return event ? { event, data } : null
    })
    .filter(Boolean)
}

const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const today = new Date()
const monday = new Date(today)
monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7))
const weekStart = fmt(monday)
const weekEnd = fmt(new Date(monday.getTime() + 6 * 86400000))

const anon = await call('GET', '/api/auth/me')
if (anon.json?.code === 40100) pass('未登录 /auth/me 返回 40100')
else fail('未登录 /auth/me 返回 40100', `http=${anon.status} code=${anon.json?.code}`)

const login = await call('POST', '/api/auth/login', null, { username: USER, password: PASS })
const studentToken = okData('登录学生', login)?.token
const teacherLogin = await call('POST', '/api/auth/login', null, { username: TEACHER_USER, password: TEACHER_PASS })
const teacherToken = okData('登录教师', teacherLogin)?.token

const me = okData('GET /auth/me（学生）', await call('GET', '/api/auth/me', studentToken))
if (me?.role === 'STUDENT') pass('学生角色为 STUDENT'); else fail('学生角色为 STUDENT', JSON.stringify(me))

const courses = okData('GET /courses', await call('GET', '/api/courses', studentToken))
const courseId = courses?.[0]?.courseId ?? 1
if (courses?.length) pass('课程列表非空', `courseId=${courseId}`); else fail('课程列表非空')

const graph = okData('GET /knowledge-graph', await call('GET', `/api/courses/${courseId}/knowledge-graph`, studentToken))
if ((graph?.nodes?.length ?? 0) >= 20) pass('图谱节点 >=20', `nodes=${graph.nodes.length}, edges=${graph.edges?.length}`)
else fail('图谱节点 >=20', `nodes=${graph?.nodes?.length}`)

const kpId = graph?.nodes?.[0]?.id
const kp = okData('GET /course/kp/{kpId}', await call('GET', `/api/course/kp/${kpId}`, studentToken))
if (kp?.kpId === kpId) pass('知识点详情一致'); else fail('知识点详情一致')

const mapText = '把主键和外键搞混了，参照完整性也不清楚'
const mapped = okData('POST /diagnosis/map', await call('POST', '/api/diagnosis/map', studentToken, { text: mapText, courseId }))
if (mapped?.decision) pass('映射返回 decision', `decision=${mapped.decision}, candidates=${mapped.candidates?.length ?? 0}`)
else fail('映射返回 decision')

const qId = 'Q_0201'
const q = okData('GET /questions/{qId}', await call('GET', `/api/questions/${qId}`, studentToken))
if (q?.qId === qId) pass('题目详情可读', `type=${q.questionType}`); else fail('题目详情可读')

const clientToken = 'real-e2e-' + Date.now()
const created = okData('POST /error/records（DRAFT）', await call('POST', '/api/error/records', studentToken, {
  courseId, clientToken, qId, kpId: q?.kpId ?? kpId, errorType: 'CONCEPT_CONFUSION', correctedText: q?.questionText ?? mapText
}))
const logId = created?.logId
if (logId) pass('错题记录落库', `logId=${logId}`); else fail('错题记录落库')

if (logId) {
  const confirmed = okData('PUT /error/records/{logId}/confirm', await call('PUT', `/api/error/records/${logId}/confirm`, studentToken, {
    kpId: q?.kpId ?? kpId, errorType: 'CONCEPT_CONFUSION', correctedText: q?.questionText ?? mapText, studentNote: '真实联调自动记录'
  }))
  if (confirmed?.status === 'CONFIRMED') pass('错题状态 CONFIRMED'); else fail('错题状态 CONFIRMED')
}

// SSE 讲解：AI 服务离线时应出现 degraded，但仍必须有 done
if (logId) {
  const explain = await call('POST', '/api/diagnosis/explain', studentToken, {
    logId, kpId: q?.kpId ?? kpId, errorType: 'CONCEPT_CONFUSION', questionText: q?.questionText ?? mapText, forceRefresh: false
  }, 20000)
  const events = parseSse(explain.text)
  const names = events.map((e) => e.event)
  if (explain.status === 200 && names.includes('meta') && names.includes('delta') && names.includes('done')) {
    pass('POST /diagnosis/explain（SSE）', `events=${names.join(',')} degraded=${names.includes('degraded')}`)
  } else {
    fail('POST /diagnosis/explain（SSE）', `http=${explain.status} events=${names.join(',')} text=${explain.text.slice(0, 120)}`)
  }
}

const recs = okData('GET /recommendations', await call('GET', `/api/recommendations?logId=${logId ?? ''}&courseId=${courseId}`, studentToken))
if (Array.isArray(recs)) pass('推荐返回数组', `count=${recs.length}`); else fail('推荐返回数组')

const plans = okData('POST /plans/generate', await call('POST', '/api/plans/generate', studentToken, {
  courseId, weekStart, dailyQuestionCount: 3
}))
if (Array.isArray(plans) && plans.length === 7) pass('生成 7 天计划', `items=${plans.reduce((s, p) => s + (p.items?.length ?? 0), 0)}`)
else fail('生成 7 天计划')

const planList = okData('GET /plans', await call('GET', `/api/plans?from=${weekStart}&to=${weekEnd}`, studentToken))
if (Array.isArray(planList)) pass('计划列表可读', `days=${planList.length}`); else fail('计划列表可读')

const pending = (plans ?? []).flatMap((p) => (p.items ?? []).filter((i) => !i.isDone).map((i) => ({ ...i, planId: p.planId })))[0]
if (pending) {
  const checkin = await call('POST', `/api/plans/${pending.planId}/checkin`, studentToken, {
    qId: pending.qId, clientToken: 'checkin-' + Date.now(), answered: true,
    myAnswer: 'A', isCorrect: true, costSeconds: 42, errorType: null, note: '真实联调打卡'
  })
  if (checkin.json?.code === 0) pass('POST /plans/{planId}/checkin', `planId=${pending.planId} qId=${pending.qId}`)
  else fail('POST /plans/{planId}/checkin', `code=${checkin.json?.code} msg=${checkin.json?.message}`)
} else {
  fail('找到待打卡计划题', '无 pending item')
}

const portrait = await call('GET', '/api/portraits/me', studentToken)
if (portrait.json?.code === 0) pass('GET /portraits/me', `totalPractice=${portrait.json?.data?.totalPractice ?? 'null'}`)
else fail('GET /portraits/me', `code=${portrait.json?.code} msg=${portrait.json?.message}`)

const classPortrait = await call('GET', `/api/portraits/class/${courseId}`, teacherToken)
if (classPortrait.json?.code === 0) pass('GET /portraits/class/{courseId}', `students=${classPortrait.json?.data?.studentCount ?? 'null'}`)
else fail('GET /portraits/class/{courseId}', `code=${classPortrait.json?.code} msg=${classPortrait.json?.message}`)

const students = okData('GET /teacher/students', await call('GET', `/api/teacher/students?courseId=${courseId}&page=1&size=20`, teacherToken))
const studentId = students?.records?.[0]?.userId
if (studentId) pass('教师学生列表非空', `total=${students.total}`); else fail('教师学生列表非空')
if (studentId) {
  const detail = await call('GET', `/api/teacher/students/${studentId}/learning-records?courseId=${courseId}`, teacherToken)
  if (detail.json?.code === 0) pass('GET /teacher/students/{id}/learning-records', `practices=${detail.json?.data?.practices?.total ?? 0}`)
  else fail('GET /teacher/students/{id}/learning-records', `code=${detail.json?.code} msg=${detail.json?.message}`)
}

const reviews = await call('GET', '/api/teacher/reviews?status=PENDING&page=1&size=10', teacherToken)
if (reviews.json?.code === 0) pass('GET /teacher/reviews', `total=${reviews.json?.data?.total ?? 0}`)
else fail('GET /teacher/reviews', `code=${reviews.json?.code} msg=${reviews.json?.message}`)

const health = await call('GET', '/api/system/health')
if (health.json?.data?.db === 'UP') pass('health db=UP', `ai=${health.json?.data?.ai}`)
else fail('health db=UP', JSON.stringify(health.json)?.slice(0, 200))

fs.writeFileSync(OUT, JSON.stringify({ base: BASE, weekStart, weekEnd, results }, null, 2))
const failed = results.filter((r) => !r.pass)
console.log(`\nREAL API: ${results.length - failed.length}/${results.length} passed`)
console.log('REPORT:', OUT)
if (failed.length) process.exitCode = 1