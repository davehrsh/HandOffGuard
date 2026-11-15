package dev.handoffguard;
import java.util.*;
/** Output-only JSON encoder for records, collections and primitive values. */
public final class Json {
    public static String encode(Object value) {
        if(value==null) return "null";
        if(value instanceof java.time.temporal.TemporalAccessor) return quote(value.toString());
        if(value instanceof String || value instanceof Enum<?>) return quote(value.toString());
        if(value instanceof Number || value instanceof Boolean) return value.toString();
        if(value instanceof Map<?,?> m) return "{"+String.join(",",m.entrySet().stream().map(e->quote(e.getKey().toString())+":"+encode(e.getValue())).toList())+"}";
        if(value instanceof Collection<?> c) return "["+String.join(",",c.stream().map(Json::encode).toList())+"]";
        if(value.getClass().isRecord()) {
            Map<String,Object> map=new LinkedHashMap<>();
            for(var c:value.getClass().getRecordComponents())try{map.put(c.getName(),c.getAccessor().invoke(value));}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
            return encode(map);
        }
        throw new IllegalArgumentException("Unsupported JSON value");
    }
    private static String quote(String value) {
        StringBuilder out=new StringBuilder("\"");
        for(char c:value.toCharArray()) switch(c) {
            case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
            default -> {if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}
        }
        return out.append('"').toString();
    }
}
