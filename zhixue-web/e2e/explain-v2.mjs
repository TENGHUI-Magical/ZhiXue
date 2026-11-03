import { chromium } from 'playwright-core'
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const PORT = process.env.E2E_PORT || '5175'
const BASE = process.env.BASE || `http://localhost:${PORT}`
const OUT = process.env.E2E_OUT || path.resolve('../../spark-output/qa/explain-v2-e2e')
fs.mkdirSync(OUT, { recursive: true })
const results = []
const check = (name, condition, detail = '') => {
  results.push({ name, pass: !!condition, detail })
  if (!condition) console.log('FAIL:', name, detail)
}

async function ensureServer() {
  try {
    const response = await fetch(BASE, { signal: AbortSignal.timeout(1500) })
    if (response.ok) return null
  } catch { /* start Vite below */ }
  const child = spawn('npm', ['run', 'dev', '--', '--port', PORT, '--strictPort'], {
    cwd: process.cwd(), stdio: 'ignore', shell: true
  })
  for (let i = 0; i < 60; i++) {
    await new Promise(resolve => setTimeout(resolve, 500))
    try {
      const response = await fetch(BASE, { signal: AbortSignal.timeout(1000) })
      if (response.ok) return child
    } catch { /* retry */ }
  }
  child.kill()
  throw new Error('Vite 未能在 30s 内启动')
}

const sectionContent = '【错因定位】\n你混淆了候选码与主码。\n【完整讲解】\n先检查候选码的最小性。\n【自查】\n去掉一个属性后还能唯一确定元组吗？'
const cardContent = '【学习卡】\n【知识点】关系模型；教材P34\n【常见误区】\n1. 误区一\n2. 误区二\n3. 误区三\n4. 误区四\n【练习提示】请完成下方同类练习，检验自己是否真正掌握。'
const sections = {
  errorLocation: '你混淆了候选码与主码。',
  explanation: '先检查候选码的最小性。',
  selfCheck: '去掉一个属性后还能唯一确定元组吗？'
}
const learningCard = {
  kpName: '关系模型', textbookPage: 'P34',
  misconceptions: ['误区一', '误区二', '误区三'],
  practiceHint: '请完成下方同类练习，检验自己是否真正掌握。'
}
const envelope = data => ({ code: 0, data, timestamp: 1 })
const frame = (event, data) => `event:${event}\ndata:${JSON.stringify(data)}\n\n`

