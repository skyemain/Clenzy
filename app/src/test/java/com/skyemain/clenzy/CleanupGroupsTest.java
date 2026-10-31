package com.skyemain.clenzy;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class CleanupGroupsTest {
 @Test public void categories_are_disjoint_and_count_only_extra_copies(){
  var files=List.of(new ReviewFile("file:///Download/a.mp4","a.mp4",200000000,1,"same"),new ReviewFile("file:///Download/b.mp4","b.mp4",200000000,2,"same"),new ReviewFile("file:///Download/book.pdf","book.pdf",42,2,null),new ReviewFile("file:///Pictures/Screenshots/s.png","s.png",12,2,null));
  var groups=CleanupGroups.from(files);assertEquals(3,groups.size());assertEquals("Duplicate copies",groups.get(0).title());assertEquals(200000000,groups.get(0).bytes());assertEquals(Set.of("file:///Download/a.mp4"),groups.get(0).keepers());
  Set<String> seen=new HashSet<>();for(var group:groups)for(var file:group.files())assertTrue(seen.add(file.uri()));assertEquals(4,seen.size());
 }
 @Test public void empty_categories_are_not_shown(){assertTrue(CleanupGroups.from(List.of()).isEmpty());}
}
