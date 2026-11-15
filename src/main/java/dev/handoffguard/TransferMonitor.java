package dev.handoffguard;
import java.time.*;
import java.util.*;
/**
 * Evaluates one Salesforce Flow-created transfer marker and AgentWork snapshot.
 * A transfer marker must originate from the actual escalation branch, not from a
 * manually entered or inferred timestamp.
 */
public final class TransferMonitor {
  public record Marker(String id,String sessionId,Instant startedAt) {}
  public record Report(String sessionId,String markerId,Instant transferStartedAt,
      Instant observedAt,AcceptanceVerifier.Status status,String workId,
      Long latencySeconds,String explanation,String provenance) {}
  public static Report assess(String sessionId,List<Marker> markers,List<AcceptanceVerifier.Work> works,
      Set<String> humanIds,Instant observation,Duration deadline,boolean snapshotComplete) {
    Objects.requireNonNull(markers);
    if(markers.size()!=1) return new Report(sessionId,null,null,observation,
        AcceptanceVerifier.Status.INSUFFICIENT_EVIDENCE,null,null,
        markers.isEmpty()?"No Salesforce Flow transfer marker for this session":
        "Multiple transfer markers; select an individual attempt before calculating latency",
        "SALESFORCE_FLOW_MARKER_AND_AGENTWORK");
    Marker marker=markers.get(0);
    if(marker==null||marker.id()==null||marker.id().isBlank()||
        !Objects.equals(marker.sessionId(),sessionId)||marker.startedAt()==null)
      return new Report(sessionId,null,null,observation,AcceptanceVerifier.Status.INSUFFICIENT_EVIDENCE,
        null,null,"Invalid transfer marker or missing Salesforce timestamp",
        "SALESFORCE_FLOW_MARKER_AND_AGENTWORK");
    AcceptanceVerifier.Result result=AcceptanceVerifier.verify(new AcceptanceVerifier.Evidence(
        sessionId,marker.startedAt(),observation,humanIds,works,
        "SALESFORCE_FLOW_MARKER_AND_AGENTWORK",snapshotComplete),deadline);
    return new Report(sessionId,marker.id(),marker.startedAt(),observation,result.status(),
        result.workId(),result.latencySeconds(),result.explanation(),result.provenance());
  }
}
