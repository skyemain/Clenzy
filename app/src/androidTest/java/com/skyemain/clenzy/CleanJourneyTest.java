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
  File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"Clenzy-journey-"+UUID.randomUUID());assertTrue(folder.mkdirs());File first=new File(folder,"Journey original.bin"),extra=new File(folder,"Journey extra.bin");
  try{
   byte[] data=new byte[357];new Random().nextBytes(data);Files.write(first.toPath(),data);Files.write(extra.toPath(),data);assertTrue(first.setLastModified(System.currentTimeMillis()-86400000));
   try(var scenario=ActivityScenario.launch(CleanActivity.class)){
    java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
    for(int n=0;n<100&&!ready.get();n++){scenario.onActivity(a->{var m=new ViewModelProvider(a).get(CleanupViewModel.class);ready.set(m.scanFinished&&!m.busy);});if(!ready.get())Thread.sleep(100);}
    scenario.onActivity(a->{var m=new ViewModelProvider(a).get(CleanupViewModel.class);assertFalse(m.scanFailed);assertTrue(m.files.stream().anyMatch(f->f.name().equals(first.getName())));m.files=m.files.stream().filter(f->f.name().equals(first.getName())||f.name().equals(extra.getName())).toList();assertEquals(2,m.files.size());m.changed();});
    UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.waitForIdle();
    assertTrue(device.takeScreenshot(new File(context.getExternalFilesDir(null),"Clenzy-Android-cleanup.png")));
    var duplicates=device.wait(Until.findObject(By.descStartsWith("Duplicate copies,")),5000);assertNotNull(duplicates);duplicates.click();
    var select=device.wait(Until.findObject(By.text("Select extra copies")),5000);assertNotNull(select);select.click();
    scenario.onActivity(a->{var m=new ViewModelProvider(a).get(CleanupViewModel.class);assertEquals(1,m.selected.size());assertFalse(m.selected.contains(first.toURI().toString()));});
    assertTrue(device.takeScreenshot(new File(context.getExternalFilesDir(null),"Clenzy-Android-selection.png")));
    device.findObject(By.text("Done")).click();
    scenario.recreate();device.waitForIdle();
    var clean=device.wait(Until.findObject(By.textStartsWith("Clean selected ·").enabled(true)),5000);assertNotNull(clean);clean.click();
    var confirm=device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)Confirm cleanup"))),5000);assertNotNull(confirm);confirm.click();
    assertTrue(device.wait(Until.hasObject(By.text("Room for more.")),10000));assertTrue(first.exists());assertFalse(extra.exists());
    assertTrue(device.takeScreenshot(new File(context.getExternalFilesDir(null),"Clenzy-Android-results.png")));
   }
  }finally{first.delete();extra.delete();folder.delete();}
 }
}
