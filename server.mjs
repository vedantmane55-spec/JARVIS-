// Run with Node 22.13+ behind an HTTPS reverse proxy. Bind only to loopback by default.
import http from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { randomBytes, scrypt, timingSafeEqual, createHash } from 'node:crypto';
import { promisify } from 'node:util';
import { readFileSync } from 'node:fs';
const derive=promisify(scrypt),db=new DatabaseSync(process.env.DROP_DATABASE||'vedyya.sqlite');
db.exec('PRAGMA journal_mode=WAL; CREATE TABLE IF NOT EXISTS accounts (id TEXT PRIMARY KEY, salt TEXT, hash TEXT, profile TEXT); CREATE TABLE IF NOT EXISTS sessions (key TEXT PRIMARY KEY, id TEXT, expires INTEGER)');
const html=readFileSync(new URL('./index.html',import.meta.url)),limits=new Map();
const clean=p=>({name:String(p?.name||'Scout').trim().slice(0,18)||'Scout',uniform:[0,1,2].includes(p?.uniform)?p.uniform:0,model:p?.model==='Ranger'?'Ranger':'Scout',wins:Math.max(0,Math.min(1000000,Number(p?.wins)|0)),best:Math.max(0,Math.min(11,Number(p?.best)|0))});
function digest(k){return createHash('sha256').update(k).digest('hex')}
const server=http.createServer(async(req,res)=>{
 const reply=(status,b)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(b))};
 if(req.method==='GET'&&['/','/index.html'].includes(req.url)){res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});res.end(html);return}
 if(req.method!=='POST'||!req.url.startsWith('/api/'))return reply(404,{error:'Not found'});
 if(req.headers['sec-fetch-site']==='cross-site'||req.headers['content-type']!=='application/json')return reply(403,{error:'Origin rejected'});
 try{
 let body='';for await(const piece of req){body+=piece;if(body.length>4096)return reply(413,{error:'Body too large'})}
 let data;try{data=JSON.parse(body)}catch{return reply(400,{error:'Invalid JSON'})}
 const key=digest((req.headers.cookie||'').match(/(?:^|; *)drop_session=([a-f0-9]{64})/)?.[1]||''),now=Date.now();
 const session=db.prepare('SELECT a.* FROM accounts a JOIN sessions s ON s.id=a.id WHERE s.key=? AND s.expires>?').get(key,now);
 if(req.url==='/api/session')return reply(200,session?{user:session.id,profile:JSON.parse(session.profile)}:{user:null});
 if(req.url==='/api/logout'){db.prepare('DELETE FROM sessions WHERE key=?').run(key);res.setHeader('Set-Cookie','drop_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0');return reply(200,{})}
 if(req.url==='/api/record'){
 if(!session)return reply(401,{error:'Sign in again'});const p=clean({...JSON.parse(session.profile),...data}),old=JSON.parse(session.profile);p.best=Math.max(old.best,p.best);p.wins=Math.max(old.wins,p.wins);db.prepare('UPDATE accounts SET profile=? WHERE id=?').run(JSON.stringify(p),session.id);return reply(200,{})}
 if(!['/api/login','/api/register'].includes(req.url))return reply(404,{error:'Not found'});
 const id=String(data.user||'').toLowerCase(),pass=data.pass;
 if(!/^[a-z0-9_]{3,24}$/.test(id)||typeof pass!=='string'||pass.length<10||pass.length>256)return reply(400,{error:'Invalid username or password length'});
 const ip=req.socket.remoteAddress,last=limits.get(ip)||{since:now,n:0};if(now-last.since>60000){last.n=0;last.since=now}limits.set(ip,last);if(++last.n>20)return reply(429,{error:'Try again in a minute'});
 let user=db.prepare('SELECT * FROM accounts WHERE id=?').get(id);
 if(req.url==='/api/register'){if(user)return reply(409,{error:'Username unavailable'});const salt=randomBytes(16).toString('hex'),hash=(await derive(pass,salt,32)).toString('hex'),profile=JSON.stringify(clean(data.profile));const inserted=db.prepare('INSERT OR IGNORE INTO accounts VALUES (?,?,?,?)').run(id,salt,hash,profile);if(!inserted.changes)return reply(409,{error:'Username unavailable'});user={id,profile}}
 else{const h=await derive(pass,user?.salt||'missing-user-salt',32);if(!user||!timingSafeEqual(Buffer.from(user.hash,'hex'),h))return reply(401,{error:'Incorrect login'})}
 const token=randomBytes(32).toString('hex');db.prepare('DELETE FROM sessions WHERE expires<=?').run(now);db.prepare('INSERT INTO sessions VALUES (?,?,?)').run(digest(token),id,now+604800000);
 res.setHeader('Set-Cookie','drop_session='+token+'; HttpOnly; SameSite=Strict; Path=/; Max-Age=604800'+(process.env.DROP_HTTPS==='1'?'; Secure':''));
 return reply(200,{user:id,profile:JSON.parse(user.profile)})
 }catch{return reply(500,{error:'Request failed'})}
});
server.listen(Number(process.env.PORT||3000),process.env.HOST||'127.0.0.1');