async function runScenario(browser, kind) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'zh-CN' })
  await context.addInitScript(() => {
    localStorage.setItem('satoken', 'mock')
    localStorage.setItem('zhixue_user', JSON.stringify({ userId: 2, username: 'u', realName: '同学', role: 'STUDENT' }))
  })
  let explainCalls = 0
  const isCard = kind === 'card'
  const explainId = isCard ? 'EX_CARD' : 'EX_SECTIONS'
  const content = isCard ? cardContent : sectionContent
  await context.route('**/*', async route => {
    const request = route.request()
    const method = request.method()
    const pathname = new URL(request.url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    const json = data => route.fulfill({ status: 200, contentType: 'application/json; charset=utf-8', body: JSON.stringify(envelope(data)) })
    if (method === 'POST' && pathname === '/api/diagnosis/explain') {
      explainCalls += 1
      const body =
        frame('meta', { explainId, traceId: 'T_V2', model: 'auto', degraded: isCard }) +
        frame('source', { kpId: 'KP0201', textbookPage: 'P34', title: '关系模型' }) +
        (isCard ? frame('degraded', { level: 'L3', reason: 'AI 服务不可用' }) : '') +
        frame('delta', { text: content }) +
        frame('done', {
          explainId, latencyMs: 12, tokens: 20,
          ...(isCard ? { learningCard } : { sections })
        })
      return route.fulfill({ status: 200, contentType: 'text/event-stream; charset=utf-8', body })
    }
    if (method === 'POST' && pathname === '/api/diagnosis/map') {
      return json({ decision: 'AUTO', top1: { kpId: 'KP0201', name: '关系模型', score: 0.99, channel: 'VECTOR' }, candidates: [{ kpId: 'KP0201', name: '关系模型', score: 0.99, channel: 'VECTOR' }], degraded: false })
    }
    if (method === 'POST' && pathname === '/api/error/records') return json({ logId: 'EL_V2', status: 'DRAFT' })
    if (method === 'PUT' && pathname === '/api/error/records/EL_V2/confirm') return json({ logId: 'EL_V2', status: 'CONFIRMED' })
    if (method === 'GET' && pathname === `/api/diagnosis/explain/${explainId}`) {
      return json({ explainId, content, sources: [], degraded: isCard, degradeLevel: isCard ? 'L3' : null, blocked: false })
    }
    if (method === 'GET' && pathname === '/api/auth/me') return json({ userId: 2, username: 'u', realName: '同学', role: 'STUDENT' })
    if (method === 'GET' && pathname === '/api/courses') return json([])
    if (method === 'GET' && pathname === '/api/recommendations') return json([])
    if (method === 'GET' && pathname === '/api/questions') return json([])
    if (method === 'GET') return json(null)
    return json(null)
  })
  const page = await context.newPage()
  page.on('pageerror', error => console.log('PAGE ERROR:', error.message))
  page.on('console', message => {
    if (message.type() === 'error') console.log('CONSOLE ERROR:', message.text())
  })
  await page.goto(`${BASE}/diagnosis`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('#main-content .page', { timeout: 20000 })
  await page.locator('textarea').first().fill('关系模型题干')
  await page.getByRole('button', { name: '匹配知识点', exact: true }).click()
  await page.getByRole('button', { name: '生成启发式讲解', exact: true }).click()
  await page.waitForSelector(isCard ? '[data-testid="learning-card"]' : '[data-testid="explain-sections"]', { timeout: 15000 })

  if (!isCard) {
    check('三段式共 3 个区域', await page.locator('[data-testid="explain-sections"] > section').count() === 3)
    check('错因定位精确展示', (await page.locator('[data-testid="error-location"] p').textContent())?.trim() === sections.errorLocation)
    check('完整讲解精确展示', (await page.locator('[data-testid="full-explanation"] p').textContent())?.trim() === sections.explanation)
    check('自查问题精确展示且只有 1 个', await page.locator('[data-testid="self-check"]').count() === 1 && (await page.locator('[data-testid="self-check"] p').textContent())?.trim() === sections.selfCheck)
    check('三段式不重复显示原始标记正文', await page.locator('.explain-text').count() === 0)
  } else {
    check('学习卡标签使用学生语言', (await page.locator('.head-actions .el-tag').textContent())?.trim() === '学习卡')
    check('学习卡知识点与页码精确展示', (await page.locator('[data-testid="learning-card-kp"]').textContent())?.trim() === '关系模型 · 教材P34')
    check('学习卡最多 3 条误区', await page.locator('[data-testid="learning-card-misconceptions"] li').count() === 3)
    check('学习卡误区内容精确展示', JSON.stringify(await page.locator('[data-testid="learning-card-misconceptions"] li').allTextContents()) === JSON.stringify(['误区一', '误区二', '误区三']))
    check('学习卡练习提示精确展示', (await page.locator('[data-testid="learning-card-practice"]').textContent())?.trim() === learningCard.practiceHint)
    check('学习卡不展示讲解长文或原始标记', await page.locator('.explain-text').count() === 0)
    const detailText = await page.locator('.explain-detail').textContent()
    check('学生区域无内部技术词', !/降级|模板|LLM/.test(detailText || ''))
  }
  check(`${kind} 只发起 1 次讲解请求`, explainCalls === 1, `actual=${explainCalls}`)
  await page.goto(`${BASE}/diagnosis?explainId=${explainId}`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector(isCard ? '[data-testid="learning-card"]' : '[data-testid="explain-sections"]', { timeout: 15000 })
  const restoredText = isCard
    ? (await page.locator('[data-testid="learning-card-kp"]').textContent())?.trim()
    : (await page.locator('[data-testid="error-location"] p').textContent())?.trim()
  check(`${kind} 刷新后仍恢复结构化展示`, restoredText === (isCard ? '关系模型 · 教材P34' : sections.errorLocation))
  await context.close()
}

const server = await ensureServer()
const browser = await chromium.launch({ channel: 'msedge', headless: true })
try {
  await runScenario(browser, 'sections')
  await runScenario(browser, 'card')
} finally {
  await browser.close()
  if (server) server.kill()
}
const passed = results.filter(item => item.pass).length
fs.writeFileSync(path.join(OUT, 'explain-v2-report.json'), JSON.stringify({ total: results.length, passed, results }, null, 2))
console.log(`[explain-v2] ${passed}/${results.length} checks passed`)
if (passed !== results.length) process.exit(1)
