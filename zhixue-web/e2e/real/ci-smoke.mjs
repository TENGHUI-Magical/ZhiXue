/**
 * 真实链路冒烟门禁（CI job e2e-real；本地同脚本）。
 *
 * 设计：窄而硬。真实 MySQL + Redis + 真实 Spring Boot 后端 + Vite 前端 + 真实浏览器，
 * 不启动任何 mock server；只覆盖 5 个高价值场景，不做“148 条全量改真后端”。
 * 端口约定唯一来源 = vite.config.ts（前端 5173，/api 代理到后端 8080），本脚本不另造一套。
 *
 * 场景：
 *   1. 学生 UI 登录 → /today → /practice 作答提交（真写 practice_record，DB 校验落行）
 *   2. 画像/诊断：真实掌握度与薄弱点字段非空（学生 /api/portraits/me + 教师班级画像）
 *   3. 教师 UI 登录 → 评阅队列 → PASS 评语 → 学生端 /api/diagnosis/explain 可见
 *   4. 越权：teacher02（无课程教师）读他人讲解 → HTTP 200 + code 40300
 *   5. 评阅留痕 CSV：真实导出，校验表头/行数/不含讲解正文
 *
 * 初始化：只用仓库现成 SQL（init-v2.sql + seed-demo-all.sql），仅把目标库名替换为
 * E2E_DB_NAME；teacher02 为派生 fixture（口令哈希取自 seed-demo-users.sql 的 teacher01），
 * 因为种子库只有 teacher01，40300 场景必须有第二个教师。
 *
 * 环境变量：
 *   E2E_DB_NAME             目标库（默认 zhixue_e2e_smoke，必须以 zhixue_e2e 开头，防止误删）
 *   MYSQL_HOST/PORT/USER/PASSWORD   本机 MySQL；CI 用 MYSQL_DOCKER_CONTAINER 走容器内客户端
 *   MYSQL_DOCKER_CONTAINER  GitHub Actions services.mysql 容器 ID（设置后用 docker exec mysql）
 *   MYSQL_BIN               本机 mysql 客户端路径（默认 mysql）
 *   JAVA_BIN / JAVA_HOME    后端 JVM（默认 java，或 JAVA_HOME/bin/java）
 *   E2E_CHANNEL             浏览器通道（默认 msedge；CI ubuntu 自带 Edge）
 *   E2E_REAL_OUT            报告目录（默认 e2e-real-artifacts）
 */
