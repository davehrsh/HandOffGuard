package dev.handoffguard;

import java.time.*;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;

/**
 * Cloud Run Job entry point. One execution examines recent, Flow-stamped
 * handoffs and optionally writes a non-PII JSON snapshot to Cloud Storage.
 * Scheduling is supplied by Cloud Scheduler (not by this JVM).
 */
public final class MonitoringJob {
  private MonitoringJob() {}
  public static void main(String[] args) throws Exception {
    Map<String,String> env=System.getenv();
    if(!"true".equalsIgnoreCase(env.getOrDefault("SF_FLOW_MARKERS_ENABLED","false"))||
       !"true".equalsIgnoreCase(env.getOrDefault("SF_LIVE_ENABLED","false")))
      throw new IllegalStateException("Salesforce live Flow collection must be explicitly enabled");
    int limit=Integer.parseInt(env.getOrDefault("MONITOR_MAX_SESSIONS","50"));
    if(limit<1||limit>100)throw new IllegalArgumentException("MONITOR_MAX_SESSIONS must be 1..100");
    Set<String> humans=new HashSet<>();
    for(String id:env.getOrDefault("SF_HUMAN_USER_IDS","").split(","))
      if(SalesforceId.valid(id.trim()))humans.add(id.trim());
    if(humans.isEmpty())throw new IllegalStateException("SF_HUMAN_USER_IDS is missing");
    Instant now=Instant.now();
    var result=new SalesforceCollector(env).monitorRecent(limit,humans,now);
    Map<String,Object> snapshot=new LinkedHashMap<>();
    snapshot.put("generatedAt",now.toString());
    snapshot.put("provenance","SALESFORCE_FLOW_MARKER_AND_AGENTWORK");
    snapshot.put("deadlineSeconds",60);
    snapshot.put("sessionsChecked",result.size());
    Map<String,Integer> counts=new TreeMap<>();
    for(var r:result)counts.merge(r.status().name(),1,Integer::sum);
    snapshot.put("counts",counts);
    snapshot.put("results",result);
    String json=Json.encode(snapshot);
    System.out.println(json); // Cloud Logging records without transcripts or Salesforce credentials
    // Structured Cloud Logging event for optional log-based alerts; no transcripts.
    int breached=counts.getOrDefault("TIMED_OUT",0)+counts.getOrDefault("ACCEPTED_LATE",0);
    System.out.println(Json.encode(Map.of(
      "severity",breached>0?"ERROR":"INFO",
      "event","HANDOFFGUARD_MONITOR_SUMMARY",
      "breached",breached,
      "sessionsChecked",result.size(),
      "generatedAt",now.toString())));
    String bucket=env.getOrDefault("MONITOR_GCS_BUCKET","");
    if(!bucket.isBlank()) {
      String object="snapshots/"+now.toString().replace(":","-")+".json";
      saveSnapshot(bucket,object,json);
      System.out.println(Json.encode(Map.of("savedTo","gs://"+bucket+"/"+object)));
    }
  }
  static void saveSnapshot(String bucket,String object,String body) throws Exception {
    if(!bucket.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{2,221}")||
        !object.matches("[a-zA-Z0-9/_:.+-]+"))
      throw new IllegalArgumentException("Invalid storage target");
    HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    URI metadata=URI.create("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token");
    HttpRequest tokenReq=HttpRequest.newBuilder(metadata).header("Metadata-Flavor","Google")
      .timeout(Duration.ofSeconds(10)).GET().build();
    HttpResponse<String> tokenRes=http.send(tokenReq,HttpResponse.BodyHandlers.ofString());
    if(tokenRes.statusCode()!=200)throw new IllegalStateException("Workload token unavailable");
    String token=JsonReader.string(JsonReader.object(JsonReader.parse(tokenRes.body())).get("access_token"));
    if(token==null||token.isBlank())throw new IllegalStateException("Workload token missing");
    String url="https://storage.googleapis.com/upload/storage/v1/b/"+URLEncoder.encode(bucket,StandardCharsets.UTF_8)+
      "/o?uploadType=media&name="+URLEncoder.encode(object,StandardCharsets.UTF_8)+"&ifGenerationMatch=0";
    HttpRequest req=HttpRequest.newBuilder(URI.create(url))
      .header("Authorization","Bearer "+token).header("Content-Type","application/json")
      .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofString(body)).build();
    HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());
    if(res.statusCode()!=200 && res.statusCode()!=201)
      throw new IllegalStateException("GCS snapshot write HTTP "+res.statusCode());
  }
}
