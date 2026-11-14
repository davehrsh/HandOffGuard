package dev.handoffguard;
import java.util.*;
public final class JsonReaderTest {
  public static void main(String[] args){
    Map<String,Object> response=JsonReader.object(JsonReader.parse("{\"done\":true,\"records\":[{\"Id\":\"0Mwg7000002VwGHCA0\",\"AcceptDateTime\":null}],\"note\":\"a\\n\\u0026b\"}"));
    if(!Boolean.TRUE.equals(response.get("done")))throw new AssertionError("done");
    if(JsonReader.array(response.get("records")).size()!=1)throw new AssertionError("records");
    if(JsonReader.object(JsonReader.array(response.get("records")).get(0)).get("AcceptDateTime")!=null)throw new AssertionError("null");
    if(!"a\n&b".equals(response.get("note")))throw new AssertionError("escape");
    for(String malformed:List.of("{","[1,]","{\"x\":}")){
      try{JsonReader.parse(malformed);throw new AssertionError("accepted invalid JSON: "+malformed);}
      catch(IllegalArgumentException expected){}
    }
    System.out.println("JsonReaderTest: passed");
  }
}
