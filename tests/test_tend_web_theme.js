const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const test = require('node:test');
const html = fs.readFileSync(`${__dirname}/../desk/app/tend.html`, 'utf8');
const code = html.slice(html.indexOf('// BEGIN SHIP APPEARANCE'), html.indexOf('// END SHIP APPEARANCE'));
const appearance = vm.runInNewContext(`${code}\nTendAppearance`, {window:{addEventListener(){}}});
const colors = {mode:'light', background:'#FAFAF5', foreground:'#202024', accent:'#366644'};
const profile = value => ({desk:{appearance:{current:JSON.stringify(value)}}});
test('full profile consumes colors, ignores executable-looking unrelated fields', () => {
  const theme = appearance.profile(profile({schemaVersion:1,theme:'Test',colors,font:'ignored',files:{css:'ignored'}}));
  assert.equal(theme.colors.background,'#fafaf5');
  assert.equal(theme.colors.mode,'light');
  assert.ok(!theme.colors.font);
  assert.ok(parseInt(theme.colors.dark_background.slice(1,3),16) > 200);
  assert.equal(appearance.css(theme.colors)['accent-text'],'#ffffff');
});
test('missing optional and invalid colors derive safely without CSS injection', () => {
  const theme = appearance.palette({...colors,selection:'url(https://invalid)',muted:{x:1}});
  for (const [key,value] of Object.entries(theme)) if (key !== 'mode') assert.match(value,/^#[a-f0-9]{6}$/);
});
test('malformed and unknown schema are errors, not absent themes', () => {
  assert.equal(appearance.profile({desk:{}}),null);
  for (const invalid of [[],{schemaVersion:2,colors},{schemaVersion:1,colors:{...colors,mode:'auto'}},{schemaVersion:1,colors:{...colors,background:'red'}}]) assert.throws(() => appearance.profile(profile(invalid)));
  assert.throws(() => appearance.profile({desk:{appearance:{current:'bad json'}}}));
});
test('stable Omarchy bridge works independent of activeId and old optional Auto values', () => {
  const theme = appearance.bridge({desk:{'ui-prefs':{themes:JSON.stringify({activeId:'another',themes:[{id:'omarchy-urbit-theme',name:'Bridge',dark:false,background:'#fafaf5',primary:'#366644',surface:'#eeeeea',text:'',muted:''}]})}}});
  assert.equal(theme.source,'Talon bridge'); assert.equal(theme.colors.mode,'light');
  assert.equal(theme.colors.dark_background,'#eeeeea'); assert.equal(theme.colors.foreground,'#202024');
});
test('bounded profiles and semantic selection ink', () => {
  assert.throws(() => appearance.profile(profile({schemaVersion:1,colors,extra:'a'.repeat(262144)})));
  assert.equal(appearance.css(appearance.palette({...colors,selection:'#ffffff'}))['selection-text'],'#000000');
});
