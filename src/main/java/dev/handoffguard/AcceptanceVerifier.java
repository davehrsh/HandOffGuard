package dev.handoffguard;

import java.time.*;
import java.util.*;

/** Read-only, deterministic assessment of a single Salesforce MessagingSession. */
public final class AcceptanceVerifier {
  public enum Status { ACCEPTED_ON_TIME, ACCEPTED_LATE, PENDING, TIMED_OUT, INSUFFICIENT_EVIDENCE }
  public record Work(String id, String workItemId, String userId, Instant acceptedAt) {}
  public record Evidence(String sessionId, Instant transferStartedAt, Instant observedAt,
                         Set<String> permittedHumanUserIds, List<Work> agentWorks,
                         String provenance, boolean completeAgentWorkSnapshot) {
    public Evidence(String sessionId, Instant transferStartedAt, Instant observedAt, Set<String> permittedHumanUserIds, List<Work> agentWorks, String provenance) {
      this(sessionId, transferStartedAt, observedAt, permittedHumanUserIds, agentWorks, provenance, false);
    }
  }
  public record Result(Status status, String sessionId, String workId, Long latencySeconds,
                       String explanation, String provenance) {}

  public static Result verify(Evidence e, Duration deadline) {
    Objects.requireNonNull(e); Objects.requireNonNull(deadline);
    if (deadline.isNegative() || deadline.isZero()) throw new IllegalArgumentException("deadline must be positive");
    if (blank(e.sessionId()) || blank(e.provenance()) || e.permittedHumanUserIds() == null ||
        e.permittedHumanUserIds().isEmpty() || e.agentWorks() == null || e.transferStartedAt() == null || e.observedAt() == null || e.observedAt().isBefore(e.transferStartedAt()))
      return result(e, Status.INSUFFICIENT_EVIDENCE, null, null, "Missing correlation, provenance, permitted human IDs or transfer start");
    List<Work> accepted = e.agentWorks().stream()
        .filter(w -> w != null && SalesforceId.same(e.sessionId(),w.workItemId()))
        .filter(w -> e.permittedHumanUserIds().contains(w.userId()))
        .filter(w -> w.acceptedAt() != null && !w.acceptedAt().isBefore(e.transferStartedAt()) && !w.acceptedAt().isAfter(e.observedAt()))
        .sorted(Comparator.comparing(Work::acceptedAt)).toList();
    if (!accepted.isEmpty()) {
      Work first = accepted.get(0);
      long seconds = Duration.between(e.transferStartedAt(), first.acceptedAt()).getSeconds();
      Status status = first.acceptedAt().isAfter(e.transferStartedAt().plus(deadline)) ? Status.ACCEPTED_LATE : Status.ACCEPTED_ON_TIME;
      return result(e, status, first.id(), seconds, "Verified permitted human AgentWork acceptance");
    }
    if (e.observedAt() == null || e.observedAt().isBefore(e.transferStartedAt()))
      return result(e, Status.INSUFFICIENT_EVIDENCE, null, null, "Missing or invalid observation timestamp");
    if (!e.completeAgentWorkSnapshot()) return result(e, Status.INSUFFICIENT_EVIDENCE, null, null, "AgentWork snapshot completeness not established");
    if (!e.observedAt().isBefore(e.transferStartedAt().plus(deadline)))
      return result(e, Status.TIMED_OUT, null, null, "No permitted human acceptance in supplied evidence by observation time; requires complete AgentWork snapshot");
    return result(e, Status.PENDING, null, null, "Deadline not elapsed at observation");
  }
  private static boolean blank(String s) { return s == null || s.isBlank(); }
  private static Result result(Evidence e, Status s, String work, Long seconds, String reason) {
    return new Result(s, e.sessionId(), work, seconds, reason, e.provenance());
  }
}
