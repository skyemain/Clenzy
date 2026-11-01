package com.skyemain.clenzy;
import java.util.*;

public final class CleanupGroups {
    public record Group(String title,String subtitle,List<ReviewFile> files,Set<String> keepers) {
        public long bytes(){long n=0;for(var f:files)if(!keepers.contains(f.uri())&&f.bytes()>0)n+=f.bytes();return n;}
    }
    public static List<Group> from(List<ReviewFile> source){
        List<ReviewFile> files=new ArrayList<>();Set<String> seen=new HashSet<>();for(var f:source)if(seen.add(f.uri()))files.add(f);
        List<Group> result=new ArrayList<>();
        List<ReviewFile> duplicates=ReviewRules.filter(files,ReviewRules.Category.DUPLICATES,System.currentTimeMillis());
        Set<String> extras=ReviewRules.extraCopies(files),keepers=new HashSet<>(),duplicateUris=new HashSet<>();
        for(var f:duplicates){duplicateUris.add(f.uri());if(!extras.contains(f.uri()))keepers.add(f.uri());}
        result.add(new Group("Duplicate copies",duplicates.size()+" files",duplicates,keepers));
        Map<String,List<ReviewFile>> categories=new LinkedHashMap<>();
        for(String key:List.of("Large videos","Downloads","Screenshots","Large files"))categories.put(key,new ArrayList<>());
        for(var f:files){
            String path=java.net.URI.create(f.uri()).getPath().toLowerCase(Locale.ROOT),name=f.name().toLowerCase(Locale.ROOT);boolean large=f.bytes()>=100L*1024*1024;
            if(large&&name.matches(".*\\.(mp4|mov|mkv|webm|avi|3gp)$"))categories.get("Large videos").add(f);
            if(path.contains("/download/")||path.contains("/downloads/"))categories.get("Downloads").add(f);
            if(name.replace("_","").replace("-","").contains("screenshot")||path.contains("/screenshots/"))categories.get("Screenshots").add(f);
            if(large&&!name.matches(".*\\.(mp4|mov|mkv|webm|avi|3gp)$"))categories.get("Large files").add(f);
        }
        for(var entry:categories.entrySet())if(!entry.getKey().equals("Large files")||!entry.getValue().isEmpty()){
            entry.getValue().sort(Comparator.comparingLong(ReviewFile::bytes).reversed());
            result.add(new Group(entry.getKey(),entry.getValue().size()+" files",entry.getValue(),keepers));
        }
        return result;
    }
    public static long reviewableBytes(List<Group> groups){
        Set<String> counted=new HashSet<>();long total=0;
        for(var group:groups)for(var f:group.files())if(!group.keepers().contains(f.uri())&&counted.add(f.uri())&&f.bytes()>0)total+=f.bytes();return total;
    }
}
