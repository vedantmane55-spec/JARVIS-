import { chromium } from 'playwright-core';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
const tmp=mkdtempSync(join(tmpdir(),'drop-test-')),url='http://127.0.0.1:3000';
const server=spawn(process.execPath,['server.mjs'],{env:{...process.env,DROP_DATABASE:join(tmp,'users.sqlite')},stdio:'inherit'});
let browser;
try {
 for(let i=0;i<60;i++){try{if((await fetch(url)).ok)break}catch{}await new Promise(r=>setTimeout(r,250))}
 browser=await chromium.launch({channel:'chrome',args:['--no-sandbox','--use-fake-device-for-media-stream','--use-fake-ui-for-media-stream']});
 const device={viewport:{width:360,height:740},hasTouch:true,isMobile:true},context=await browser.newContext(device),p=await context.newPage();
 const faults=[];p.on('pageerror',e=>faults.push(e.message));await p.goto(url);
 await p.locator('#name').fill('vedyya');await p.getByRole('button',{name:'Enter lobby'}).tap();
 await p.getByRole('button',{name:'Account',exact:true}).tap();await p.locator('#userId').fill('radio_scout');await p.locator('#pass').fill('example-test-passphrase');
 await p.getByRole('button',{name:'Register',exact:true}).tap();await p.getByRole('button',{name:'Start match'}).tap();
 await p.getByRole('button',{name:'Jump',exact:true}).tap();await p.waitForFunction(()=>dropTime===0,{},{timeout:20000});
 await p.getByRole('button',{name:'Bag',exact:true}).tap();await p.getByRole('button',{name:'Sprint',exact:true}).tap();
 assert(await p.evaluate(()=>player.kit.sprint));await p.getByRole('button',{name:'Auto',exact:true}).tap();
 const prior=await p.evaluate(()=>yaw);await p.mouse.move(152,280);await p.mouse.down();await p.mouse.move(245,295);await p.mouse.up();assert.notEqual(await p.evaluate(()=>yaw),prior);
 await p.locator('#pause').tap();await p.getByRole('button',{name:'Resume'}).tap();await p.evaluate(()=>{for(const a of actors)if(!a.kit)a.hp=0});await p.waitForFunction(()=>screen==='result');
 await p.getByRole('button',{name:'Lobby',exact:true}).tap();await p.evaluate(()=>accountQueue);
 const second=await browser.newContext(device),q=await second.newPage();await q.goto(url);await q.locator('#name').fill('Guest');await q.getByRole('button',{name:'Enter lobby'}).tap();
 await q.getByRole('button',{name:'Account',exact:true}).tap();await q.locator('#userId').fill('radio_scout');await q.locator('#pass').fill('example-test-passphrase');await q.getByRole('button',{name:'Sign in',exact:true}).tap();await q.waitForFunction(()=>screen==='lobby'&&profile.wins===1);
 assert.match(await q.locator('#record').innerText(),/vedyya/i);assert.deepEqual(faults,[]);
 console.log('PASS: mobile browser drop, canvas, touch UI, camera, equipment, finish, cloud registration and cross-browser login with restored record.');
} finally {if(browser)await browser.close();server.kill()}
