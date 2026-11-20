'use strict';
let run=null;
const $=id=>document.getElementById(id);
function element(tag,text,cls){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e;}
async function execute(profile){
  $('broken').disabled=$('fixed').disabled=true;$('export').disabled=true;
  $('status').textContent='Executing simulator and validating receiving-side evidence…';
  try{
    const res=await fetch('/api/run?profile='+profile,{method:'POST'});
    if(!res.ok)throw new Error('Run failed ('+res.status+')');
    run=await res.json();
    $('profile').textContent=profile==='broken'?'Injected faults':'Corrected configuration';
    $('passed').textContent=run.results.flatMap(r=>r.checks).filter(c=>c.passed).length;
    $('passed-sub').textContent='Of '+run.results.flatMap(r=>r.checks).length+' deterministic assertions';
    $('failed').textContent=run.failed;$('failed').style.color=run.failed?'#b3403c':'#17634d';
    const times=run.results.map(r=>r.handoffMs).filter(t=>t>=0).sort((a,b)=>a-b);
    $('latency').textContent=times.length?times[Math.ceil(times.length*.95)-1]+' ms':'—';
    $('status').textContent=run.passed+'/6 scenarios passed · '+run.source+' · '+new Date(run.createdAt).toLocaleTimeString();
    $('scenarios').replaceChildren();
    run.results.forEach((r,i)=>{const b=element('button',undefined,'scenario');const row=element('div',undefined,'scenario-top');row.append(element('span',r.title,'scenario-title'),element('span',r.status,'pill '+r.status.toLowerCase()));b.append(row,element('small',r.description));b.addEventListener('click',()=>select(i));$('scenarios').append(b);});
    select(0);$('export').disabled=false;
  }catch(err){$('status').textContent=err.message+'. Check that the Java server is running.';}
  finally{$('broken').disabled=$('fixed').disabled=false;}
}
function select(i){
  const r=run.results[i];[...$('scenarios').children].forEach((b,n)=>{b.classList.toggle('selected',i===n);b.setAttribute('aria-pressed',String(i===n));});
  $('verdict').textContent=r.status+' · SIMULATOR';
  const root=$('detail');root.replaceChildren();
  const head=element('div',undefined,'detail-head');head.append(element('h3',r.title),element('p',r.description));root.append(head,element('div','ASSERTIONS / '+r.checks.length,'subheading'));
  const checks=element('div',undefined,'checks');
  r.checks.forEach(c=>{const row=element('div',undefined,'check '+(c.passed?'':'bad'));row.append(element('span',c.passed?'✓':'×','check-symbol'));const body=element('div');body.append(element('b',c.name),element('small','Expected: '+c.expected),element('small','Observed: '+c.actual));row.append(body);checks.append(row);});
  root.append(checks,element('div','EVENT TIMELINE / SYNTHETIC TIMESTAMPS','subheading'));
  const timeline=element('div',undefined,'timeline');
  r.events.forEach(e=>{const row=element('div',undefined,'event');const body=element('div',undefined,'event-body');body.append(element('b',e.type.replaceAll('_',' ')),element('p',Object.entries(e.data).filter(([k])=>!['requestId','transferId'].includes(k)).map(([k,v])=>k+': '+v).join(' · ')));row.append(element('time','+'+e.atMs+'ms'),body);timeline.append(row);});root.append(timeline);
}
$('broken').addEventListener('click',()=>execute('broken'));$('fixed').addEventListener('click',()=>execute('fixed'));
$('export').addEventListener('click',()=>{if(!run)return;const url=URL.createObjectURL(new Blob([JSON.stringify(run,null,2)],{type:'application/json'}));const a=element('a');a.href=url;a.download='handoffguard-'+run.profile+'-'+run.id+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});

async function loadSalesforceEvidence(){
 const target=$('salesforce-sessions');
 try{
  const response=await fetch('/api/salesforce/evidence',{cache:'no-store'});
  if(!response.ok){
   let message='Verified monitoring evidence is unavailable';
   try{const err=await response.json();if(err.error)message=err.error;}catch(_){}
   throw Error(message);
  }
  const data=await response.json();
  if(data.source!=='PRIVATE_GCS_MONITORING_SNAPSHOT'||data.provenance!=='SALESFORCE_FLOW_MARKER_AND_AGENTWORK'||!Array.isArray(data.results))
    throw Error('Unexpected evidence source');
  target.replaceChildren();
  const generated=new Date(data.generatedAt);
  if(!Number.isFinite(generated.getTime()))throw Error('Invalid monitoring timestamp');
  target.append(element('p','Snapshot recorded: '+generated.toLocaleString()+' · '+data.results.length+' assessed handoffs · 60-second acceptance SLA','evidence-meta'));
  if(data.results.length===0){target.append(element('p','The latest monitoring snapshot contains no assessed handoffs.','evidence-empty'));return;}
  const allowed=new Set(['ACCEPTED_ON_TIME','ACCEPTED_LATE','PENDING','TIMED_OUT','INSUFFICIENT_EVIDENCE']);
  for(const [i,session] of data.results.entries()){
   if(!allowed.has(session.status))throw Error('Invalid assessment result');
   const card=element('div',undefined,'check');
   const info=element('div');
   info.append(element('b','Assessment '+(i+1)+' · '+session.status.replaceAll('_',' ')));
   if(typeof session.latencySeconds==='number'&&Number.isFinite(session.latencySeconds))
     info.append(element('small','Verified human acceptance latency: '+session.latencySeconds+' seconds'));
   else if(session.status==='TIMED_OUT')
     info.append(element('small','No permitted human acceptance observed by the deadline'));
   else if(session.status==='INSUFFICIENT_EVIDENCE')
     info.append(element('small','Evidence incomplete; no SLA determination made'));
   card.append(info);target.append(card);
  }
  target.append(element('small','Source: Salesforce Flow + AgentWork, read from private Cloud Storage. Identifiers omitted.','evidence-meta'));
 }catch(err){
  target.replaceChildren();
  target.append(element('p',err.message+'. No simulated or cached results substituted.','evidence-empty'));
 }
}
loadSalesforceEvidence();