import fs from 'node:fs'
import path from 'node:path'
import net from 'node:net'
import { spawn, spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const WEB_DIR = path.resolve(HERE, '../..')
const REPO_DIR = path.resolve(WEB_DIR, '..')
const SERVER_DIR = path.join(REPO_DIR, 'zhixue-server')
const SQL_DIR = path.join(SERVER_DIR, 'sql')

const BACKEND = 'http://127.0.0.1:8080'
const WEB_BASE = 'http://127.0.0.1:5173'
const BACKEND_PORT = 8080
const WEB_PORT = 5173

const DB_NAME = process.env.E2E_DB_NAME || 'zhixue_e2e_smoke'
const DB_HOST = process.env.MYSQL_HOST || '127.0.0.1'
const DB_PORT = process.env.MYSQL_PORT || '3306'
const DB_USER = process.env.MYSQL_USER || 'root'
const DB_PASS = process.env.MYSQL_PASSWORD || process.env.DB_PASSWORD || 'change-me'
const MYSQL_CONTAINER = process.env.MYSQL_DOCKER_CONTAINER || ''
const OUT = path.resolve(process.env.E2E_REAL_OUT || path.join(WEB_DIR, 'e2e-real-artifacts'))

/** 种子账号口令，来源：zhixue-server/sql/seed-demo-users.sql 的 @demo_password */
const DEMO_PASSWORD = 'Demo@2026'
const STUDENT = 'student01'
const TEACHER = 'teacher01'
const TEACHER2 = 'teacher02'
const TARGET_EXPLAIN = 'DEMO_EX_EL_DEMO_2_1'
const SEARCH_KP = 'KP0201'

const results = []
const consoleErrors = []
const serverErrors = []
const runId = new Date().toISOString().replace(/[-:T.Z]/g, '').slice(0, 14)
const COMMENT = `E2E真实冒烟评阅 PASS ${runId}`

fs.mkdirSync(OUT, { recursive: true })
const runLog = path.join(OUT, 'run.log')
fs.writeFileSync(runLog, '')

function log(line = '') {
  console.log(line)
  fs.appendFileSync(runLog, line + '\n')
}

function pass(name, detail = '') {
  results.push({ name, pass: true, detail })
  log(`PASS  ${name}${detail ? ' · ' + detail : ''}`)
}

function fail(name, detail = '') {
  results.push({ name, pass: false, detail })
  log(`FAIL  ${name}${detail ? ' · ' + detail : ''}`)
}

function assert(name, cond, detail = '') {
  if (cond) pass(name, detail)
  else fail(name, detail)
  return !!cond
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

/** 端口占用检测：起服务前先失败，避免复用到别人的进程而“假绿”。 */
function portInUse(port) {
  return new Promise((resolve) => {
    const socket = net.connect({ host: '127.0.0.1', port })
    socket.once('connect', () => { socket.destroy(); resolve(true) })
    socket.once('error', () => resolve(false))
    socket.setTimeout(1500, () => { socket.destroy(); resolve(false) })
  })
}

// ---------------- MySQL CLI（本机直连 / CI 容器内） ----------------

function mysqlCommand(extraArgs) {
  const common = [`-u${DB_USER}`, `-p${DB_PASS}`, '-N', '-B', '--default-character-set=utf8mb4', ...extraArgs]
  if (MYSQL_CONTAINER) {
    return ['docker', ['exec', '-i', MYSQL_CONTAINER, 'mysql', ...common]]
  }
  return [process.env.MYSQL_BIN || 'mysql', ['-h', DB_HOST, '-P', DB_PORT, ...common]]
}

function mysqlRun(mode, payload, label) {
  return new Promise((resolve, reject) => {
    const [cmd, args] = mysqlCommand(mode === 'stdin' ? [] : ['-e', payload])
    const child = spawn(cmd, args, { stdio: ['pipe', 'pipe', 'pipe'] })
    let out = ''
    let err = ''
    child.stdout.on('data', (d) => { out += d })
    child.stderr.on('data', (d) => { err += d })
    const timer = setTimeout(() => {
      child.kill()
      reject(new Error(`${label} 超时（>180s）`))
    }, 180000)
    child.on('error', (e) => { clearTimeout(timer); reject(new Error(`${label} 启动失败: ${e.message}`)) })
    child.on('close', (code) => {
      clearTimeout(timer)
      // mysql 对 -p 明文口令会在 stderr 打印 warning，不当失败
      const realErr = err.split(/\r?\n/).filter((l) => l && !l.includes('Using a password on the command line')).join('\n')
      if (code !== 0) reject(new Error(`${label} 失败(code=${code}): ${realErr || out}`))
      else resolve(out)
    })
    if (mode === 'stdin') child.stdin.end(payload)
  })
}

const sqlQuery = (sql) => mysqlRun('exec', sql, 'SQL').then((s) => s.trim())
const sqlExec = async (sql) => { await mysqlRun('exec', sql, 'SQL') }

/** 应用仓库 SQL：只替换目标库名（CREATE DATABASE / USE），其余内容不动。 */
async function applyRepoSql(fileName) {
  const full = path.join(SQL_DIR, fileName)
  if (!fs.existsSync(full)) throw new Error(`缺少 SQL 文件: ${full}`)
  const raw = fs.readFileSync(full, 'utf8')
  const transformed = raw
    .replace(/CREATE DATABASE IF NOT EXISTS zhixue/g, `CREATE DATABASE IF NOT EXISTS ${DB_NAME}`)
    .replace(/^USE zhixue;/gm, `USE ${DB_NAME};`)
  const started = Date.now()
  await mysqlRun('stdin', transformed, `导入 ${fileName}`)
  log(`  SQL ${fileName} → ${DB_NAME}（${Math.round((Date.now() - started) / 1000)}s，${raw.length} 字节）`)
}

// ---------------- 进程编排 ----------------

let backendProc = null
let frontendProc = null
let browser = null
let cleaned = false

function javaBin() {
  if (process.env.JAVA_BIN) return process.env.JAVA_BIN
  if (process.env.JAVA_HOME) {
    const exe = process.platform === 'win32' ? 'java.exe' : 'java'
    const candidate = path.join(process.env.JAVA_HOME, 'bin', exe)
    if (fs.existsSync(candidate)) return candidate
  }
  return 'java'
}

function findBootJar() {
  const target = path.join(SERVER_DIR, 'zhixue-bootstrap', 'target')
  if (!fs.existsSync(target)) throw new Error(`后端未打包：${target} 不存在，请先 mvn -DskipTests package`)
  const jars = fs.readdirSync(target)
    .filter((f) => f.endsWith('.jar') && !f.endsWith('-sources.jar') && !f.endsWith('-javadoc.jar'))
    .sort()
  if (!jars.length) throw new Error(`未找到可执行 jar：${target}`)
  return path.join(target, jars[jars.length - 1])
}

function trackProcess(child, name) {
  child.stdout.on('data', (d) => fs.appendFileSync(path.join(OUT, `${name}.log`), d))
  child.stderr.on('data', (d) => {
    fs.appendFileSync(path.join(OUT, `${name}.log`), d)
    const text = String(d)
    if (/ERROR|Exception|BUILD FAILURE/.test(text)) serverErrors.push(`${name}: ${text.slice(0, 300)}`)
  })
  return child
}

function killTree(child) {
  if (!child || child.exitCode !== null) return
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/T', '/F', '/PID', String(child.pid)], { stdio: 'ignore' })
  } else {
    try { process.kill(-child.pid, 'SIGKILL') } catch { try { child.kill('SIGKILL') } catch { /* noop */ } }
  }
}

async function cleanup() {
  if (cleaned) return
  cleaned = true
  log('\n--- 清理进程 ---')
  // 浏览器先关，避免残留 Edge/Chrome 拖住 job；关不掉也不阻塞退出（有超时兜底）
  const b = browser
  browser = null
  if (b) await Promise.race([b.close().catch(() => {}), sleep(10000)])
  killTree(frontendProc)
  killTree(backendProc)
  // 等端口释放，避免下一次运行复用到悬挂进程；未释放就是门禁失败，不吞
  let free = false
  for (let i = 0; i < 20; i++) {
    const busy = (await portInUse(BACKEND_PORT)) || (await portInUse(WEB_PORT))
    if (!busy) { free = true; break }
    await sleep(250)
  }
  if (free) log(`端口 ${BACKEND_PORT}/${WEB_PORT} 已释放`)
  else fail('清理：端口未释放', `端口 ${BACKEND_PORT}/${WEB_PORT} 仍被占用，可能是残留进程`)
}

process.on('SIGINT', async () => { await cleanup(); process.exit(130) })
process.on('SIGTERM', async () => { await cleanup(); process.exit(143) })

async function waitFor(name, url, timeoutMs, check) {
  const deadline = Date.now() + timeoutMs
  let last = ''
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(5000) })
      const body = await res.text()
      if (check(res, body)) return body
      last = `HTTP ${res.status}: ${body.slice(0, 200)}`
    } catch (e) {
      last = e.message
    }
    await sleep(500)
  }
  throw new Error(`${name} 等待超时（${Math.round(timeoutMs / 1000)}s），最后状态：${last}`)
}

