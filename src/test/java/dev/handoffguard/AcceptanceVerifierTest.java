package dev.handoffguard;
import java.time.*;
import java.util.*;
import static dev.handoffguard.AcceptanceVerifier.*;
public class AcceptanceVerifierTest {
  static final String SESSION="0Mwg7000002VwGHCA0", HUMAN="005g700000HB9FtAAL", AUTOMATED="005g700000GZw7yAAD";
  static final Instant START=Instant.parse("2026-11-14T09:24:26Z");
  static int count=0;
  static Evidence evidence(Instant start, Instant observed, List<Work> works) {
    return new Evidence(SESSION,start,observed,Set.of(HUMAN),works,"TEST_FIXTURE",true);
  }
  static Work work(String id,String session,String user,Instant accepted) { return new Work(id,session,user,accepted); }
  static void check(Status expected, Evidence e) {
    Status actual=verify(e,Duration.ofSeconds(60)).status();
    if(actual!=expected) throw new AssertionError(expected+" != "+actual);
    count++;
  }
  public static void main(String[] args) {
    check(Status.ACCEPTED_ON_TIME,evidence(START,START.plusSeconds(100),List.of(work("human",SESSION,HUMAN,START.plusSeconds(44)))));
    check(Status.ACCEPTED_LATE,evidence(START,START.plusSeconds(120),List.of(work("human",SESSION,HUMAN,START.plusSeconds(61)))));
    check(Status.ACCEPTED_ON_TIME,evidence(START,START.plusSeconds(200),List.of(work("human",SESSION,HUMAN,START.plusSeconds(60)))));
    check(Status.PENDING,evidence(START,START.plusSeconds(59),List.of()));
    check(Status.TIMED_OUT,evidence(START,START.plusSeconds(61),List.of()));
    check(Status.INSUFFICIENT_EVIDENCE,evidence(null,START.plusSeconds(100),List.of()));
    check(Status.INSUFFICIENT_EVIDENCE,evidence(START,null,List.of()));
    check(Status.INSUFFICIENT_EVIDENCE,evidence(START,START.minusSeconds(1),List.of()));
    check(Status.TIMED_OUT,evidence(START,START.plusSeconds(90),List.of(work("automated",SESSION,AUTOMATED,START.plusSeconds(1)))));
    check(Status.TIMED_OUT,evidence(START,START.plusSeconds(90),List.of(work("other","different-session",HUMAN,START.plusSeconds(1)))));
    check(Status.TIMED_OUT,evidence(START,START.plusSeconds(90),List.of(work("invalid",SESSION,HUMAN,START.minusSeconds(1)))));
    check(Status.ACCEPTED_ON_TIME,evidence(START,START.plusSeconds(200),List.of(work("late",SESSION,HUMAN,START.plusSeconds(80)),work("early",SESSION,HUMAN,START.plusSeconds(20)))));
    check(Status.ACCEPTED_ON_TIME,evidence(START,START.plusSeconds(200),List.of(work("human",SESSION,HUMAN,START.plusSeconds(44)),work("automated",SESSION,AUTOMATED,START.plusSeconds(1)))));
    System.out.println(count+" verifier regression checks passed");
  }
}
