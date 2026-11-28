import {chromium} from 'playwright-core';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';
import assert from 'node:assert/strict';
const browser=await chromium.launch({channel:'chrome',args:['--no-sandbox']});
try {
 const p=await browser.newPage({viewport:{width:360,height:720},hasTouch:true});const errors=[];p.on('pageerror',e=>errors.push(e.message));
 await p.goto(pathToFileURL(resolve('mosswake/index.html')).href);
 await p.getByRole('button',{name:'Land on the island'}).tap();
 await p.keyboard.down('d');await p.waitForTimeout(250);await p.keyboard.up('d');assert(await p.evaluate(()=>s.x>0));
 // Place the ranger at each encounter, then use the same action control as a player.
 async function at(x,y,label){await p.evaluate(([x,y])=>{s.x=x;s.y=y;refresh()},[x,y]);await p.getByRole('button',{name:label,exact:true}).tap()}
 await at(24,57,'Befriend Fernkip');await at(-75,65,'Gather wood');await at(-137,-125,'Gather wood');await at(72,-7,'Gather stone');await at(130,-110,'Gather stone');await at(0,0,'Build shelter');
 await at(-55,5,'Pick berries');await at(-165,-82,'Befriend Pebblit');await at(119,43,'Pick berries');await at(204,88,'Befriend Glimmoth');
 await at(252,-138,'Challenge guardian');
 for(const label of ['Strike','Strike','Guard','Strike','Strike','Guard','Strike','Strike'])await p.getByRole('button',{name:label,exact:true}).tap();
 await p.getByRole('button',{name:'Return to island'}).tap();await p.reload();await p.getByRole('button',{name:'Continue adventure'}).tap();
 assert(await p.evaluate(()=>s.lit&&s.camp&&s.friends.length===3));await p.getByRole('button',{name:'Field journal'}).tap();await p.getByRole('button',{name:'Travel with Fernkip'}).tap();
 assert.equal(await p.evaluate(()=>s.active),0);await p.setViewportSize({width:740,height:360});await p.locator('#act').waitFor();assert.deepEqual(errors,[]);console.log('PASS: start, movement, all three companions, gathering, building, guardian, reload and companion selection.');
}finally{await browser.close()}
