import { chromium } from 'playwright-core';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { readFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
const tmp=mkdtempSync(join(tmpdir(),'drop-test-')),url='http://127.0.0.1:3000';
const server=spawn(process.execPath,['server.mjs'],{env:{...process.env,DROP_DATABASE:join(tmp,'users.sqlite')},stdio:'inherit'});
let browser,signals;
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
 signals=spawn(process.execPath,['--input-type=module','-e',"import {PeerServer} from 'peer';PeerServer({port:9100,path:'/drop',host:'127.0.0.1'});"],{stdio:'inherit'});for(let i=0;i<60;i++){try{await fetch('http://127.0.0.1:9100');break}catch{}await new Promise(r=>setTimeout(r,200))}
 const peerScript=readFileSync('node_modules/peerjs/dist/peerjs.min.js','utf8');
 for(const tab of [p,q]){
   await tab.route('https://cdn.jsdelivr.net/npm/peerjs@1.5.5/dist/peerjs.min.js',route=>route.fulfill({contentType:'application/javascript',body:peerScript+";window.Peer=class extends window.Peer{constructor(id){super(id,{host:'127.0.0.1',port:9100,path:'/drop',secure:false,config:{iceServers:[]}})}};"}));
   await tab.context().grantPermissions(['microphone'],{origin:url});
   await tab.getByRole('button',{name:'Online squad'}).tap();
 }
 await p.getByRole('button',{name:'Invite a friend'}).tap();
 await p.waitForFunction(()=>document.querySelector('#desc').textContent.includes('Share this squad code'));
 const code=(await p.locator('#desc').innerText()).split(': ').pop();
 q.once('dialog',d=>d.accept(code));await q.getByRole('button',{name:'Join with code'}).tap();
 await q.waitForFunction(()=>screen==='match'&&net.role==='guest');await p.waitForFunction(()=>screen==='match'&&net.role==='host');
 await q.getByRole('button',{name:'Bag',exact:true}).tap();await q.getByRole('button',{name:'Crouch',exact:true}).tap();
 await p.waitForFunction(()=>actors[1].kit.crouch);await q.waitForFunction(()=>player.kit.crouch);
 await q.locator('#micBtn').tap();await p.waitForFunction(()=>net.audio?.srcObject?.getAudioTracks().length===1);
 await q.locator('#micBtn').tap();await q.waitForFunction(()=>!net.stream);await q.evaluate(()=>leaveRoom());await p.waitForFunction(()=>screen==='room');
 assert.deepEqual(faults,[]);
 console.log('PASS: real WebRTC data channel, guest controls, host snapshots, received synthetic microphone track, mic off and clean disconnect over local signaling.');
 const offline=await browser.newContext(device),local=await offline.newPage();await local.route('**/api/**',route=>route.fulfill({status:404,contentType:'text/plain',body:'offline'}));await local.goto(url);
 await local.locator('#name').fill('Device Scout');await local.getByRole('button',{name:'Enter lobby'}).tap();await local.getByRole('button',{name:'Account',exact:true}).tap();await local.locator('#userId').fill('local_scout');await local.locator('#pass').fill('local-passphrase-123');await local.getByRole('button',{name:'Register',exact:true}).tap();await local.waitForFunction(()=>accountUser==='local_scout'&&screen==='lobby');
 await local.reload();await local.waitForFunction(()=>accountUser==='local_scout');await local.getByRole('button',{name:'Account',exact:true}).tap();await local.getByRole('button',{name:'Sign out'}).tap();await local.locator('#name').fill('Guest');await local.getByRole('button',{name:'Enter lobby'}).tap();await local.getByRole('button',{name:'Account',exact:true}).tap();await local.locator('#userId').fill('local_scout');await local.locator('#pass').fill('local-passphrase-123');await local.getByRole('button',{name:'Sign in',exact:true}).tap();await local.waitForFunction(()=>screen==='lobby'&&profile.name==='Device Scout');console.log('PASS: standalone device registration, session restore, logout and password sign-in.');
 console.log('PASS: mobile browser drop, canvas, touch UI, camera, equipment, finish, cloud registration and cross-browser login with restored record.');
} finally {if(browser)await browser.close();server.kill();signals?.kill()}
