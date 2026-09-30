// Opt-in fake-ship QA. Frontend route override; no ship deployment or Gall source changes.
// PLAYWRIGHT_MODULE=/path/to/playwright node tests/tend_theme_live.mjs
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const output=path.resolve('mobile/artifacts/themes/web');
await fs.mkdir(output,{recursive:true});
const html=await fs.readFile('desk/app/tend.html','utf8');
const palettes={
  dark:{mode:'dark',background:'#141c19',dark_background:'#0f1512',darker_background:'#0a0f0d',lighter_background:'#202b26',foreground:'#d2dfd5',dark_foreground:'#809a87',light_foreground:'#e0e9e2',bright_foreground:'#f0f6f1',accent:'#94c8a6',selection:'#344d3f',muted:'#809a87',red:'#e88986',green:'#97c78b',blue:'#83aed2'},
  light:{mode:'light',background:'#faf5e9',dark_background:'#f3eddf',darker_background:'#eee6d5',lighter_background:'#fffcf4',foreground:'#302d27',dark_foreground:'#6f695b',light_foreground:'#26231e',bright_foreground:'#171510',accent:'#426950',selection:'#d4e2c9',muted:'#716956',red:'#ae4037',green:'#466e39',blue:'#305e91'},
};
const profile=mode=>({schemaVersion:1,theme:`QA ${mode}`,updateId:mode==='dark'?'f'.repeat(32):'0'.repeat(32),deviceId:'a'.repeat(32),colors:palettes[mode]});
const slots=[['omarchy-urbit-theme','appearance','current'],['talon','ui-prefs','themes']];
async function connect(port,loop,ship) {
  const url=`http://127.0.0.1:${port}`;
  const code=await (await fetch(`http://127.0.0.1:${loop}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({source:{dojo:'+code'},sink:{stdout:null}})})).json();
  const login=await fetch(`${url}/~/login`,{method:'POST',body:new URLSearchParams({password:code.trim().replace(/^~/,'')})});
  const cookies=login.headers.getSetCookie().map(c=>c.split(';')[0]);
  assert.ok(cookies.some(c=>c.startsWith('urbauth-')),'Missing fake-ship session');
  const headers={cookie:cookies.join('; '),'Content-Type':'application/json'};
  const read=async desk=> {const r=await fetch(`${url}/~/scry/settings/desk/${desk}.json`,{headers}); assert.equal(r.status,200); return r.json();};
  const saved=[];
  for(const [desk,bucket,key] of slots) {const body=await read(desk);saved.push((body.desk||body)[bucket]?.[key] ?? null);}
  async function put(index,value) {
    const [desk,bucket,key]=slots[index], channel=`${url}/~/channel/theme-qa-${crypto.randomUUID()}`;
    const data={desk,'bucket-key':bucket,'entry-key':key};
    if(value!=null) data.value=value;
    const commands=async values=> {const r=await fetch(channel,{method:'PUT',headers,body:JSON.stringify(values)});assert.equal(r.status,204);};
    const abort=new AbortController(), timeout=setTimeout(()=>abort.abort(),15000);
    try {
      await commands([{id:1,action:'poke',ship,app:'settings',mark:'settings-event',json:{[value==null?'del-entry':'put-entry']:data}}]);
      const r=await fetch(channel,{headers,signal:abort.signal}); assert.equal(r.status,200);
      let buffer=''; const reader=r.body.getReader();
      for (;;) {
        const {done,value}=await reader.read(); if(done) throw Error('No settings acknowledgement');
        buffer+=new TextDecoder().decode(value);
        const blocks=buffer.split('\n\n'); buffer=blocks.pop();
        for(const block of blocks) {
          const line=block.split('\n').find(l=>l.startsWith('data:')); if(!line)continue;
          const m=JSON.parse(line.slice(5));
          if(m.response==='poke' && m.id===1) {assert.ok(!m.err,'Settings rejected the QA write'); return;}
        }
      }
    } finally {clearTimeout(timeout);abort.abort();await commands([{id:2,action:'delete'}]);}
  }
  return {url,cookies,read,put,restore:async()=>{for(let i=0;i<slots.length;i++)await put(i,saved[i]);}};
}
const ships=[];
let browser;
try {
  ships.push(await connect(8082,12324,'wex'),await connect(8081,12323,'rus'));
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_EXECUTABLE});
  const contexts=[];
  for(const [i,ship] of ships.entries()) {
    const context=await browser.newContext({viewport:{width:1440,height:960}});
    await context.addCookies(ship.cookies.map(c=>({name:c.slice(0,c.indexOf('=')),value:c.slice(c.indexOf('=')+1),url:ship.url})));
    const page=await context.newPage();
    page.on('pageerror',error=>{throw error;});
    await page.goto(`${ship.url}/apps/tend`);
    await page.locator('#identity').filter({hasText:i?'~rus':'~wex'}).waitFor();
    await page.screenshot({path:`${output}/${i}-before.png`});
    await page.route('**/apps/tend',route=>route.fulfill({status:200,contentType:'text/html',body:html}));
    await page.reload();
    await page.locator('#identity').filter({hasText:i?'~rus':'~wex'}).waitFor();
    contexts.push({context,page});
  }
  const [{page,context},{page:peer}]=contexts;
  const bg=p=>p.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--bg').trim());
  const waitBg=(p,color)=>p.waitForFunction(c=>getComputedStyle(document.documentElement).getPropertyValue('--bg').trim()===c,color,{timeout:15000});
  await ships[0].put(0,JSON.stringify(profile('dark')));
  await waitBg(page,palettes.dark.background);
  assert.equal(await bg(peer),'#0c0c0d','Ship themes must remain isolated');
  await page.locator('#new-reminder-title').fill('Keep this unfinished draft');
  await ships[0].put(0,JSON.stringify(profile('light')));
  await waitBg(page,palettes.light.background);
  assert.equal(await page.locator('#new-reminder-title').inputValue(),'Keep this unfinished draft');
  assert.equal(await page.evaluate(()=>document.activeElement.id),'new-reminder-title');
  for(const mode of ['light','dark']) {
    await ships[0].put(0,JSON.stringify(profile(mode))); await waitBg(page,palettes[mode].background);
    for(const width of [320,640,768,1024,1280,1440,1536]) for(const height of [568,960]) {
      await page.setViewportSize({width,height});
      await page.waitForTimeout(200); // CSS hover/color transitions must settle for visual evidence.
      await page.screenshot({path:`${output}/${mode}-${width}x${height}.png`});
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Horizontal viewport overflow');
    }
  }
  await ships[0].put(0,'{"schemaVersion":9}');
  await page.waitForTimeout(800);
  assert.equal(await bg(page),palettes.dark.background,'Invalid profile must retain last good');
  await context.route('**/~/scry/settings/**',route=>route.abort());
  await page.reload(); await waitBg(page,palettes.dark.background);
  await context.unroute('**/~/scry/settings/**');
  await ships[0].put(0,JSON.stringify(profile('light'))); await waitBg(page,palettes.light.background);
  await page.setViewportSize({width:1440,height:960});
  await page.locator('#open-settings').click();
  await page.locator('.appearance-mode-shell .select-trigger').click();
  await page.getByRole('option',{name:'Tend default',exact:true}).click();
  await waitBg(page,'#0c0c0d');
  await page.reload(); await page.locator('#identity').filter({hasText:'~wex'}).waitFor(); await waitBg(page,'#0c0c0d');
  await page.locator('#open-settings').click();
  await page.locator('.appearance-mode-shell .select-trigger').click();
  await page.getByRole('option',{name:'Follow ship theme',exact:true}).click();
  await waitBg(page,palettes.light.background);
  await page.waitForTimeout(200);
  await page.screenshot({path:`${output}/appearance-settings-light.png`});
  await page.locator('[data-close-settings]').click();
  await page.locator('[data-open-detail]').first().click();
  await page.waitForTimeout(200);
  await page.screenshot({path:`${output}/detail-light.png`});
  await page.keyboard.press('Escape');
  await page.locator('[data-date-trigger]').first().click();
  await page.screenshot({path:`${output}/calendar-light.png`});
  await page.keyboard.press('Escape');
  const bridge={activeId:'something-else',themes:[{id:'omarchy-urbit-theme',name:'Bridge QA',dark:false,primary:'#426950',background:'#faf5e9',surface:'#f3eddf',text:'#302d27',muted:'#716956'}]};
  await ships[1].put(1,JSON.stringify(bridge)); await waitBg(peer,'#faf5e9');
  await peer.screenshot({path:`${output}/peer-bridge-light.png`});
  await ships[0].put(1,JSON.stringify(bridge)); await ships[0].put(0,null);
  await page.waitForFunction(()=>TendAppearance.label().includes('Talon bridge'));
  await ships[0].put(1,null); await waitBg(page,'#0c0c0d');
  console.log('PASS: real settings watches, dark/light, opaque updateIds, draft preservation, invalid/cache fallback, local opt-out, bridge priority, deletion, ship isolation, 28 viewport screenshots.');
} finally {
  for(const ship of ships) await ship.restore();
  await browser?.close();
  console.log('Original fake-ship appearance entries restored.');
}
