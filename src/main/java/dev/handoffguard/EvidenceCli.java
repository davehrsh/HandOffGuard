package dev.handoffguard;
import java.nio.file.*;
import java.util.*;
/** Validates normalized evidence exported by a future channel-specific collector. */
public final class EvidenceCli {
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Usage: EvidenceCli SCENARIO session-id evidence.tsv");
        var scenario=Engine.Scenario.valueOf(args[0]);String session=args[1];
        var lines=Files.readAllLines(Path.of(args[2]));
        if(lines.isEmpty()||!lines.getFirst().equals("session\tatMs\ttype\tkey\tvalue"))throw new IllegalArgumentException("Invalid TSV header");
        record Key(String session,long at,String type){}
        Map<Key,Map<String,String>> rows=new LinkedHashMap<>();
        for(int i=1;i<lines.size();i++){
            if(lines.get(i).isBlank())continue;
            String[] parts=lines.get(i).split("\t",-1);
            if(parts.length!=5)throw new IllegalArgumentException("Expected five columns at line "+(i+1));
            long at=Long.parseLong(parts[1]);if(at<0)throw new IllegalArgumentException("Negative timestamp");
            var key=new Key(parts[0],at,parts[2]);
            var data=rows.computeIfAbsent(key,k->new LinkedHashMap<>());
            if(data.putIfAbsent(parts[3],parts[4])!=null)throw new IllegalArgumentException("Duplicate attribute at line "+(i+1));
        }
        List<Engine.Event> events=rows.entrySet().stream().map(e->new Engine.Event(e.getKey().session(),e.getKey().at(),e.getKey().type(),e.getValue())).toList();
        var result=new Engine().verify(scenario,session,events,"IMPORTED_UNVERIFIED");
        System.out.println(Json.encode(result));
        if(!result.status().equals("PASS"))System.exit(1);
    }
}
