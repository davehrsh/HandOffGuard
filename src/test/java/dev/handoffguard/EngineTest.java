package dev.handoffguard;
import java.util.*;
import java.nio.file.*;
public class EngineTest {
    private static int assertions;
    private static void require(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    private static boolean pass(Engine.Result r){return r.status().equals("PASS");}
    public static void main(String[] args)throws Exception{
        Engine engine=new Engine();
        var fixed=engine.run("fixed"); var broken=engine.run("broken");
        for(var r:fixed.results())require(pass(r),"Corrected scenario failed: "+r.scenario());
        for(var r:broken.results())require(!pass(r),"Injected fault escaped: "+r.scenario());
        for(var scenario:Engine.Scenario.values())require(!pass(engine.verify(scenario,"empty",List.of(),"TEST")),"Empty evidence passed");
        String session="mutation";
        var good=new Engine.RetailSimulator().execute(Engine.Scenario.CONTEXT,false,session);
        require(!pass(engine.verify(Engine.Scenario.CONTEXT,"other-session",good,"TEST")),"Cross-session evidence accepted");
        var noAccept=good.stream().filter(e->!e.type().equals("TRANSFER_ACCEPTED")).toList();
        require(!pass(engine.verify(Engine.Scenario.CONTEXT,session,noAccept,"TEST")),"Transcript-only evidence passed");
        var late=good.stream().map(e->e.type().equals("TRANSFER_ACCEPTED")?new Engine.Event(e.session(),10000,e.type(),e.data()):e).toList();
        require(!pass(engine.verify(Engine.Scenario.CONTEXT,session,late,"TEST")),"SLO violation passed");
        var early=good.stream().map(e->e.type().equals("TRANSFER_ACCEPTED")?new Engine.Event(e.session(),10,e.type(),e.data()):e).toList();
        require(!pass(engine.verify(Engine.Scenario.CONTEXT,session,early,"TEST")),"Acceptance before request passed");
        var noId=good.stream().map(e->{if(!e.type().equals("TRANSFER_ACCEPTED"))return e;var d=new HashMap<>(e.data());d.remove("transferId");return new Engine.Event(e.session(),e.atMs(),e.type(),d);}).toList();
        require(!pass(engine.verify(Engine.Scenario.CONTEXT,session,noId,"TEST")),"Missing transfer ID passed");
        var reversed=new ArrayList<>(good);Collections.reverse(reversed);
        require(pass(engine.verify(Engine.Scenario.CONTEXT,session,reversed,"TEST")),"Unordered events not normalized");
        var dup=new ArrayList<>(good);dup.add(good.stream().filter(e->e.type().equals("TRANSFER_ACCEPTED")).findFirst().orElseThrow());
        require(!pass(engine.verify(Engine.Scenario.CONTEXT,session,dup,"TEST")),"Duplicate acceptance passed");
        require(Json.encode("\"\n\\\t").equals("\"\\\"\\n\\\\\\t\""),"JSON escaping failed");
        try{engine.run("live");throw new AssertionError("Invalid profile accepted");}catch(IllegalArgumentException expected){assertions++;}
        Files.createDirectories(Path.of("build/reports"));
        Files.writeString(Path.of("build/reports/fixed.json"),Json.encode(fixed));
        Files.writeString(Path.of("build/reports/broken.json"),Json.encode(broken));
        System.out.println("PASS: "+assertions+" regression assertions. Fixed 6/6; injected faults detected 6/6.");
    }
}
