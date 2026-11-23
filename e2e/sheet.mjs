import { chromium } from 'playwright'
import { readFileSync } from 'node:fs'
const [out, width, ...names] = process.argv.slice(2)
const b = await chromium.launch()
const p = await b.newPage({ viewport: { width: 1800, height: 900 } })
const imgs = names.map(n => `<img style="width:${width}px;border:1px solid #888" src="data:image/png;base64,${readFileSync('shots/'+n+'.png').toString('base64')}">`).join('')
await p.setContent(`<body style="margin:8px;display:flex;flex-wrap:wrap;gap:8px;background:#777">${imgs}</body>`)
await p.waitForTimeout(200)
await p.screenshot({ path: out, fullPage: true })
await b.close()
