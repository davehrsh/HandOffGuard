package dev.handoffguard;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

/** Local development server. Bind to loopback; use the Spring service for deployment. */
public class Server {
    public static void main(String[] args) throws Exception {
        int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
        HttpServer server=HttpServer.create(new InetSocketAddress(System.getenv().getOrDefault("HOST","127.0.0.1"),port),32);
        Engine engine=new Engine();
        server.createContext("/api/run",x->{
            if(!x.getRequestMethod().equals("POST")){send(x,405,"application/json","{\"error\":\"Use POST\"}");return;}
            String query=x.getRequestURI().getRawQuery();
            String profile="profile=fixed".equals(query)?"fixed":"profile=broken".equals(query)?"broken":"";
            try {send(x,200,"application/json",Json.encode(engine.run(profile)));}
            catch(IllegalArgumentException e){send(x,400,"application/json",Json.encode(Map.of("error",e.getMessage())));}
        });
        server.createContext("/api/salesforce/evidence",x->{
            if(!x.getRequestMethod().equals("GET")){send(x,405,"application/json","{\"error\":\"Use GET\"}");return;}
            if(!"true".equalsIgnoreCase(System.getenv().getOrDefault("PUBLIC_EVIDENCE_ENABLED","false"))){
                send(x,503,"application/json","{\"error\":\"Snapshot evidence reader not configured\"}");return;
            }
            try {
                Map<String,Object> evidence=PublicEvidence.latest(System.getenv());
                send(x,200,"application/json",Json.encode(evidence));
            }catch(Exception e){
                System.err.println("Public evidence read failed: "+e.getClass().getSimpleName());
                send(x,503,"application/json","{\"error\":\"Verified monitoring evidence is currently unavailable\"}");
            }
        });
        server.createContext("/api/salesforce/monitor",x->{
            if(!x.getRequestMethod().equals("POST")){send(x,405,"application/json","{\"error\":\"Use POST\"}");return;}
            Map<String,String> env=System.getenv();
            String access=env.getOrDefault("SF_VERIFY_API_KEY","");
            if(!"true".equalsIgnoreCase(env.getOrDefault("SF_LIVE_ENABLED","false"))||access.length()<32){
                send(x,503,"application/json","{\"error\":\"Live Salesforce integration not configured\"}");return;
            }
            String supplied=x.getRequestHeaders().getFirst("X-HandoffGuard-Key");
            if(supplied==null||!java.security.MessageDigest.isEqual(access.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){
                send(x,401,"application/json","{\"error\":\"Unauthorized\"}");return;
            }
            if(!"true".equalsIgnoreCase(env.getOrDefault("SF_FLOW_MARKERS_ENABLED","false"))){
                send(x,503,"application/json","{\"error\":\"Salesforce Flow transfer marker collection not configured\"}");return;
            }
            try{
                byte[] body=x.getRequestBody().readNBytes(4097);
                if(body.length>4096){send(x,413,"application/json","{\"error\":\"Request too large\"}");return;}
                Map<String,Object> input=JsonReader.object(JsonReader.parse(new String(body,StandardCharsets.UTF_8)));
                String sessionId=JsonReader.string(input.get("sessionId"));
                Set<String> humans=new HashSet<>();
                for(String id:env.getOrDefault("SF_HUMAN_USER_IDS","").split(","))
                    if(id.trim().matches("[a-zA-Z0-9]{15}([a-zA-Z0-9]{3})?"))humans.add(id.trim());
                TransferMonitor.Report report=new SalesforceCollector(env).monitor(sessionId,humans,java.time.Instant.now());
                send(x,200,"application/json",Json.encode(report));
            }catch(IllegalArgumentException e){send(x,400,"application/json",Json.encode(Map.of("error",e.getMessage())));}
            catch(Exception e){System.err.println("Salesforce monitor unavailable: "+e.getClass().getSimpleName());send(x,502,"application/json","{\"error\":\"Salesforce monitoring unavailable; check server logs\"}");}
        });
        server.createContext("/api/salesforce/inspect",x->{
            if(!x.getRequestMethod().equals("POST")){send(x,405,"application/json","{\"error\":\"Use POST\"}");return;}
            Map<String,String> env=System.getenv();
            String access=env.getOrDefault("SF_VERIFY_API_KEY","");
            if(!"true".equalsIgnoreCase(env.getOrDefault("SF_LIVE_ENABLED","false"))||access.length()<32){
                send(x,503,"application/json","{\"error\":\"Live Salesforce integration not configured\"}");return;
            }
            String supplied=x.getRequestHeaders().getFirst("X-HandoffGuard-Key");
            if(supplied==null||!java.security.MessageDigest.isEqual(access.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){
                send(x,401,"application/json","{\"error\":\"Unauthorized\"}");return;
            }
            try{
                byte[] body=x.getRequestBody().readNBytes(4097);
                if(body.length>4096){send(x,413,"application/json","{\"error\":\"Request too large\"}");return;}
                Map<String,Object> input=JsonReader.object(JsonReader.parse(new String(body,StandardCharsets.UTF_8)));
                String sessionId=JsonReader.string(input.get("sessionId"));
                Set<String> humans=new HashSet<>();
                for(String id:env.getOrDefault("SF_HUMAN_USER_IDS","").split(","))
                    if(id.trim().matches("[a-zA-Z0-9]{15}([a-zA-Z0-9]{3})?"))humans.add(id.trim());
                Map<String,Object> result=new SalesforceCollector(env).inspect(sessionId,humans);
                send(x,200,"application/json",Json.encode(result));
            }catch(IllegalArgumentException e){send(x,400,"application/json",Json.encode(Map.of("error",e.getMessage())));}
            catch(Exception e){System.err.println("Salesforce inspection failed: "+e.getClass().getSimpleName());send(x,502,"application/json","{\"error\":\"Salesforce inspection unavailable; check server logs\"}");}
        });
        server.createContext("/api/salesforce/verify",x->{
            if(!x.getRequestMethod().equals("POST")){send(x,405,"application/json","{\"error\":\"Use POST\"}");return;}
            Map<String,String> env=System.getenv();
            String access=env.getOrDefault("SF_VERIFY_API_KEY","");
            if(!"true".equalsIgnoreCase(env.getOrDefault("SF_LIVE_ENABLED","false"))||access.length()<32){
                send(x,503,"application/json","{\"error\":\"Live Salesforce integration not configured\"}");return;
            }
            String supplied=x.getRequestHeaders().getFirst("X-HandoffGuard-Key");
            if(supplied==null||!java.security.MessageDigest.isEqual(access.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){
                send(x,401,"application/json","{\"error\":\"Unauthorized\"}");return;
            }
            try{
                byte[] body=x.getRequestBody().readNBytes(4097);
                if(body.length>4096){send(x,413,"application/json","{\"error\":\"Request too large\"}");return;}
                Map<String,Object> input=JsonReader.object(JsonReader.parse(new String(body,StandardCharsets.UTF_8)));
                String sessionId=JsonReader.string(input.get("sessionId"));
                String started=JsonReader.string(input.get("transferStartedAt"));
                if(sessionId==null||started==null)throw new IllegalArgumentException("sessionId and transferStartedAt required");
                java.time.Instant transfer=java.time.Instant.parse(started);
                String allow=env.getOrDefault("SF_HUMAN_USER_IDS","");
                Set<String> humans=new HashSet<>();
                for(String id:allow.split(","))if(id.trim().matches("[a-zA-Z0-9]{15}([a-zA-Z0-9]{3})?"))humans.add(id.trim());
                AcceptanceVerifier.Result result=new SalesforceCollector(env).verify(sessionId,transfer,java.time.Instant.now(),humans,java.time.Duration.ofSeconds(60));
                Map<String,Object> resultView=new LinkedHashMap<>();
                resultView.put("sessionId", result.sessionId());
                resultView.put("humanAcceptanceFound", result.workId()!=null);
                resultView.put("workId", result.workId());
                resultView.put("provenance", result.provenance());
                resultView.put("sloStatus", "NOT_VERIFIED_MANUAL_TRANSFER_TIMESTAMP");
                resultView.put("reportedStatusFromSuppliedTimestamp", result.status().toString());
                resultView.put("reportedLatencySecondsFromSuppliedTimestamp", result.latencySeconds());
                resultView.put("warning", "Caller-supplied transfer timestamp is not independently verified; do not interpret reported latency as a Salesforce SLO");
                send(x,200,"application/json",Json.encode(resultView));
            }catch(IllegalArgumentException e){send(x,400,"application/json",Json.encode(Map.of("error",e.getMessage())));}
            catch(Exception e){System.err.println("Salesforce verification failed: "+e.getClass().getSimpleName());send(x,502,"application/json","{\"error\":\"Salesforce verification unavailable; check server logs\"}");}
        });
        server.createContext("/api/health",x->send(x,200,"application/json",Json.encode(Map.of("status","UP","salesforceConnected",false,"salesforceIntegrationEnabled","true".equalsIgnoreCase(System.getenv().getOrDefault("SF_LIVE_ENABLED","false")),"mode","SIMULATOR_WITH_PROTECTED_SALESFORCE_API"))));
        server.createContext("/",x->{
            if(!x.getRequestMethod().equals("GET")){send(x,405,"text/plain","Use GET");return;}
            String path=x.getRequestURI().getPath();
            Map<String,String> allowed=Map.of("/","index.html","/index.html","index.html","/app.js","app.js","/style.css","style.css","/history","history.html","/history.html","history.html","/history.js","history.js");
            String file=allowed.get(path);
            if(file==null){send(x,404,"text/plain","Not found");return;}
            String mime=file.endsWith(".js")?"text/javascript":file.endsWith(".css")?"text/css":"text/html";
            send(x,200,mime,Files.readString(Path.of("web",file)));
        });
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("HandoffGuard: http://localhost:"+port+" — SIMULATOR ONLY");
    }
    private static void send(HttpExchange x,int status,String type,String body)throws IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; style-src 'self'; script-src 'self'; frame-ancestors 'none'");
        x.sendResponseHeaders(status,bytes.length);try(var out=x.getResponseBody()){out.write(bytes);}
    }
}