async function waitBackendHealthy() {
  let health = null
  await waitFor('后端健康检查', `${BACKEND}/api/system/health`, 180000, (res, body) => {
    if (res.status !== 200) return false
    try {
      const json = JSON.parse(body)
      if (json.code !== 0 || !json.data) return false
      health = json.data
      // db/redis 必须 UP；AI 未启动时整体 DEGRADED 属预期（本门禁不覆盖 AI 模型服务）
      return json.data.db === 'UP' && json.data.redis === 'UP'
    } catch {
      return false
    }
  })
  log(`  健康检查通过：status=${health.status} db=${health.db} redis=${health.redis} ai=${health.ai}`)
  return health
}

async function startServers() {
  if (await portInUse(BACKEND_PORT)) throw new Error(`端口 ${BACKEND_PORT} 已被占用，先停掉旧后端再跑（禁止复用未知进程）`)
  if (await portInUse(WEB_PORT)) throw new Error(`端口 ${WEB_PORT} 已被占用，先停掉旧 Vite 再跑（禁止复用未知进程）`)

  const jar = findBootJar()
  const url = `jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true`
  fs.mkdirSync(path.join(SERVER_DIR, 'data', 'upload'), { recursive: true })
  log(`  后端 jar: ${path.relative(SERVER_DIR, jar)}`)
  backendProc = trackProcess(spawn(javaBin(), [
    '-jar', jar,
    `--spring.datasource.url=${url}`,
    `--spring.datasource.username=${DB_USER}`,
    `--spring.datasource.password=${DB_PASS}`,
    `--spring.data.redis.host=${process.env.REDIS_HOST || '127.0.0.1'}`,
    `--spring.data.redis.port=${process.env.REDIS_PORT || '6379'}`,
    '--logging.level.com.zhixue=info'
  ], {
    cwd: SERVER_DIR,
    env: { ...process.env, DB_PASSWORD: DB_PASS, DB_USERNAME: DB_USER },
    stdio: ['ignore', 'pipe', 'pipe'],
    detached: process.platform !== 'win32'
  }), 'backend')
  backendProc.on('close', (code) => { if (!cleaned && code !== 0) serverErrors.push(`backend 提前退出 code=${code}`) })

  await waitBackendHealthy()

  const viteBin = path.join(WEB_DIR, 'node_modules', 'vite', 'bin', 'vite.js')
  if (!fs.existsSync(viteBin)) throw new Error(`缺少 Vite：${viteBin}（先 npm ci）`)
  log('  启动 Vite dev（5173，/api → 8080）')
  frontendProc = trackProcess(spawn(process.execPath, [viteBin, '--host', '127.0.0.1', '--port', String(WEB_PORT)], {
    cwd: WEB_DIR,
    env: { ...process.env },
    stdio: ['ignore', 'pipe', 'pipe'],
    detached: process.platform !== 'win32'
  }), 'frontend')
  await waitFor('前端 dev server', `${WEB_BASE}/`, 90000, (res, body) => res.status === 200 && body.includes('id="app"'))
  log('  Vite 就绪')
}

