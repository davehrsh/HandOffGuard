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
}
