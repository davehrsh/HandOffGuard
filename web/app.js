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
 try{
  const response=await fetch('/api/salesforce/evidence');if(!response.ok)throw Error('Evidence unavailable');
  const data=await response.json(), target=$('salesforce-sessions');target.replaceChildren();
  for(const session of data.sessions){
   const card=element('div',undefined,'check');const info=element('div');
   info.append(element('b',session.name+' · '+session.assessment.replaceAll('_',' ')),element('small','Session ID: '+session.sessionId));
   if(session.requestToAcceptSeconds!==undefined)info.append(element('small','Human AgentWork request → acceptance: '+session.requestToAcceptSeconds+' seconds (not transfer-start SLA)'));
   if(session.transferStartedAt===null)info.append(element('small','Exact transfer start: not captured'));
   card.append(info);target.append(card);
  }
 }catch(err){$('salesforce-sessions').textContent='Recorded Salesforce evidence unavailable: '+err.message;}
}
loadSalesforceEvidence();
