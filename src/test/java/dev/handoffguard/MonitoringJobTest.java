package dev.handoffguard;
import java.time.*;
import java.util.*;
/** Regression checks for scheduled snapshot output and timestamp serialization. */
public final class MonitoringJobTest {
  public static void main(String[] args) {
    Instant at=Instant.parse("2026-11-16T05:50:44Z");
    TransferMonitor.Report report=new TransferMonitor.Report(
      "0Mwg7000002Y2cX","a00g700002Y6rHxAAJ",at,at.plusSeconds(61),
      AcceptanceVerifier.Status.TIMED_OUT,null,null,
      "No permitted human acceptance", "SALESFORCE_FLOW_MARKER_AND_AGENTWORK");
    String encoded=Json.encode(Map.of("results",List.of(report),"generatedAt",at.toString()));
    var root=JsonReader.object(JsonReader.parse(encoded));
    var results=JsonReader.array(root.get("results"));
    var row=JsonReader.object(results.get(0));
    if(!"TIMED_OUT".equals(row.get("status")))throw new AssertionError("status");
    if(!"2026-11-16T05:50:44Z".equals(row.get("transferStartedAt")))throw new AssertionError("timestamp");
    if(row.get("workId")!=null||row.get("latencySeconds")!=null)throw new AssertionError("null values");
    if(!SalesforceId.same("0Mwg7000002Y2cX","0Mwg7000002Y2cXCAS"))throw new AssertionError("Salesforce ID");
    System.out.println("MonitoringJobTest: 4 assertions passed");
  }
}
