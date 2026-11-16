package dev.handoffguard;
/** Salesforce 15-character case-sensitive IDs and their 18-character case-safe forms. */
public final class SalesforceId {
  private SalesforceId() {}
  public static boolean valid(String id) {
    return id!=null && (id.length()==15 || id.length()==18) && id.matches("[a-zA-Z0-9]+");
  }
  public static boolean same(String a,String b){
    return valid(a)&&valid(b)&&a.regionMatches(false,0,b,0,15);
  }
  public static String shortId(String id){
    if(!valid(id))throw new IllegalArgumentException("Invalid Salesforce ID");
    return id.substring(0,15);
  }
}
