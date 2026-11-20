package dev.handoffguard;
import java.util.*;
/** Regression: public evidence must be genuine monitoring provenance and redact IDs. */
public final class PublicEvidenceTest {
  public static void main(String[] args) {
    String snapshot="""
      {"generatedAt":"2026-11-16T07:19:56Z",
       "provenance":"SALESFORCE_FLOW_MARKER_AND_AGENTWORK",
       "results":[
         {"sessionId":"000000000000001","markerId":"abc","workId":"secret-work","status":"ACCEPTED_ON_TIME","latencySeconds":12,"explanation":"internal"},
         {"sessionId":"000000000000002","status":"TIMED_OUT","latencySeconds":null}
       ]}
      """;
    Map<String,Object> safe=PublicEvidence.fromSnapshot(snapshot);
    String output=Json.encode(safe);
    if(!output.contains("ACCEPTED_ON_TIME")||!output.contains("TIMED_OUT")||
       !output.contains("12")||!output.contains("2026-11-16"))
      throw new AssertionError("Verified assessments lost");
    for(String id:List.of("000000000000001","000000000000002","secret-work","markerId","explanation"))
      if(output.contains(id))throw new AssertionError("Private Salesforce data leaked: "+id);
    try{
      PublicEvidence.fromSnapshot(snapshot.replace("SALESFORCE_FLOW_MARKER_AND_AGENTWORK","SIMULATED"));
      throw new AssertionError("Simulator provenance accepted as actual evidence");
    }catch(IllegalArgumentException expected){}
    try{
      PublicEvidence.fromSnapshot(snapshot.replace("ACCEPTED_ON_TIME","NOT_REAL"));
      throw new AssertionError("Unknown verdict accepted");
    }catch(IllegalArgumentException expected){}
    System.out.println("PublicEvidenceTest passed");
  }
}
