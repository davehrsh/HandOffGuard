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
            if (!"true".equalsIgnoreCase(System.getenv().getOrDefault("ENABLE_PRIVATE_EVIDENCE", "false"))) {
                send(x,403,"application/json","{\"error\":\"Historical Salesforce evidence disabled on public deployments\"}");return;
            }
            send(x,200,"application/json",Files.readString(Path.of("evidence","known-sessions.json")));
        });
        server.createContext("/api/health",x->send(x,200,"application/json",Json.encode(Map.of("status","UP","salesforceConnected",false,"mode","SIMULATOR"))));
        server.createContext("/",x->{
            if(!x.getRequestMethod().equals("GET")){send(x,405,"text/plain","Use GET");return;}
            String path=x.getRequestURI().getPath();
            Map<String,String> allowed=Map.of("/","index.html","/index.html","index.html","/app.js","app.js","/style.css","style.css");
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
