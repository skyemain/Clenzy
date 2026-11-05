package com.skyemain.clenzy;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class CleanupGroupsTest {
 @Test public void categories_overlap_without_double_counting_or_exposing_keepers(){
  var files=List.of(new ReviewFile("file:///Download/a.mp4","a.mp4",200000000,1,"same"),new ReviewFile("file:///Download/b.mp4","b.mp4",200000000,2,"same"),new ReviewFile("file:///Download/book.pdf","book.pdf",42,2,null),new ReviewFile("file:///Download/Screen_shot.png","Screen_shot.png",12,2,null));
  var groups=CleanupGroups.from(files);assertEquals(4,groups.size());assertEquals("Duplicate copies",groups.get(0).title());assertEquals(200000000,groups.get(0).bytes());
  assertEquals(2,groups.stream().filter(g->g.title().equals("Videos")).findFirst().get().files().size());
  assertEquals(1,groups.stream().filter(g->g.title().equals("Screenshots")).findFirst().get().files().size());
  for(var group:groups)assertTrue(group.keepers().contains("file:///Download/a.mp4"));
  assertEquals(200000054,CleanupGroups.reviewableBytes(groups));
 }
 @Test public void empty_categories_show_truthful_zero_counts(){var groups=CleanupGroups.from(List.of());assertEquals(4,groups.size());for(var g:groups){assertTrue(g.files().isEmpty());assertEquals(0,g.bytes());}}
 @org.junit.Test public void samsung_capture_paths_are_recognized(){
  org.junit.Assert.assertTrue(CleanupGroups.isScreenshot("image.png","/storage/emulated/0/DCIM/Screen captures/image.png"));
  org.junit.Assert.assertTrue(CleanupGroups.isScreenshot("Screen shot 2026.png","/Pictures/image.png"));
  org.junit.Assert.assertFalse(CleanupGroups.isScreenshot("holiday.png","/DCIM/Camera/holiday.png"));
 }
 @Test public void media_index_classifies_small_video_and_files_outside_named_folders() {
  var file = new ReviewFile("file:/Movies/clip", "clip", 7, 1, null, true, true, false);
  var image = new ReviewFile("file:/Pictures/001.jpg", "001.jpg", 19, 1, null, false, false, true);
  var groups = CleanupGroups.from(List.of(file.withDigest("digest"),image));
  assertEquals(1,groups.get(1).files().size());
  assertEquals(1,groups.get(2).files().size());
  assertEquals(1,groups.get(3).files().size());
 }
}
