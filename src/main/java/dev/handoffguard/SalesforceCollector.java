package dev.handoffguard;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
/** Read-only Salesforce collector; never returns OAuth credentials or raw customer transcript. */
public final class SalesforceCollector {
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  private final String domain,clientId,secret,apiVersion;
  public SalesforceCollector(Map<String,String> env){
    domain=required(env,"SF_DOMAIN");clientId=required(env,"SF_CLIENT_ID");secret=required(env,"SF_CLIENT_SECRET");
    apiVersion=env.getOrDefault("SF_API_VERSION","v66.0");
    URI u=URI.create(domain);if(!"https".equals(u.getScheme())||u.getHost()==null||!u.getHost().endsWith(".salesforce.com")||u.getRawUserInfo()!=null||u.getPort()!=-1||!u.getPath().isEmpty()&&!u.getPath().equals("/"))throw new IllegalArgumentException("SF_DOMAIN must be Salesforce My Domain HTTPS origin");
    if(!apiVersion.matches("v[0-9]+\\.0"))throw new IllegalArgumentException("Invalid API version");
  }
  private static String required(Map<String,String> e,String key){String v=e.get(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing "+key);return v;}
  private Map<String,Object> json(HttpRequest req)throws Exception{
    HttpResponse<String> r=http.send(req,HttpResponse.BodyHandlers.ofString());
    if(r.statusCode()<200||r.statusCode()>299)throw new IllegalStateException("Salesforce HTTP "+r.statusCode());
    return JsonReader.object(JsonReader.parse(r.body()));
  }
  private String token()throws Exception{
    String form="grant_type=client_credentials&client_id="+enc(clientId)+"&client_secret="+enc(secret);
    HttpRequest req=HttpRequest.newBuilder(URI.create(domain+"/services/oauth2/token")).timeout(Duration.ofSeconds(15))
      .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
    return required(Map.of("token",Objects.requireNonNull(JsonReader.string(json(req).get("access_token")))),"token");
  }
  private static String enc(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
  private List<Map<String,Object>> query(String token,String soql)throws Exception{
    List<Map<String,Object>> out=new ArrayList<>();String next="/services/data/"+apiVersion+"/query?q="+enc(soql);
    for(int page=0;page<20;page++){
      URI u=URI.create(domain+next);
      HttpRequest req=HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+token).GET().build();
      Map<String,Object> result=json(req);
      for(Object row:JsonReader.array(result.get("records")))out.add(JsonReader.object(row));
      if(Boolean.TRUE.equals(result.get("done")))return out;
      next=JsonReader.string(result.get("nextRecordsUrl"));
      if(next==null||!next.startsWith("/services/data/"+apiVersion+"/query/"))throw new IllegalStateException("Unexpected Salesforce pagination URL");
    }
    throw new IllegalStateException("Salesforce pagination limit reached; completeness unknown");
  }
  private static Instant time(Object v){String s=JsonReader.string(v);return s==null?null:Instant.parse(s.replaceAll("([+-][0-9]{2})([0-9]{2})$","$1:$2"));}
  public AcceptanceVerifier.Result verify(String sessionId,Instant transferStart,Instant observation,Set<String> humanIds,Duration deadline)throws Exception{
    if(!sessionId.matches("[a-zA-Z0-9]{15}([a-zA-Z0-9]{3})?"))throw new IllegalArgumentException("Invalid session ID");
    if(humanIds==null||humanIds.isEmpty())throw new IllegalArgumentException("Human allowlist required");
    String bearer=token();
    List<Map<String,Object>> sessions=query(bearer,"SELECT Id FROM MessagingSession WHERE Id = '"+sessionId+"' LIMIT 1");
    if(sessions.size()!=1)throw new IllegalArgumentException("MessagingSession not found");
    List<Map<String,Object>> rows=query(bearer,"SELECT Id, WorkItemId, UserId, AcceptDateTime FROM AgentWork WHERE WorkItemId = '"+sessionId+"' ORDER BY CreatedDate ASC");
    List<AcceptanceVerifier.Work> works=new ArrayList<>();
    for(Map<String,Object> row:rows)works.add(new AcceptanceVerifier.Work(JsonReader.string(row.get("Id")),JsonReader.string(row.get("WorkItemId")),JsonReader.string(row.get("UserId")),time(row.get("AcceptDateTime"))));
    return AcceptanceVerifier.verify(new AcceptanceVerifier.Evidence(sessionId,transferStart,observation,humanIds,works,"SALESFORCE_REST_LIVE",true),deadline);
  }
  /** Returns evidence without deriving a deadline from a guessed transfer time. */
  public Map<String,Object> inspect(String sessionId,Set<String> humanIds)throws Exception{
    if(sessionId==null||!sessionId.matches("[a-zA-Z0-9]{15}([a-zA-Z0-9]{3})?"))throw new IllegalArgumentException("Invalid session ID");
    if(humanIds==null||humanIds.isEmpty())throw new IllegalArgumentException("Human allowlist required");
    String bearer=token();
    List<Map<String,Object>> sessions=query(bearer,"SELECT Id FROM MessagingSession WHERE Id = '"+sessionId+"' LIMIT 1");
    if(sessions.size()!=1)throw new IllegalArgumentException("MessagingSession not found");
    List<Map<String,Object>> rows=query(bearer,"SELECT Id, WorkItemId, UserId, AcceptDateTime FROM AgentWork WHERE WorkItemId = '"+sessionId+"' ORDER BY CreatedDate ASC");
    List<Map<String,Object>> accepted=new ArrayList<>();
    for(Map<String,Object> row:rows){
      String user=JsonReader.string(row.get("UserId"));
      String at=JsonReader.string(row.get("AcceptDateTime"));
      if(at!=null&&humanIds.contains(user))accepted.add(Map.of("workId",String.valueOf(row.get("Id")),"acceptedAt",at));
    }
    accepted.sort(Comparator.comparing(m->String.valueOf(m.get("acceptedAt"))));
    Map<String,Object> result=new LinkedHashMap<>();
    result.put("sessionId",sessionId);
    result.put("provenance","SALESFORCE_REST_LIVE");
    result.put("humanAcceptanceFound",!accepted.isEmpty());
    result.put("humanAcceptances",accepted);
    result.put("agentWorkRecordCount",rows.size());
    result.put("sloStatus","NOT_ASSESSED_TRANSFER_START_UNVERIFIED");
    result.put("note","Actual transfer-request timestamp must be independently established before computing a 60-second handoff SLO");
    return result;
  }

  /**
   * Flow records must be created at the escalation-request point. Multiple
   * markers are intentionally ambiguous until an individual attempt is chosen.
   */
  public TransferMonitor.Report monitor(String sessionId,Set<String> humanIds,Instant observedAt)throws Exception{
    if(sessionId==null||!sessionId.matches("[a-zA-Z0-9]{15}([a-zA-Z0-9]{3})?"))
      throw new IllegalArgumentException("Invalid session ID");
    if(humanIds==null||humanIds.isEmpty())throw new IllegalArgumentException("Human allowlist required");
    if(!"true".equalsIgnoreCase(System.getenv().getOrDefault("SF_FLOW_MARKERS_ENABLED","false")))
      throw new IllegalStateException("Salesforce Flow transfer marker collection not enabled");
    String bearer=token();
    List<Map<String,Object>> sessions=query(bearer,"SELECT Id FROM MessagingSession WHERE Id = '"+sessionId+"' LIMIT 1");
    if(sessions.size()!=1)throw new IllegalArgumentException("MessagingSession not found");
    String canonicalSessionId=JsonReader.string(sessions.get(0).get("Id"));
    String shortSessionId=SalesforceId.shortId(canonicalSessionId);
    List<Map<String,Object>> source=query(bearer,
      "SELECT Id, Messaging_Session_ID__c, Transfer_Started_At__c FROM HandoffGuardTransfer__c WHERE Messaging_Session_ID__c = '"+shortSessionId+"' ORDER BY CreatedDate DESC LIMIT 2");
    List<TransferMonitor.Marker> markers=new ArrayList<>();
    for(Map<String,Object> m:source)markers.add(new TransferMonitor.Marker(
      JsonReader.string(m.get("Id")),JsonReader.string(m.get("Messaging_Session_ID__c")),time(m.get("Transfer_Started_At__c"))));
    if(markers.size()!=1)return TransferMonitor.assess(sessionId,markers,List.of(),humanIds,observedAt,Duration.ofSeconds(60),false);
    List<Map<String,Object>> rows=query(bearer,
      "SELECT Id, WorkItemId, UserId, AcceptDateTime FROM AgentWork WHERE WorkItemId = '"+canonicalSessionId+"' ORDER BY CreatedDate ASC");
    List<AcceptanceVerifier.Work> works=new ArrayList<>();
    for(Map<String,Object> r:rows)works.add(new AcceptanceVerifier.Work(
      JsonReader.string(r.get("Id")),JsonReader.string(r.get("WorkItemId")),
      JsonReader.string(r.get("UserId")),time(r.get("AcceptDateTime"))));
    return TransferMonitor.assess(sessionId,markers,works,humanIds,observedAt,Duration.ofSeconds(60),true);
  }

  /**
   * Find recently created Flow transfer markers and evaluate each distinct
   * MessagingSession with the source-of-truth transfer timestamp.
   * Ambiguous multi-attempt sessions intentionally remain insufficient.
   */
  public List<TransferMonitor.Report> monitorRecent(int limit,Set<String> humanIds,Instant observedAt)throws Exception{
    if(limit<1||limit>100)throw new IllegalArgumentException("Limit must be 1..100");
    if(humanIds==null||humanIds.isEmpty())throw new IllegalArgumentException("Human allowlist required");
    String bearer=token();
    List<Map<String,Object>> markers=query(bearer,
      "SELECT Id, Messaging_Session_ID__c FROM HandoffGuardTransfer__c WHERE CreatedDate = LAST_N_DAYS:1 ORDER BY CreatedDate DESC LIMIT "+limit);
    Set<String> seen=new LinkedHashSet<>();
    List<TransferMonitor.Report> reports=new ArrayList<>();
    for(Map<String,Object> marker:markers) {
      String sessionId=JsonReader.string(marker.get("Messaging_Session_ID__c"));
      if(!SalesforceId.valid(sessionId)||!seen.add(SalesforceId.shortId(sessionId)))continue;
      reports.add(monitor(sessionId,humanIds,observedAt));
    }
    return reports;
  }

}
