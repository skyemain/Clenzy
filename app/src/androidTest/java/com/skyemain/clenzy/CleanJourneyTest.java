package com.skyemain.clenzy;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import androidx.lifecycle.ViewModelProvider;
import android.content.*;
import android.os.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class CleanJourneyTest {
 @Test public void approved_journey_preserves_original_and_shows_verified_results() throws Exception {
  Context context=ApplicationProvider.getApplicationContext();
  try(var fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("appops set --uid "+context.getPackageName()+" MANAGE_EXTERNAL_STORAGE allow");var input=new ParcelFileDescriptor.AutoCloseInputStream(fd)){while(input.read()!=-1){}}
  File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"Clenzy-journey-"+UUID.randomUUID());assertTrue(folder.mkdirs());File first=new File(folder,"Journey original.bin"),extra=new File(folder,"Journey extra.bin"),video=new File(folder,"Journey clip.mp4"),screenshot=new File(folder,"Screenshot Journey.png"),download=new File(folder,"Journey guide.pdf");
  try{
   byte[] data=new byte[357];new Random().nextBytes(data);Files.write(first.toPath(),data);Files.write(extra.toPath(),data);assertTrue(first.setLastModified(System.currentTimeMillis()-86400000));
   try(var file=new RandomAccessFile(video,"rw")){file.setLength(100L*1024*1024+17);}
   android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(40,40,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.BLUE);try(var out=new FileOutputStream(screenshot)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();Files.write(download.toPath(),new byte[89]);
   try(var scenario=ActivityScenario.launch(CleanActivity.class)){
    java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
    for(int n=0;n<100&&!ready.get();n++){scenario.onActivity(a->{var m=new ViewModelProvider(a).get(CleanupViewModel.class);ready.set(m.scanFinished&&!m.busy);});if(!ready.get())Thread.sleep(100);}
    scenario.onActivity(a->{var m=new ViewModelProvider(a).get(CleanupViewModel.class);assertFalse(m.scanFailed);assertTrue(m.files.stream().anyMatch(f->f.name().equals(first.getName())));m.files=m.files.stream().filter(f->f.uri().contains(folder.getName())).toList();assertEquals(5,m.files.size());m.changed();});
    UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.waitForIdle();
    if(!device.wait(Until.hasObject(By.text("Videos")),15000)){java.io.ByteArrayOutputStream hierarchy=new java.io.ByteArrayOutputStream();device.dumpWindowHierarchy(hierarchy);throw new AssertionError("Videos missing: "+hierarchy.toString("UTF-8"));}assertNotNull("Downloads visible",device.findObject(By.text("Downloads")));assertNotNull("Screenshots visible",device.findObject(By.text("Screenshots")));
    assertTrue(device.takeScreenshot(new File(context.getExternalFilesDir(null),"Clenzy-Android-cleanup.png")));
    var duplicates=device.wait(Until.findObject(By.text("Duplicate copies")),5000);assertNotNull(duplicates);duplicates.click();
    var select=device.wait(Until.findObject(By.text("Select extra copies")),5000);assertNotNull(select);select.click();
    scenario.onActivity(a->{var m=new ViewModelProvider(a).get(CleanupViewModel.class);assertEquals(1,m.selected.size());assertFalse(m.selected.contains(first.toURI().toString()));});
    assertTrue(device.takeScreenshot(new File(context.getExternalFilesDir(null),"Clenzy-Android-selection.png")));
    device.findObject(By.text("Done")).click();
    scenario.recreate();device.waitForIdle();
    var clean=device.wait(Until.findObject(By.textStartsWith("Clean selected ·").enabled(true)),5000);assertNotNull(clean);clean.click();
    var confirm=device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)Confirm cleanup"))),5000);assertNotNull(confirm);confirm.click();
    assertTrue(device.wait(Until.hasObject(By.text("Cleanup complete")),10000));assertTrue(first.exists());assertFalse(extra.exists());assertTrue(video.exists());assertTrue(screenshot.exists());assertTrue(download.exists());
    assertTrue(device.takeScreenshot(new File(context.getExternalFilesDir(null),"Clenzy-Android-results.png")));
   }
  }finally{first.delete();extra.delete();video.delete();screenshot.delete();download.delete();folder.delete();}
 }
}
