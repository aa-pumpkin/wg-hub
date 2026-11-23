import { chromium } from 'playwright'
const BASE='http://localhost:8090'
const b=await chromium.launch(); let bad=0
const check=(n,ok,x='')=>{console.log(`${ok?'PASS':'FAIL'}  ${n} ${x}`); if(!ok)bad++}
async function login(u,opts={}){const c=await b.newContext({viewport:{width:1280,height:820},locale:'de-DE',...opts});const p=await c.newPage();p.on('pageerror',e=>{bad++;console.log('pageerror',e.message)});await p.goto(BASE+'/login');await p.locator('main input').first().fill(u);await p.locator('input[type=password]').fill('demo12345');await p.locator('button[type=submit]').click();await p.waitForURL('**/dashboard');return p}
const anna=await login('anna'),max=await login('max'),lisa=await login('lisa')
const hid=anna.url().match(/w\/([^/]+)/)[1]
for(const p of [anna,max,lisa]){await p.locator('aside select').selectOption('ru');await p.waitForTimeout(250)}
const bal=async p=>Object.fromEntries((await p.evaluate(async h=>(await fetch(`/api/households/${h}/balances`)).json(),hid)).balances.map(x=>[x.user.displayName,x.cents]))
const add=async(p,name,personal=false)=>{await p.getByRole('button',{name:'Добавить товар'}).click();const f=p.locator('dialog[open]');await f.getByLabel('Товар').fill(name);if(personal)await f.getByRole('button',{name:'Только для меня'}).click();await f.getByRole('button',{name:'Сохранить'}).click();await p.waitForTimeout(350)}
const start=await bal(anna); console.log('   start',start)
const diff=async()=>{const n=await bal(anna);return Object.fromEntries(Object.keys(n).map(k=>[k,n[k]-(start[k]||0)]))}
await anna.goto(`${BASE}/w/${hid}/shopping`); await max.goto(`${BASE}/w/${hid}/shopping`); await lisa.goto(`${BASE}/w/${hid}/expenses`); await anna.waitForTimeout(500)
// WG item
await add(anna,'Порошок')
await max.getByRole('checkbox',{name:/Порошок/}).click()
const d=max.locator('dialog[open]'); await d.getByLabel('Цена в €').waitFor()
await max.screenshot({path:'shots/b-dialog.png'})
await d.getByLabel('Цена в €').fill('abc'); await d.getByRole('button',{name:'Купил(а)'}).click(); await max.waitForTimeout(200)
check('bad price is rejected',await d.getByRole('alert').count()===1)
await d.getByLabel('Цена в €').fill('18,00')
await d.getByText('6,00').waitFor(); check('preview shows the split',await d.getByText(/с человека/).count()===1)
await max.screenshot({path:'shots/b-dialog-price.png'})
await d.getByRole('button',{name:'Купил(а)'}).click(); await max.waitForTimeout(600)
let x=await diff(); console.log('   WG',x)
check('WG purchase: all owe the buyer',x.Max===1200&&x.Anna===-600&&x.Lisa===-600)
await lisa.getByText('Порошок').waitFor({timeout:4000}); check('expense appears live for others',true)
// personal
await add(anna,'Шампунь',true)
await anna.screenshot({path:'shots/b-list.png'})
await max.getByRole('checkbox',{name:/Шампунь/}).click(); await d.getByLabel('Цена в €').fill('5')
await d.getByText(/будет должен/).waitFor(); check('personal preview names the requester',await d.getByText(/Anna/).count()>=1)
await d.getByRole('button',{name:'Купил(а)'}).click(); await max.waitForTimeout(600)
x=await diff(); console.log('   personal',x)
check('personal purchase: only requester owes',x.Max===1700&&x.Anna===-1100&&x.Lisa===-600)
// own personal: no dialog
await add(anna,'Крем',true)
await anna.getByRole('checkbox',{name:/Крем/}).click(); await anna.waitForTimeout(400)
check('own personal item: no question, no debt',await anna.locator('dialog[open]').count()===0&&(await diff()).Anna===-1100)
// no price
await add(anna,'Соль')
await max.getByRole('checkbox',{name:/Соль/}).click(); await d.getByRole('button',{name:'Купил(а)'}).click(); await max.waitForTimeout(500)
check('no price: bought, no debt',(await diff()).Max===1700)
// cancel: others cannot; buyer can
await lisa.goto(`${BASE}/w/${hid}/shopping`); await lisa.getByText(/Куплено \(/).click()
await lisa.getByRole('checkbox',{name:/Порошок/}).click(); await lisa.waitForTimeout(200)
await lisa.locator('dialog[open]').getByRole('button',{name:'Вернуть в список'}).click(); await lisa.getByText('нет на это прав').first().waitFor()
check('someone else cannot cancel a paid purchase',(await diff()).Max===1700)
await max.getByText(/Куплено \(/).click()
await max.getByRole('checkbox',{name:/Порошок/}).click()
await max.locator('dialog[open]').getByText('18,00').waitFor(); await max.screenshot({path:'shots/b-putback.png'})
await max.locator('dialog[open]').getByRole('button',{name:'Вернуть в список'}).click(); await max.waitForTimeout(600)
x=await diff(); check('buyer puts it back: debt gone',x.Max===500&&x.Anna===-500&&x.Lisa===0,JSON.stringify(x))
// dashboard
await max.goto(`${BASE}/w/${hid}/dashboard`); await max.waitForTimeout(500)
await max.locator('main').getByRole('checkbox',{name:/Порошок/}).click(); await d.getByLabel('Цена в €').waitFor()
check('dashboard asks for the price too',true); await max.keyboard.press('Escape'); await max.waitForTimeout(200)
check('cancel leaves it open',await max.locator('main').getByRole('checkbox',{name:/Порошок/}).count()===1)
// phone
const ph=await login('anna',{viewport:{width:320,height:700},hasTouch:true}); await ph.goto(`${BASE}/w/${hid}/shopping`); await ph.waitForTimeout(500); await ph.screenshot({path:'shots/b-phone.png'})
await ph.getByRole('checkbox',{name:/Порошок/}).click(); await ph.waitForTimeout(300); await ph.screenshot({path:'shots/b-phone-dialog.png'})
await b.close(); console.log(bad?`BAD ${bad}`:'ALL OK')
