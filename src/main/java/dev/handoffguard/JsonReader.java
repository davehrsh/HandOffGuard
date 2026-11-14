package dev.handoffguard;
import java.util.*;
/** Small dependency-free JSON parser for Salesforce REST responses. */
public final class JsonReader {
  private final String s; private int p;
  private JsonReader(String s){this.s=s;}
  public static Object parse(String s){JsonReader r=new JsonReader(s);Object v=r.value();r.ws();if(r.p!=s.length())throw new IllegalArgumentException("Trailing JSON");return v;}
  private void ws(){while(p<s.length()&&Character.isWhitespace(s.charAt(p)))p++;}
  private char peek(){ws();if(p>=s.length())throw new IllegalArgumentException("Unexpected JSON end");return s.charAt(p);}
  private Object value(){char c=peek();if(c=='"')return str();if(c=='{')return obj();if(c=='[')return arr();if(s.startsWith("true",p)){p+=4;return true;}if(s.startsWith("false",p)){p+=5;return false;}if(s.startsWith("null",p)){p+=4;return null;}int a=p;while(p<s.length()&&"-+0123456789.eE".indexOf(s.charAt(p))>=0)p++;if(a==p)throw new IllegalArgumentException("Invalid JSON token");return Double.parseDouble(s.substring(a,p));}
  private String str(){if(peek()!='"')throw new IllegalArgumentException("Expected string");p++;StringBuilder b=new StringBuilder();while(p<s.length()){char c=s.charAt(p++);if(c=='"')return b.toString();if(c=='\\'){if(p>=s.length())throw new IllegalArgumentException("Bad escape");char e=s.charAt(p++);switch(e){case '"'->b.append('"');case '\\'->b.append('\\');case '/'->b.append('/');case 'b'->b.append('\b');case 'f'->b.append('\f');case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'u'->{if(p+4>s.length())throw new IllegalArgumentException("Bad unicode");b.append((char)Integer.parseInt(s.substring(p,p+4),16));p+=4;}default->throw new IllegalArgumentException("Bad escape");}}else b.append(c);}throw new IllegalArgumentException("Unclosed string");}
  private Map<String,Object> obj(){p++;Map<String,Object> m=new LinkedHashMap<>();if(peek()=='}'){p++;return m;}while(true){String k=str();if(peek()!=':')throw new IllegalArgumentException("Expected colon");p++;m.put(k,value());char c=peek();p++;if(c=='}')return m;if(c!=',')throw new IllegalArgumentException("Expected comma");}}
  private List<Object> arr(){p++;List<Object> a=new ArrayList<>();if(peek()==']'){p++;return a;}while(true){a.add(value());char c=peek();p++;if(c==']')return a;if(c!=',')throw new IllegalArgumentException("Expected comma");}}
  @SuppressWarnings("unchecked") public static Map<String,Object> object(Object v){if(!(v instanceof Map<?,?>))throw new IllegalArgumentException("Expected JSON object");return (Map<String,Object>)v;}
  @SuppressWarnings("unchecked") public static List<Object> array(Object v){if(!(v instanceof List<?>))throw new IllegalArgumentException("Expected JSON array");return (List<Object>)v;}
  public static String string(Object v){return v==null?null:String.valueOf(v);}
}
