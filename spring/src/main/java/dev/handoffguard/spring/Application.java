package dev.handoffguard.spring;
import dev.handoffguard.Engine;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
@SpringBootApplication
@RestController
public class Application {
    private final Engine engine=new Engine();
    public static void main(String[] args){SpringApplication.run(Application.class,args);}
    @PostMapping("/api/run") public Engine.Run run(@RequestParam String profile){return engine.run(profile);}
    @GetMapping("/api/health") public Map<String,Object> health(){return Map.of("status","UP","salesforceConnected",false,"mode","SIMULATOR");}
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));}
}
