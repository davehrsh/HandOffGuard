package dev.handoffguard;
import java.time.*;
import java.util.*;
public class SalesforceVerifierRegressionTest {
 static final Instant START=Instant.parse("2026-11-14T09:24:26Z");
 static final String SESSION="0Mwg7000002VwGHCA0", HUMAN="005g700000HB9FtAAL", AUTO="005g700000GZw7yAAD";
 static int checks=0;
 static void check(AcceptanceVerifier.Status expected, Instant observed, List<AcceptanceVerifier.Work> work, boolean complete) {
  var e=new AcceptanceVerifier.Evidence(SESSION,START,observed,Set.of(HUMAN),work,"TEST_FIXTURE",complete);
  var actual=AcceptanceVerifier.verify(e,Duration.ofSeconds(60)).status();
  if(actual!=expected) throw new AssertionError(expected+" != "+actual); checks++;
 }
 static AcceptanceVerifier.Work w(String id,String session,String user,long offset){return new AcceptanceVerifier.Work(id,session,user,START.plusSeconds(offset));}
 public static void main(String[] args){
  check(AcceptanceVerifier.Status.ACCEPTED_ON_TIME,START.plusSeconds(44),List.of(w("human",SESSION,HUMAN,44)),true);
  check(AcceptanceVerifier.Status.ACCEPTED_LATE,START.plusSeconds(90),List.of(w("human",SESSION,HUMAN,80)),true);
  check(AcceptanceVerifier.Status.PENDING,START.plusSeconds(59),List.of(),true);
  check(AcceptanceVerifier.Status.TIMED_OUT,START.plusSeconds(60),List.of(w("auto",SESSION,AUTO,1)),true);
  check(AcceptanceVerifier.Status.INSUFFICIENT_EVIDENCE,START.plusSeconds(90),List.of(),false);
  check(AcceptanceVerifier.Status.INSUFFICIENT_EVIDENCE,null,List.of(),true);
  check(AcceptanceVerifier.Status.PENDING,START.plusSeconds(10),List.of(w("future",SESSION,HUMAN,30)),true);
  check(AcceptanceVerifier.Status.TIMED_OUT,START.plusSeconds(70),List.of(w("other","another-session",HUMAN,20)),true);
  System.out.println("Salesforce verifier regression: "+checks+" passed");
 }
}
