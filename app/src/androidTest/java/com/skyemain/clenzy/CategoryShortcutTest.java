package com.skyemain.clenzy;
import android.content.*;
import android.os.*;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class CategoryShortcutTest {
 @Test public void shortcuts_open_actual_category_and_samsung_capture_folder() throws Exception {
  Context context=ApplicationProvider.getApplicationContext();
  try(var fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("appops set --uid "+context.getPackageName()+" MANAGE_EXTERNAL_STORAGE allow");var in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){while(in.read()!=-1){}}
  String id="Clenzy-shortcut-"+UUID.randomUUID();
  File downloadDir=new File(Environment.getExternalStorageDirectory(),"WhatsApp/Media/WhatsApp Documents/"+id),captureDir=new File(Environment.getExternalStorageDirectory(),"DCIM/Screen_captures/"+id);
  assertTrue(downloadDir.mkdirs());assertTrue(captureDir.mkdirs());File document=new File(downloadDir,"document-"+id+".pdf"),capture=new File(captureDir,"capture-"+id+".png"),video=new File(downloadDir,"clip-"+id+".mp4");
  try {
   for (File file : new File[]{document, capture, video}) {
    Files.write(file.toPath(), new byte[7]);
    assertEquals(7L, file.length());
   }
   String[] categories={"Downloads","Screenshots","Videos"};File[] files={document,capture,video};
   UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
   for(int n=0;n<categories.length;n++){
    try(var scenario=ActivityScenario.<CleanActivity>launch(new Intent(context,CleanActivity.class).putExtra("category",categories[n]))){
     assertTrue(categories[n]+" opens sheet",device.wait(Until.hasObject(By.text("Sort")),30000));
     scenario.onActivity(activity -> {
      var model = new androidx.lifecycle.ViewModelProvider(activity).get(CleanupViewModel.class);
      assertTrue(model.status, model.status.contains("Android index:"));
     });
     assertTrue(categories[n]+" discovers file",device.wait(Until.hasObject(By.text(files[n].getName())),5000));
    }
   }
  } finally {document.delete();capture.delete();video.delete();downloadDir.delete();captureDir.delete();}
 }
 @Test public void unreadable_root_fails_instead_of_empty_success() throws Exception {
  Context context=ApplicationProvider.getApplicationContext();File missing=new File(context.getCacheDir(),"missing-"+UUID.randomUUID());
  try {new SharedStorageCleaner(missing).scan(new CancellationSignal(),false,false);fail("Missing root must fail");}catch(IOException expected){}
 }
}
