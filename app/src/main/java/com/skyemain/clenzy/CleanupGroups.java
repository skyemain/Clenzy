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
        Set<String> extras=ReviewRules.extraCopies(files),keepers=new HashSet<>();
        for(var f:duplicates){if(!extras.contains(f.uri()))keepers.add(f.uri());}
        result.add(new Group("Duplicate copies",duplicates.size()+" files",duplicates,keepers));
        Map<String,List<ReviewFile>> categories=new LinkedHashMap<>();
        for(String key:List.of("Videos","Downloads","Screenshots","Large files"))categories.put(key,new ArrayList<>());
        for(var f:files){
            String path=java.net.URI.create(f.uri()).getPath().toLowerCase(Locale.ROOT),name=f.name().toLowerCase(Locale.ROOT);boolean large=f.bytes()>=100L*1024*1024;
            boolean video = f.video() || name.matches(".*\\.(mp4|mov|mkv|webm|avi|3gp|3g2|m4v|mpeg|mpg|ts|mts|m2ts|wmv|flv|ogv)$");
            if (video) categories.get("Videos").add(f);
            if (f.download() || path.contains("/download/") || path.contains("/downloads/") || receivedWhatsAppFile(path)) categories.get("Downloads").add(f);
            if (f.screenshot() || isScreenshot(name,path)) categories.get("Screenshots").add(f);
            if (large && !video) categories.get("Large files").add(f);
        }
        for(var entry:categories.entrySet())if(!entry.getKey().equals("Large files")||!entry.getValue().isEmpty()){
            entry.getValue().sort(Comparator.comparingLong(ReviewFile::bytes).reversed());
            result.add(new Group(entry.getKey(),entry.getValue().size()+" files",entry.getValue(),keepers));
        }
        return result;
    }
    private static boolean receivedWhatsAppFile(String path) {
        String[] segments = path.split("/");
        for (int i = 1; i + 3 < segments.length; i++) {
            String app = segments[i];
            if (!app.equals("whatsapp") && !app.equals("whatsapp business")) continue;
            if (!segments[i + 1].equals("media")) continue;
            String folder = segments[i + 2];
            String prefix = app + " ";
            if (!folder.startsWith(prefix)) continue;
            if (!Set.of("documents", "images", "video", "audio", "voice notes", "animated gifs")
                    .contains(folder.substring(prefix.length()))) continue;
            for (int j = i + 3; j < segments.length; j++) {
                if (segments[j].startsWith(".") || segments[j].equals("sent") || segments[j].equals("private")) return false;
            }
            return true;
        }
        return false;
    }
    public static boolean isScreenshot(String name,String path){
        String normalized=(name+"/"+path).toLowerCase(Locale.ROOT).replaceAll("[ _-]","");
        return normalized.contains("screenshot")||normalized.contains("screencapture")||normalized.contains("screencap/");
    }
    public static long reviewableBytes(List<Group> groups){
        Set<String> counted=new HashSet<>();long total=0;
        for(var group:groups)for(var f:group.files())if(!group.keepers().contains(f.uri())&&counted.add(f.uri())&&f.bytes()>0)total+=f.bytes();return total;
    }
}
