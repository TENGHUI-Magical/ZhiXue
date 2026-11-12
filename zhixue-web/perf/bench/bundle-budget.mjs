#!/usr/bin/env node
/**
 * 确定性体积棘轮：解析 dist/assets，按 gzip 体积检查预算。
 * 用法：
 *   node perf/bench/bundle-budget.mjs           # 检查，超限退出 1
 *   node perf/bench/bundle-budget.mjs --update  # 把当前值写回 ceilings.json
 *
 * 口径：同一 Node/Vite 版本、同一构建命令下的 gzip 字节数；不做平均数。
 */
import fs from 'node:fs'
import path from 'node:path'
import zlib from 'node:zlib'

const distDir = path.resolve('dist/assets')
const ceilingsPath = path.resolve('perf/bench/ceilings.json')
const update = process.argv.includes('--update')

const files = fs.readdirSync(distDir).filter((file) => /\.(js|css)$/.test(file))
if (files.length === 0) {
  console.error('dist/assets 为空，先运行 vite build')
  process.exit(2)
}

const rows = files.map((file) => {
  const buffer = fs.readFileSync(path.join(distDir, file))
  return {
    file,
    type: file.endsWith('.js') ? 'js' : 'css',
    raw: buffer.length,
    gzip: zlib.gzipSync(buffer).length
  }
})

const sum = (items) => items.reduce((total, item) => total + item.gzip, 0)
const max = (items) => (items.length === 0 ? 0 : Math.max(...items.map((item) => item.gzip)))
const js = rows.filter((row) => row.type === 'js')
const css = rows.filter((row) => row.type === 'css')

const metrics = {
  totalJsGzip: sum(js),
  totalCssGzip: sum(css),
  largestJsGzip: max(js),
  largestCssGzip: max(css)
}

const labels = {
  totalJsGzip: 'JS 总 gzip',
  totalCssGzip: 'CSS 总 gzip',
  largestJsGzip: '最大 JS chunk gzip',
  largestCssGzip: '最大 CSS chunk gzip'
}

const kb = (bytes) => `${(bytes / 1024).toFixed(2)} kB`

console.log('构建产物体积（gzip）：')
for (const [key, value] of Object.entries(metrics)) {
  console.log(`  ${labels[key]}: ${kb(value)}`)
}
console.log('最大的 5 个文件：')
for (const row of rows.sort((a, b) => b.gzip - a.gzip).slice(0, 5)) {
  console.log(`  ${row.file}: ${kb(row.gzip)}`)
}

if (update) {
  fs.writeFileSync(ceilingsPath, JSON.stringify(metrics, null, 2) + '\n')
  console.log(`已写入棘轮上限：${ceilingsPath}`)
  process.exit(0)
}

let ceilings
try {
  ceilings = JSON.parse(fs.readFileSync(ceilingsPath, 'utf8'))
} catch (error) {
  // 棘轮上限是提交进仓库的“合同”，不能在读取失败时自动写回并 exit 0：
  // 那会让配置损坏 / 文件缺失 / JSON 语法错误的构建全部变绿，等于门禁失效。
  const reason = error instanceof Error ? error.message : String(error)
  console.error('✗ 读取体积上限失败：' + ceilingsPath)
  console.error('  ' + reason)
  console.error('  若确属首次建立基线，请显式运行：node perf/bench/bundle-budget.mjs --update')
  process.exit(1)
}

const REQUIRED_KEYS = ['totalJsGzip', 'totalCssGzip', 'largestJsGzip', 'largestCssGzip']
const invalidKeys = REQUIRED_KEYS.filter((key) => {
  const value = ceilings?.[key]
  return typeof value !== 'number' || !Number.isFinite(value) || value <= 0
})
if (invalidKeys.length > 0) {
  console.error('✗ 体积上限配置不完整或非法：' + invalidKeys.join(', '))
  console.error('  修正 perf/bench/ceilings.json 后重试（不要留空值，否则该指标等于不设限）')
  process.exit(1)
}

const failures = []
for (const [key, value] of Object.entries(metrics)) {
  const limit = ceilings[key]
  if (limit == null) {
    failures.push(`缺少 ${key} 的上限，运行 --update 初始化`)
    continue
  }
  if (value > limit) {
    failures.push(`${labels[key]} ${kb(value)} 超过上限 ${kb(limit)}`)
  }
}

if (failures.length > 0) {
  console.error('✗ 体积棘轮失败：')
  for (const failure of failures) {
    console.error(`  - ${failure}`)
  }
  process.exit(1)
}

console.log('✓ 体积棘轮通过')
