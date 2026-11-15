package dev.handoffguard;
import java.time.*;
import java.util.*;
public final class TransferMonitorTest {
  private static final String SESSION="0Mwg7000002VwGHCA0", HUMAN="005g700000HB9FtAAL", BOT="005g700000GZw7yAAD";
  private static final Instant START=Instant.parse("2026-11-14T09:24:00Z");
  private static final TransferMonitor.Marker MARKER=new TransferMonitor.Marker("a00Test123456789",SESSION,START);
  private static final Duration DEADLINE=Duration.ofSeconds(60);
  private static void expect(AcceptanceVerifier.Status expected,TransferMonitor.Report actual) {
    if(actual.status()!=expected)throw new AssertionError("expected "+expected+" got "+actual.status()+" "+actual.explanation());
  }
  private static TransferMonitor.Report run(List<TransferMonitor.Marker> markers,List<AcceptanceVerifier.Work> works,Instant now,boolean complete){
    return TransferMonitor.assess(SESSION,markers,works,Set.of(HUMAN),now,DEADLINE,complete);
  }
  public static void main(String[] args){
    expect(AcceptanceVerifier.Status.INSUFFICIENT_EVIDENCE,run(List.of(),List.of(),START.plusSeconds(61),true));
    expect(AcceptanceVerifier.Status.INSUFFICIENT_EVIDENCE,run(List.of(MARKER,MARKER),List.of(),START.plusSeconds(61),true));
    expect(AcceptanceVerifier.Status.PENDING,run(List.of(MARKER),List.of(),START.plusSeconds(59),true));
    expect(AcceptanceVerifier.Status.TIMED_OUT,run(List.of(MARKER),List.of(),START.plusSeconds(60),true));
    expect(AcceptanceVerifier.Status.INSUFFICIENT_EVIDENCE,run(List.of(MARKER),List.of(),START.plusSeconds(61),false));
    var bot=new AcceptanceVerifier.Work("bot",SESSION,BOT,START.plusSeconds(3));
    var ontime=new AcceptanceVerifier.Work("human",SESSION,HUMAN,START.plusSeconds(44));
    var late=new AcceptanceVerifier.Work("late",SESSION,HUMAN,START.plusSeconds(61));
    var other=new AcceptanceVerifier.Work("other","0Mwg00000000000000",HUMAN,START.plusSeconds(4));
    expect(AcceptanceVerifier.Status.ACCEPTED_ON_TIME,run(List.of(MARKER),List.of(bot,other,ontime),START.plusSeconds(90),true));
    expect(AcceptanceVerifier.Status.ACCEPTED_LATE,run(List.of(MARKER),List.of(late),START.plusSeconds(90),true));
    expect(AcceptanceVerifier.Status.TIMED_OUT,run(List.of(MARKER),List.of(bot,other),START.plusSeconds(90),true));
    expect(AcceptanceVerifier.Status.INSUFFICIENT_EVIDENCE,run(List.of(new TransferMonitor.Marker("bad","incorrect",START)),List.of(ontime),START.plusSeconds(90),true));
    System.out.println("TransferMonitorTest: 9 scenarios passed");
  }
}