// ---------------- HTTP / DB 辅助 ----------------

async function api(method, pathname, token, body) {
  const res = await fetch(BACKEND + pathname, {
    method,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...(token ? { satoken: token } : {})
    },
    body: body == null ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(20000)
  })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch { /* CSV/SSE 等非 JSON */ }
  return { status: res.status, json, text, headers: res.headers }
}

async function loginApi(username) {
  const r = await api('POST', '/api/auth/login', null, { username, password: DEMO_PASSWORD })
  if (r.json?.code !== 0 || !r.json?.data?.token) {
    throw new Error(`登录失败 ${username}: http=${r.status} code=${r.json?.code} ${r.json?.message || r.text.slice(0, 120)}`)
  }
  return r.json.data.token
}

function csvParse(text) {
  const rows = []
  let row = []
  let field = ''
  let quoted = false
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') { field += '"'; i++ }
      else if (ch === '"') quoted = false
      else field += ch
    } else if (ch === '"') quoted = true
    else if (ch === ',') { row.push(field); field = '' }
    else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++
      row.push(field); field = ''
      if (row.length > 1 || row[0] !== '') rows.push(row)
      row = []
    } else field += ch
  }
  if (field !== '' || row.length) { row.push(field); rows.push(row) }
  return rows
}

const db = (table) => `${DB_NAME}.${table}`

// ---------------- 主流程 ----------------

