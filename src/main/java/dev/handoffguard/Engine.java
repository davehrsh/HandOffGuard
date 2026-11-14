package dev.handoffguard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Deterministic assertions over externally observable handoff evidence. No LLM judge. */
public final class Engine {
    public enum Scenario {
        HUMAN_REQUEST("Explicit human request", "A customer asks for a representative."),
        API_FAILURE("Repeated order-service failure", "Two failed lookups must trigger escalation."),
        NO_CAPACITY("No representative available", "An unavailable queue must create a fallback case."),
        CONTEXT("Conversation context survives", "Order, issue and attempted steps must reach the recipient."),
        RETRY("Timeout and duplicate retry", "A repeated request must not create a second handoff."),
        WRONG_QUEUE("Skill-based routing", "Order support must reach the Retail Support queue.");
        public final String title, description;
        Scenario(String title, String description) { this.title=title; this.description=description; }
    }
    public record Event(String session, long atMs, String type, Map<String,String> data) {
        public Event { data=Map.copyOf(data); }
    }
    public record Check(String name, boolean passed, String expected, String actual) {}
    public record Result(String scenario, String title, String description, String status,
                         String source, long handoffMs, List<Check> checks, List<Event> events) {}
    public record Run(String id, String profile, String source, String createdAt,
                      int passed, int failed, List<Result> results) {}

    private static Check check(String name, boolean ok, String expected, String actual) {
        return new Check(name,ok,expected,actual);
    }
    private static List<Event> of(List<Event> events, String type) {
        return events.stream().filter(e->e.type().equals(type)).toList();
    }
    public Result verify(Scenario scenario, String session, List<Event> evidence, String source) {
        List<Event> events = evidence.stream().filter(e->e.session().equals(session))
            .sorted(Comparator.comparingLong(Event::atMs)).toList();
        List<Check> checks = new ArrayList<>();
        var requested=of(events,"TRANSFER_REQUESTED");
        var accepted=of(events,"TRANSFER_ACCEPTED");
        var cases=of(events,"CASE_CREATED");
        checks.add(check("Transfer requested",!requested.isEmpty(),"At least one transfer request",requested.size()+" observed"));
        long latency=-1;
        if (scenario == Scenario.NO_CAPACITY) {
            checks.add(check("Fallback case created",cases.size()==1,"Exactly one fallback case",cases.size()+" observed"));
            boolean informed=events.stream().anyMatch(e->e.type().equals("CUSTOMER_MESSAGE") && "fallback".equals(e.data().get("kind")));
            boolean falseJoin=events.stream().anyMatch(e->e.type().equals("CUSTOMER_MESSAGE") && "joined".equals(e.data().get("kind")));
            checks.add(check("Honest customer status",informed&&!falseJoin,"Callback notice; no joined claim",informed&&!falseJoin?"Callback explained":"Missing fallback notice or false joined claim"));
            checks.add(check("No phantom acceptance",accepted.isEmpty(),"No acceptance while queue unavailable",accepted.size()+" observed"));
        } else {
            checks.add(check("Recipient accepted handoff",accepted.size()==1,"Exactly one receiving-side acceptance",accepted.size()+" observed"));
            if (!accepted.isEmpty()) {
                Event received=accepted.getFirst();
                String queue=received.data().getOrDefault("queue", "missing");
                checks.add(check("Correct destination",queue.equals("Retail Support"),"Retail Support",queue));
                List<String> missing=List.of("orderId","issue","attempts").stream()
                    .filter(k->received.data().getOrDefault(k,"").isBlank()).toList();
                checks.add(check("Required context preserved",missing.isEmpty(),"orderId, issue, attempts",missing.isEmpty()?"All fields present":"Missing: "+String.join(", ",missing)));
                if(!requested.isEmpty()) latency=received.atMs()-requested.getFirst().atMs();
            }
            checks.add(check("Handoff within SLO",latency>=0 && latency<=5000,"0–5000 ms",latency<0?"No valid request → acceptance pair":latency+" ms"));
            long distinct=accepted.stream().map(e->e.data().getOrDefault("transferId", "")).distinct().count();
            checks.add(check("No duplicate side effects",accepted.size()==1 && distinct==1 && !accepted.getFirst().data().getOrDefault("transferId", "").isBlank() && cases.size()<=1,"One acceptance; at most one case",accepted.size()+" acceptances, "+cases.size()+" cases"));
        }
        if(scenario==Scenario.API_FAILURE) {
            var errors=of(events,"LOOKUP_FAILED");
            boolean correct=errors.size()==2 && !requested.isEmpty() && errors.getLast().atMs()<=requested.getFirst().atMs();
            checks.add(check("Bounded retries before escalation",correct,"Two failed lookups before transfer",errors.size()+" failures"));
        }
        boolean pass=checks.stream().allMatch(Check::passed);
        return new Result(scenario.name(),scenario.title,scenario.description,pass?"PASS":"FAIL",source,latency,List.copyOf(checks),events);
    }

