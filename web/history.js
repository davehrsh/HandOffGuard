"use strict";
const picker=document.getElementById("snapshot");
const status=document.getElementById("file-status");
const metrics=document.getElementById("metrics");
const rows=document.getElementById("results");
const statuses=new Set(["ACCEPTED_ON_TIME","ACCEPTED_LATE","PENDING","TIMED_OUT","INSUFFICIENT_EVIDENCE"]);
function cell(tr,value){const td=document.createElement("td");td.textContent=value==null?"—":String(value);tr.append(td);return td;}
picker.addEventListener("change",async()=>{
  rows.replaceChildren();metrics.textContent="";status.textContent="Reading snapshot…";
  try{
    const file=picker.files?.[0];if(!file){status.textContent="No snapshot loaded.";return;}
    if(file.size>2_000_000)throw Error("Snapshot exceeds 2 MB limit");
    const raw=JSON.parse(await file.text());
    if(!raw||!Array.isArray(raw.results)||typeof raw.generatedAt!=="string")throw Error("Invalid HandoffGuard snapshot");
    if(raw.results.length>100)throw Error("Too many results in snapshot");
    let timeout=0,accepted=0,pending=0,missing=0;
    for(const r of raw.results){
      if(!r||!statuses.has(r.status))throw Error("Unknown or malformed status");
      if(r.status==="TIMED_OUT")timeout++;
      else if(r.status.startsWith("ACCEPTED"))accepted++;
      else if(r.status==="PENDING")pending++;
      else missing++;
      const tr=document.createElement("tr");
      cell(tr,r.sessionId);
      cell(tr,r.transferStartedAt);
      const tag=cell(tr,r.status);tag.className=r.status==="TIMED_OUT"?"warn":r.status==="ACCEPTED_ON_TIME"?"ok":"";
      cell(tr,r.latencySeconds==null?"—":r.latencySeconds+" s");
      cell(tr,r.provenance);
      rows.append(tr);
    }
    metrics.textContent=`Sessions: ${raw.results.length}  ·  Accepted: ${accepted}  ·  Timed out: ${timeout}  ·  Pending: ${pending}  ·  Insufficient: ${missing}`;
    status.textContent="Snapshot generated "+raw.generatedAt+". Local-only analysis. No file uploaded.";
  }catch(e){rows.replaceChildren();status.textContent="Cannot read snapshot: "+e.message;}
});
