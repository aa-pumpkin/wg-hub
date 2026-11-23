import { chromium } from 'playwright'
const BASE='http://localhost:8090'
const b=await chromium.launch()
const WIDTHS=[320,390,768,1024,1440], LANGS=['de','ru','en']
const PAGES=['dashboard','chores','chores#plan','shopping','calendar','notes','expenses','points','dishwasher','members','settings','profile','more']
let found=new Map(), checked=0
const ctxBase=await b.newContext({locale:'de-DE'}); const lp=await ctxBase.newPage()
await lp.goto(BASE+'/login'); await lp.locator('input').first().fill('anna'); await lp.locator('input[type=password]').fill('demo12345'); await lp.locator('button[type=submit]').click(); await lp.waitForURL('**/dashboard')
const hid=lp.url().match(/w\/([^/]+)/)[1]; const state=await ctxBase.storageState()
const inspect=()=>{ // runs in page
  const out=[]; const vw=document.documentElement.clientWidth
  const name=e=>(e.tagName.toLowerCase()+' "'+(e.innerText||e.getAttribute('aria-label')||'').trim().replace(/\s+/g,' ').slice(0,30)+'"')
  if(document.documentElement.scrollWidth>vw+1) out.push(`page scrolls sideways ${document.documentElement.scrollWidth}>${vw}`)
  const vis=e=>{const r=e.getBoundingClientRect();const s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'}
  for(const e of document.querySelectorAll('main *, nav *')){
    if(!vis(e)||e.matches('.sr-only')||e.closest('dialog')||e.closest('[popover]')||e.tagName==='svg'||e.closest('svg'))continue
    const r=e.getBoundingClientRect()
    if(r.right>vw+1||r.left<-1) out.push('off screen: '+name(e))
    const card=e.parentElement?.closest('.card')
    if(card&&!e.closest('[role=menu]')){const c=card.getBoundingClientRect(); if(r.right>c.right+1||r.left<c.left-1) out.push('sticks out of card: '+name(e))}
    // text cut off in something that is not meant to truncate
    if(e.children.length===0&&e.innerText&&e.scrollWidth>e.clientWidth+1&&getComputedStyle(e).overflow!=='visible'&&getComputedStyle(e).textOverflow!=='ellipsis') out.push('clipped: '+name(e))
    if(['BUTTON','A'].includes(e.tagName)&&e.children.length===0&&e.scrollWidth>e.clientWidth+1&&getComputedStyle(e).textOverflow!=='ellipsis') out.push('button text overflows: '+name(e))
  }
  // rows in one card must share left and right edges with the card's padding
  for(const row of document.querySelectorAll('.list-row')){
    if(!vis(row)||row.closest('dialog'))continue
    const rr=row.getBoundingClientRect()
    const kids=[...row.children].filter(vis); if(!kids.length)continue
    const visual=k=>{const c=k.matches('button[role=checkbox]')?k.firstElementChild:k.matches('button.tap,.row-end')?null:k;return c?c.getBoundingClientRect():null}
    const f=visual(kids[0]); if(f&&Math.abs(f.left-rr.left-16)>1.5) out.push(`left edge ${Math.round(f.left-rr.left)}px: `+name(row))
    const l=visual(kids.at(-1)); if(l&&Math.abs(rr.right-l.right-16)>1.5&&!row.classList.contains('flex-wrap')) out.push(`right edge ${Math.round(rr.right-l.right)}px: `+name(row))
  }
  // cards side by side must be the same height
  for(const grid of document.querySelectorAll('main .grid')){
    const items=[...grid.children].filter(k=>vis(k)&&k.matches('.card,section.card,a'))
    const byRow=new Map(); for(const k of items){const r=k.getBoundingClientRect();const y=Math.round(r.top);byRow.set(y,[...(byRow.get(y)||[]),Math.round(r.height)])}
    if(!grid.className.includes('items-start')) for(const hs of byRow.values()) if(new Set(hs).size>1&&hs.length>1) out.push('uneven row '+hs.join('/'))
  }
  return [...new Set(out)]
}
for(const lang of LANGS)for(const w of WIDTHS){
  const c=await b.newContext({storageState:state,viewport:{width:w,height:800},locale:'de-DE',hasTouch:w<768})
  const p=await c.newPage()
  await p.goto(`${BASE}/w/${hid}/dashboard`); await p.waitForTimeout(300)
  await p.evaluate(async(l)=>{await fetch('/api/me',{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({locale:l})});localStorage.setItem('wghub.locale',l)},lang)
  for(const page of PAGES){
    const [route,tab]=page.split('#')
    await p.goto(`${BASE}/w/${hid}/${route}`); await p.waitForTimeout(450)
    if(tab){ await p.locator('main [role=group] button').nth(1).click(); await p.waitForTimeout(450)}
    checked++
    for(const issue of await p.evaluate(inspect)){const k=`${page} | ${issue}`;const v=found.get(k)||[];v.push(`${lang}${w}`);found.set(k,v)}
    if(lang==='de'&&(w===390||w===1440)||lang==='ru'&&w===320) await p.screenshot({path:`shots/a-${page.replace('#','-')}-${lang}-${w}.png`})
  }
  await c.close()
}
console.log('pages checked',checked)
for(const [k,v] of found)console.log(k,' <- ',v.slice(0,6).join(','),v.length>6?`+${v.length-6}`:'')
console.log(found.size?`ISSUES ${found.size}`:'NO ISSUES')
await b.close()