    /** In-memory fake transport, separated from the assertion engine. */
    public static final class RetailSimulator {
        private final Map<String,String> transfers=new ConcurrentHashMap<>();
        public List<Event> execute(Scenario scenario, boolean broken, String session) {
            List<Event> out=new ArrayList<>();
            emit(out,session,0,"CUSTOMER_MESSAGE",Map.of("text","Order DG-1042 has not arrived. Please connect me to a person."));
            if(scenario==Scenario.API_FAILURE) {
                emit(out,session,100,"LOOKUP_FAILED",Map.of("reason","Order API timeout"));
                emit(out,session,300,"LOOKUP_FAILED",Map.of("reason","Order API timeout"));
            }
            emit(out,session,400,"TRANSFER_REQUESTED",Map.of("queue","Retail Support","requestId",session+"-request"));
            if(scenario==Scenario.NO_CAPACITY) {
                emit(out,session,500,"QUEUE_UNAVAILABLE",Map.of("queue","Retail Support"));
                if(!broken) {
                    emit(out,session,600,"CASE_CREATED",Map.of("caseId",session+"-case","reason","No available representative"));
                    emit(out,session,700,"CUSTOMER_MESSAGE",Map.of("kind","fallback","text","No representative is available. A follow-up case has been created."));
                } else emit(out,session,700,"CUSTOMER_MESSAGE",Map.of("kind","joined","text","A representative has joined."));
            } else if(broken && (scenario==Scenario.HUMAN_REQUEST || scenario==Scenario.API_FAILURE)) {
                emit(out,session,500,"ROUTING_FAILED",Map.of("reason","Inactive routing configuration"));
                emit(out,session,600,"CUSTOMER_MESSAGE",Map.of("text","Connecting you now."));
            } else {
                accept(out,session,900,scenario,broken);
                if(scenario==Scenario.RETRY) {
                    emit(out,session,1100,"RETRY_RECEIVED",Map.of("requestId",session+"-request"));
                    if(broken) transfers.remove(session);
                    accept(out,session,1400,scenario,broken);
                }
            }
            return List.copyOf(out);
        }
        private void accept(List<Event> out,String session,long time,Scenario scenario,boolean broken) {
            String id=UUID.randomUUID().toString();
            String old=transfers.putIfAbsent(session,id);
            if(old!=null) { emit(out,session,time,"DUPLICATE_SUPPRESSED",Map.of("transferId",old)); return; }
            Map<String,String> context=new HashMap<>(Map.of("transferId",id,"queue",broken&&scenario==Scenario.WRONG_QUEUE?"Billing":"Retail Support","orderId","DG-1042","issue","Missing delivery","attempts","Order lookup attempted; customer requested human"));
            if(broken&&scenario==Scenario.CONTEXT) {context.remove("orderId");context.remove("attempts");}
            emit(out,session,time,"TRANSFER_ACCEPTED",context);
            emit(out,session,time+10,"CUSTOMER_MESSAGE",Map.of("kind","joined","text","A representative has joined with your order details."));
        }
        private void emit(List<Event> out,String session,long at,String type,Map<String,String> data) {out.add(new Event(session,at,type,data));}
    }
    public Run run(String profile) {
        if(!Set.of("broken","fixed").contains(profile)) throw new IllegalArgumentException("Profile must be broken or fixed");
        String id=UUID.randomUUID().toString();
        var simulator=new RetailSimulator();
        List<Result> results=Arrays.stream(Scenario.values()).map(s->{String session=id+"-"+s.name();return verify(s,session,simulator.execute(s,profile.equals("broken"),session),"SIMULATOR");}).toList();
        int passed=(int)results.stream().filter(r->r.status().equals("PASS")).count();
        return new Run(id,profile,"SIMULATOR",java.time.Instant.now().toString(),passed,results.size()-passed,results);
    }
}