try {
  log(`=== 智学领航真实链路冒烟 ${new Date().toISOString()} ===`)
  log(`库=${DB_NAME} 后端=${BACKEND} 前端=${WEB_BASE} 输出=${OUT}`)

  if (!DB_NAME.startsWith('zhixue_e2e')) {
    throw new Error(`E2E_DB_NAME 必须以 zhixue_e2e 开头（防止误删开发库），当前=${DB_NAME}`)
  }

  // ---------- 0. 初始化真实库 ----------
  log('\n[0] 初始化数据库（仓库 SQL）')
  await sqlExec(`DROP DATABASE IF EXISTS ${DB_NAME}`)
  await applyRepoSql('init-v2.sql')
  await applyRepoSql('seed-demo-all.sql')

  // teacher02 派生 fixture：种子只有 teacher01。口令哈希复制自 teacher01（seed-demo-users.sql），
  // user_id 9000 不与演示学生 9001—9011 冲突，且不分配课程 → 用于 40300 越权场景。
  // 走 stdin 而非 -e，避免 Windows 命令行中文参数被控制台代码页破坏。
  await mysqlRun('stdin', `USE ${DB_NAME};
    INSERT INTO sys_user (user_id, username, password, real_name, role, class_name, is_active)
    SELECT 9000, '${TEACHER2}', password, '演示教师02', 'TEACHER', NULL, 1
    FROM sys_user WHERE user_id = 1
    ON DUPLICATE KEY UPDATE password = VALUES(password), role = 'TEACHER', is_active = 1;`, 'teacher02 fixture')
  const teacher2Courses = await sqlQuery(`SELECT COUNT(*) FROM ${db('course')} WHERE teacher_id = 9000 AND is_active = 1`)
  assert('0.1 teacher02 无课程（40300 前置）', Number(teacher2Courses) === 0, `courses=${teacher2Courses}`)

  const seeded = await sqlQuery(`SELECT
      (SELECT COUNT(*) FROM ${db('course')}),
      (SELECT COUNT(*) FROM ${db('question_bank')}),
      (SELECT COUNT(*) FROM ${db('student_error_record')}),
      (SELECT COUNT(*) FROM ${db('ai_generation_log')} WHERE scene='RAG')`)
  log(`  种子数据：course/question/error/RAG日志 = ${seeded.replace(/\t/g, ' / ')}`)

  const targetLog = await sqlQuery(`SELECT log_pk, user_id, reviewed_at FROM ${db('ai_generation_log')} WHERE explain_id='${TARGET_EXPLAIN}'`)
  const targetFields = targetLog.split('\t')
  if (targetFields.length < 3) throw new Error(`种子中找不到评阅目标 ${TARGET_EXPLAIN}，真实数据缺少 #49/#51 场景前置`)
  assert('0.2 评阅目标初始未评阅', targetFields[2] === 'NULL' || targetFields[2] === '', `logPk=${targetFields[0]} reviewed_at=${targetFields[2]}`)
  const targetLogPk = Number(targetFields[0])

  const expectedCsvRows = Number(await sqlQuery(`SELECT COUNT(*) FROM ${db('ai_generation_log')} l
    WHERE l.scene='RAG' AND NOT EXISTS (
      SELECT 1 FROM ${db('student_error_record')} e JOIN ${db('question_bank')} q ON q.q_id=e.q_id
      WHERE e.log_id=l.log_id AND q.course_id NOT IN (SELECT course_id FROM ${db('course')} WHERE teacher_id=1 AND is_active=1))`))
  assert('0.3 教师留痕导出预期行数', expectedCsvRows >= 1, `expected=${expectedCsvRows}`)

  // ---------- 启动真实后端 + 前端 ----------
  log('\n[启动] 真实 Spring Boot + Vite')
  await startServers()

  const studentToken = await loginApi(STUDENT)
  const teacherToken = await loginApi(TEACHER)
  const teacher2Token = await loginApi(TEACHER2)
  assert('0.4 三账号真实登录', !!studentToken && !!teacherToken && !!teacher2Token)

  // ---------- 场景 1：学生 UI 登录 → 首页 → 作答 → 提交 → 真写库 ----------
  log('\n[场景 1] 学生登录 → 学生首页 → 开始练习 → 作答 → 提交')
  const beforeRows = Number(await sqlQuery(`SELECT COUNT(*) FROM ${db('practice_record')} WHERE user_id=2`))
  browser = await chromium.launch({
    channel: process.env.E2E_CHANNEL || 'msedge',
    headless: true
  })
  const stCtx = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' })
  const stPage = await stCtx.newPage()
  stPage.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)) })
  stPage.on('pageerror', (e) => consoleErrors.push('PAGEERROR ' + e.message.slice(0, 200)))
  let submitted = null
  stPage.on('response', async (r) => {
    if (r.request().method() !== 'POST') return
    if (!/\/api\/practices$/.test(new URL(r.url()).pathname)) return
    try {
      const json = await r.json()
      if (json?.code === 0) submitted = json.data
    } catch { /* ignore */ }
  })

  await stPage.goto(WEB_BASE + '/login', { waitUntil: 'domcontentloaded' })
  await stPage.waitForSelector('.login-card input', { timeout: 20000 })
  await stPage.locator('.login-card input').nth(0).fill(STUDENT)
  await stPage.locator('.login-card input').nth(1).fill(DEMO_PASSWORD)
  await stPage.getByRole('button', { name: '登录' }).click()
  await stPage.waitForURL((url) => new URL(url).pathname === '/today', { timeout: 20000 })
  await stPage.waitForSelector('.metric-card', { timeout: 20000 })
  const metricCards = await stPage.locator('.metric-card').count()
  assert('1.1 学生真实登录并进入首页', metricCards >= 1, `metricCards=${metricCards}`)

  await stPage.goto(WEB_BASE + '/practice', { waitUntil: 'domcontentloaded' })
  await stPage.waitForSelector('.finder-input input', { timeout: 20000 })
  await stPage.locator('.finder-input input').fill(SEARCH_KP)
  await stPage.waitForSelector('.kp-result', { timeout: 20000 })
  await stPage.locator('.kp-result').first().click()
  await stPage.waitForSelector('.question-row', { timeout: 20000 })
  const questionRows = await stPage.locator('.question-row').count()
  assert('1.2 开始练习：真实题库出题', questionRows >= 1, `${SEARCH_KP} questions=${questionRows}`)
  await stPage.locator('.question-row').first().click()
  await stPage.waitForSelector('.practice-drawer .stem', { timeout: 20000 })

  const optionCount = await stPage.locator('.practice-drawer .option').count()
  if (optionCount > 0) {
    await stPage.locator('.practice-drawer .option').first().click()
  } else {
    const textarea = stPage.locator('.practice-drawer textarea')
    await textarea.waitFor({ state: 'visible', timeout: 10000 })
    await textarea.fill(`E2E 真实冒烟作答 ${runId}`)
  }
  await stPage.getByRole('button', { name: '查看参考答案' }).click()
  await stPage.waitForSelector('.practice-drawer .answer-panel', { timeout: 20000 })
  const objective = await stPage.getByRole('button', { name: '提交本题（系统判分）' }).count()
  if (objective > 0) await stPage.getByRole('button', { name: '提交本题（系统判分）' }).click()
  else await stPage.getByRole('button', { name: '我答对了（自评）' }).click()
  await stPage.waitForSelector('.practice-drawer .submission-result', { timeout: 20000 })
  await sleep(500)

  assert('1.3 提交返回 practiceId', !!submitted?.practiceId, JSON.stringify(submitted)?.slice(0, 200))
  const afterRows = Number(await sqlQuery(`SELECT COUNT(*) FROM ${db('practice_record')} WHERE user_id=2`))
  assert('1.4 practice_record 真落一行', afterRows === beforeRows + 1, `before=${beforeRows} after=${afterRows}`)

  if (submitted?.practiceId) {
    const row = await sqlQuery(`SELECT user_id, q_id, kp_id, source, is_correct, LENGTH(my_answer)
      FROM ${db('practice_record')} WHERE practice_id=${Number(submitted.practiceId)}`)
    const cols = row.split('\t')
    assert(
      '1.5 落库字段与作答一致',
      cols.length >= 6 && cols[0] === '2' && cols[2] === SEARCH_KP && Number(cols[5]) > 0,
      `user=${cols[0]} q=${cols[1]} kp=${cols[2]} source=${cols[3]} correct=${cols[4]} answerLen=${cols[5]}`
    )
  }
  await stPage.screenshot({ path: path.join(OUT, '01-student-practice.png') })

  // ---------- 场景 2：诊断/画像（真实掌握度、薄弱点） ----------
  log('\n[场景 2] 诊断工作台：真实掌握度/薄弱点字段')
  // Sa-Token 单会话（is-concurrent=false）：UI 重新登录会轮换掉同账号旧 token，
  // 所以先在 UI 会话内检查画像页，再用接口重登后的新 token 做字段断言。
  await stPage.goto(WEB_BASE + '/portrait', { waitUntil: 'domcontentloaded' })
  await stPage.waitForSelector('.baseline-row', { timeout: 20000 })
  const portraitText = await stPage.locator('#main-content').innerText()
  assert('2.5 画像页真实渲染', /掌握度\s*\d+%/.test(portraitText) && !/加载失败|服务异常/.test(portraitText), portraitText.match(/掌握度\s*\d+%/)?.[0] || '')
  await stPage.screenshot({ path: path.join(OUT, '02-student-portrait.png') })
  await stCtx.close()

  const studentTokenApi = await loginApi(STUDENT)
  const portrait = await api('GET', '/api/portraits/me', studentTokenApi)
  const p = portrait.json?.data
  const dims = p?.dimensionScores && Object.values(p.dimensionScores).filter((v) => typeof v === 'number')
  const weak = Array.isArray(p?.weakestKps) ? p.weakestKps : []
  assert('2.1 学生画像接口不报错', portrait.json?.code === 0 && !!p, `http=${portrait.status} code=${portrait.json?.code}`)
  assert('2.2 掌握度维度非空', (dims?.length || 0) >= 3, `dimensions=${dims?.length || 0}`)
  assert(
    '2.3 薄弱点字段非空',
    weak.length >= 1 && weak.every((w) => w.kpId && w.name && typeof w.mastery === 'number'),
    weak.slice(0, 2).map((w) => `${w.kpId}:${w.mastery}`).join(',')
  )
  assert('2.4 真实练习计数', typeof p?.totalPractice === 'number' && p.totalPractice > 0, `totalPractice=${p?.totalPractice}`)

  const classPortrait = await api('GET', '/api/portraits/class/1', teacherToken)
  const cp = classPortrait.json?.data
  assert(
    '2.6 教师班级画像（掌握度/薄弱点）',
    classPortrait.json?.code === 0 && cp && cp.studentCount > 0 && Array.isArray(cp.weakestKpTop) && cp.weakestKpTop.length > 0
      && cp.weakestKpTop.every((w) => w.kpId && typeof w.avgMastery === 'number'),
    `students=${cp?.studentCount} weak=${cp?.weakestKpTop?.length}`
  )

  // ---------- 场景 4（先做越权，避免评阅后被误判为状态相关）：40300 ----------
  log('\n[场景 4] 越权：teacher02 读不属于自己的讲解')
  const forbidden = await api('GET', `/api/diagnosis/explain/${TARGET_EXPLAIN}`, teacher2Token)
  assert(
    '4.1 teacher02 越权读取 → 40300',
    forbidden.status === 200 && forbidden.json?.code === 40300,
    `http=${forbidden.status} code=${forbidden.json?.code} msg=${forbidden.json?.message}`
  )

  // ---------- 场景 3：教师 UI 评阅 PASS → 学生端可见 ----------
  log('\n[场景 3] 教师评阅：待办列表 → PASS 评语 → 学生端可见')
  const queue = await api('GET', '/api/teacher/reviews?status=PENDING&size=50', teacherToken)
  const queueRows = queue.json?.data?.records || []
  assert('3.1 评阅待办真实有数据', queue.json?.code === 0 && queueRows.length >= 1, `total=${queue.json?.data?.total}`)
  const targetItem = queueRows.find((r) => r.explainId === TARGET_EXPLAIN)
  assert('3.2 待办含种子目标记录', !!targetItem, targetItem ? `logPk=${targetItem.logPk} user=${targetItem.userName}` : `explain=${TARGET_EXPLAIN}`)

  const tpCtx = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' })
  const tp = await tpCtx.newPage()
  tp.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 200)) })
  tp.on('pageerror', (e) => consoleErrors.push('PAGEERROR ' + e.message.slice(0, 200)))

  await tp.goto(WEB_BASE + '/login', { waitUntil: 'domcontentloaded' })
  await tp.waitForSelector('.login-card input', { timeout: 20000 })
  await tp.locator('.login-card input').nth(0).fill(TEACHER)
  await tp.locator('.login-card input').nth(1).fill(DEMO_PASSWORD)
  await tp.getByRole('button', { name: '登录' }).click()
  await tp.waitForURL((url) => new URL(url).pathname === '/teacher', { timeout: 20000 })

  await tp.goto(WEB_BASE + '/teacher/reviews', { waitUntil: 'domcontentloaded' })
  await tp.waitForSelector('.el-table__row', { timeout: 20000 })
  const uiRows = await tp.locator('.el-table__row').count()
  assert('3.3 教师 UI 评阅队列有数据', uiRows >= 1, `rows=${uiRows}`)

  const targetRow = tp.locator('.el-table__row', { hasText: targetItem?.userName || '演示学生01' }).first()
  assert('3.4 UI 定位到目标学生行', await targetRow.count() >= 1)
  await targetRow.getByRole('button', { name: '评阅' }).click()
  await tp.waitForSelector('.review-drawer', { state: 'visible', timeout: 15000 })
  assert('3.5 评阅抽屉含评阅证据', (await tp.locator('.review-drawer .evidence-block').count()) >= 1)

  await tp.locator('.review-drawer .score-pill').nth(4).click()
  const passRadio = tp.locator('.review-drawer .el-radio-button').first()
  const passDisabled = await passRadio.evaluate((el) => el.className.includes('is-disabled')).catch(() => true)
  assert('3.6 证据完整允许 PASS', !passDisabled)
  await tp.locator('.review-drawer textarea').fill(COMMENT)
  await tp.getByRole('button', { name: '提交评阅' }).click()
  const toast = await tp.getByText('评阅已提交').first().waitFor({ state: 'visible', timeout: 15000 }).then(() => true).catch(() => false)
  const drawerClosed = await tp.locator('.review-drawer').waitFor({ state: 'hidden', timeout: 15000 }).then(() => true).catch(() => false)
  assert('3.7 评阅提交成功', toast || drawerClosed)
  await tp.screenshot({ path: path.join(OUT, '03-teacher-review.png') })
  await tpCtx.close()

  // 教师 UI 登录已轮换 teacherToken；学生 UI 会话也已轮换学生 token，这里重新登录后读回。
  const studentTokenAfter = await loginApi(STUDENT)
  const studentView = await api('GET', `/api/diagnosis/explain/${TARGET_EXPLAIN}`, studentTokenAfter)
  const sv = studentView.json?.data
  assert(
    '3.8 学生端可见 PASS 评语',
    studentView.json?.code === 0 && sv?.reviewResult === 'PASS' && sv?.reviewComment === COMMENT && !!sv?.reviewedAt,
    `result=${sv?.reviewResult} comment=${sv?.reviewComment} reviewedAt=${sv?.reviewedAt}`
  )
  const dbReview = await sqlQuery(`SELECT review_result, teacher_score, teacher_comment, reviewed_at
    FROM ${db('ai_generation_log')} WHERE log_pk=${targetLogPk}`)
  const dbCols = dbReview.split('\t')
  assert('3.9 评阅真写库', dbCols[0] === 'PASS' && dbCols[2] === COMMENT && dbCols[3] !== 'NULL', dbReview.replace(/\t/g, ' | '))

  // ---------- 场景 5：CSV 导出（真数据） ----------
  log('\n[场景 5] 评阅留痕 CSV 导出')
  const teacherTokenAfter = await loginApi(TEACHER)
  const csvRes = await fetch(`${BACKEND}/api/teacher/reviews/export?status=ALL`, {
    // 与前端 axios 默认 Accept 一致；避免异常路径因 Accept 只有 text/csv 而 500
    headers: { Accept: 'application/json, text/plain, */*', satoken: teacherTokenAfter },
    signal: AbortSignal.timeout(30000)
  })
  const csvBuf = await csvRes.arrayBuffer()
  const csvText = new TextDecoder('utf-8', { ignoreBOM: true }).decode(csvBuf)
  fs.writeFileSync(path.join(OUT, 'review-export.csv'), csvText)
  const csvRows = csvParse(csvText.replace(/^\uFEFF/, ''))
  const header = csvRows[0] || []
  const expectedHeader = '留痕ID,讲解ID,学生（脱敏）,知识点,错因,题干,模型,是否降级,创建时间,评阅结果,教师评分,教师评语,评阅时间'
  assert('5.1 CSV HTTP 200 + UTF-8 BOM', csvRes.status === 200 && csvText.startsWith('\uFEFF'), `http=${csvRes.status} type=${csvRes.headers.get('content-type')} bom=${csvText.charCodeAt(0) === 0xfeff}`)
  assert('5.2 CSV 表头 13 列且一致', header.join(',') === expectedHeader, `cols=${header.length} header=${header.join('|')}`)
  assert('5.3 CSV 行数等于真实范围', csvRows.length - 1 === expectedCsvRows, `csv=${csvRows.length - 1} expected=${expectedCsvRows}`)
  // 防泄漏用正文独有片段（种子评语同样以 [演示数据] 开头，不能拿这个当标记）
  const contentProbe = (await sqlQuery(`SELECT SUBSTRING(content_text, 12, 16) FROM ${db('ai_generation_log')} WHERE explain_id='${TARGET_EXPLAIN}'`)).trim()
  assert(
    '5.4 CSV 不含 content_text 泄漏',
    !header.some((h) => /content|正文/.test(h)) && contentProbe.length >= 8 && !csvText.includes(contentProbe),
    `probe=${contentProbe}`
  )
  assert('5.5 CSV 含本次真实评阅行', csvText.includes(COMMENT))

  // 先清理再统计：端口未释放等收尾失败也要计入退出码，不能只打日志
  await cleanup()

  const failed = results.filter((r) => !r.pass)
  log(`\n真实冒烟：${results.length - failed.length}/${results.length} 通过`)
  if (consoleErrors.length) log(`浏览器 console 错误 ${consoleErrors.length} 条：${consoleErrors.slice(0, 3).join(' | ')}`)
  if (serverErrors.length) log(`服务端错误 ${serverErrors.length} 条：${serverErrors.slice(0, 3).join(' | ')}`)

  fs.writeFileSync(path.join(OUT, 'report.json'), JSON.stringify({
    runId,
    db: DB_NAME,
    backend: BACKEND,
    web: WEB_BASE,
    results,
    consoleErrors,
    serverErrors
  }, null, 2))
  log(`报告：${path.join(OUT, 'report.json')}`)

  if (failed.length) {
    log('\n失败项：' + failed.map((f) => f.name).join('；'))
  }
  log(`\n退出码 ${failed.length ? 1 : 0}`)
  process.exit(failed.length ? 1 : 0)
} catch (e) {
  fail('runner 异常', e.message)
  log(`\nRUNNER ERROR: ${e.stack || e.message}`)
  fs.writeFileSync(path.join(OUT, 'report.json'), JSON.stringify({ runId, db: DB_NAME, results, error: e.message }, null, 2))
  try { if (browser) await browser.close() } catch { /* noop */ }
  await cleanup()
  log('\n退出码 1')
  process.exit(1)
}
