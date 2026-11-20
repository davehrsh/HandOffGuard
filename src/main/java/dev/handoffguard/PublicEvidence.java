package dev.handoffguard;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Server-side, read-only bridge from private GCS monitoring snapshots to the public
 * evidence panel. Never returns Salesforce record IDs, names, credentials or transcripts.
 * Does NOT perform a live Salesforce query or invent demonstration outcomes.
 */
public final class PublicEvidence {
  private PublicEvidence() {}
  private static final String PROVENANCE="SALESFORCE_FLOW_MARKER_AND_AGENTWORK";
  private static final Set<String> STATES=Set.of(
      "ACCEPTED_ON_TIME","ACCEPTED_LATE","PENDING","TIMED_OUT","INSUFFICIENT_EVIDENCE");

  public static Map<String,Object> fromSnapshot(String json) {
    Map<String,Object> source=JsonReader.object(JsonReader.parse(json));
    if(!PROVENANCE.equals(source.get("provenance")))
      throw new IllegalArgumentException("Unverified snapshot provenance");
    String generated=JsonReader.string(source.get("generatedAt"));
    if(generated==null || generated.isBlank())throw new IllegalArgumentException("Missing snapshot timestamp");
    List<Object> raw=JsonReader.array(source.get("results"));
    List<Map<String,Object>> results=new ArrayList<>();
    for(Object item:raw){
      Map<String,Object> r=JsonReader.object(item);
      String status=JsonReader.string(r.get("status"));
      if(!STATES.contains(status))throw new IllegalArgumentException("Unknown assessment status");
      Map<String,Object> safe=new LinkedHashMap<>();
      safe.put("status",status);
      // Only publish verifiable SLA latency, never raw Salesforce IDs.
      Object seconds=r.get("latencySeconds");
      if(seconds!=null && (!(seconds instanceof Number) || ((Number)seconds).doubleValue()<0))
        throw new IllegalArgumentException("Invalid acceptance latency");
      safe.put("latencySeconds",seconds);
      results.add(safe);
    }
    Map<String,Object> output=new LinkedHashMap<>();
    output.put("source","PRIVATE_GCS_MONITORING_SNAPSHOT");
    output.put("provenance",PROVENANCE);
    output.put("generatedAt",generated);
    output.put("deadlineSeconds",60);
    output.put("sessionsChecked",results.size());
    output.put("results",results);
    return output;
  }

  public static Map<String,Object> latest(Map<String,String> env) throws Exception {
    String bucket=env.getOrDefault("MONITOR_GCS_BUCKET","");
    if(!bucket.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{2,221}"))
      throw new IllegalStateException("Snapshot bucket not configured");
    HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    HttpRequest tokenRequest=HttpRequest.newBuilder(
        URI.create("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"))
        .header("Metadata-Flavor","Google").timeout(Duration.ofSeconds(10)).GET().build();
    HttpResponse<String> tokenResponse=http.send(tokenRequest,HttpResponse.BodyHandlers.ofString());
    if(tokenResponse.statusCode()!=200)throw new IllegalStateException("Workload identity unavailable");
    String token=JsonReader.string(JsonReader.object(JsonReader.parse(tokenResponse.body())).get("access_token"));
    if(token==null||token.isBlank())throw new IllegalStateException("Workload identity token missing");

    // Optional pinned historical snapshot. This is a REAL private GCS object,
    // not a hardcoded result. Keeps the verified Developer Edition test visible
    // after the rolling 1-hour Salesforce scan stops finding those sessions.
    String selected=env.getOrDefault("PUBLIC_EVIDENCE_OBJECT","").trim();
    if(!selected.isEmpty()){
      if(!selected.matches("snapshots/\\d{4}-\\d{2}-\\d{2}T[0-9A-Za-z._:+-]+\\.json"))
        throw new IllegalStateException("Invalid configured evidence object");
      String url="https://storage.googleapis.com/storage/v1/b/"+encode(bucket)+
          "/o/"+encode(selected)+"?alt=media";
      HttpResponse<String> snapshot=request(http,token,url);
      if(snapshot.body().length()>2_000_000)throw new IllegalStateException("Snapshot exceeds size limit");
      return fromSnapshot(snapshot.body());
    }

    // GCS lists objects lexicographically. Scan all pages to choose the latest
    // dated snapshot rather than relying on page order or a cached demo record.
    String prefix="snapshots/";
    String cursor=null,latest=null;
    int pages=0;
    do{
      if(++pages>30)throw new IllegalStateException("Snapshot listing exceeded safe page limit");
      String uri="https://storage.googleapis.com/storage/v1/b/"+encode(bucket)+
          "/o?prefix="+encode(prefix)+"&maxResults=1000&fields="+
          encode("nextPageToken,items(name)");
      if(cursor!=null)uri+="&pageToken="+encode(cursor);
      HttpResponse<String> response=request(http,token,uri);
      Map<String,Object> listing=JsonReader.object(JsonReader.parse(response.body()));
      if(listing.get("items") instanceof List<?> items){
        for(Object item:items){
          String name=JsonReader.string(JsonReader.object(item).get("name"));
          if(name!=null && name.matches("snapshots/\\d{4}-\\d{2}-\\d{2}T[0-9A-Za-z._:+-]+\\.json")
              && (latest==null || name.compareTo(latest)>0)) latest=name;
        }
      }
      cursor=JsonReader.string(listing.get("nextPageToken"));
    }while(cursor!=null&&!cursor.isBlank());
    if(latest==null)throw new IllegalStateException("No monitoring snapshots found");
    String url="https://storage.googleapis.com/storage/v1/b/"+encode(bucket)+
        "/o/"+encode(latest)+"?alt=media";
    HttpResponse<String> snapshot=request(http,token,url);
    if(snapshot.body().length()>2_000_000)throw new IllegalStateException("Snapshot exceeds size limit");
    return fromSnapshot(snapshot.body());
  }
  private static String encode(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
  private static HttpResponse<String> request(HttpClient http,String token,String url) throws Exception {
    HttpRequest req=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
        .header("Authorization","Bearer "+token).GET().build();
    HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString());
    if(res.statusCode()!=200)throw new IllegalStateException("Storage API HTTP "+res.statusCode());
    return res;
  }
}
