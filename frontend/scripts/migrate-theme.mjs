import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const SRC = path.join(__dirname, '..', 'src')

const COLOR_MAP = [
  // 文字
  [/(color:\s*)#303133/gi, '$1var(--color-text)'],
  [/(color:\s*)#606266/gi, '$1var(--color-text-2)'],
  [/(color:\s*)#909399/gi, '$1var(--color-text-3)'],
  [/(color:\s*)#c0c4cc/gi, '$1var(--color-text-3)'],
  // 边框
  [/(border[^:]*:\s*)#dcdfe6/gi, '$1var(--color-border)'],
  [/(border[^:]*:\s*)#ebeef5/gi, '$1var(--color-border-light)'],
  [/(border[^:]*:\s*)#e4e7ed/gi, '$1var(--color-border-light)'],
  // 背景与表面
  [/(background(?:-color)?:\s*)#ffffff\b/gi, '$1var(--color-surface)'],
  [/(background(?:-color)?:\s*)#f5f7fa\b/gi, '$1var(--color-bg)'],
  // 品牌/状态色
  [/(color:\s*)#409eff\b/gi, '$1var(--color-primary)'],
  [/(color:\s*)#67c23a\b/gi, '$1var(--color-success)'],
  [/(color:\s*)#e6a23c\b/gi, '$1var(--color-warning)'],
  [/(color:\s*)#f56c6c\b/gi, '$1var(--color-danger)'],
  [/(background(?:-color)?:\s*)#409eff\b/gi, '$1var(--color-primary)'],
  [/(background(?:-color)?:\s*)#67c23a\b/gi, '$1var(--color-success)'],
  [/(background(?:-color)?:\s*)#e6a23c\b/gi, '$1var(--color-warning)'],
  [/(background(?:-color)?:\s*)#f56c6c\b/gi, '$1var(--color-danger)'],
]

function walk(dir) {
  const files = []
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name)
    const stat = fs.statSync(full)
    if (stat.isDirectory()) {
      files.push(...walk(full))
    } else if (name.endsWith('.vue')) {
      files.push(full)
    }
  }
  return files
}

function processFile(file) {
  const raw = fs.readFileSync(file, 'utf8')
  const m = raw.replace(/(<style[^>]*>)([\s\S]*?)(<\/style>)/g, (match, open, body, close) => {
    let updated = body
    for (const [pattern, replacement] of COLOR_MAP) {
      updated = updated.replace(pattern, replacement)
    }
    return open + updated + close
  })
  if (m !== raw) {
    fs.writeFileSync(file, m, 'utf8')
    console.log('MIGRATED', path.relative(SRC, file))
  }
}

for (const file of walk(SRC)) {
  processFile(file)
}
console.log('Theme migration finished.')
