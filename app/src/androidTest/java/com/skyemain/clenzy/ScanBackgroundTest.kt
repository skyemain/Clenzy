package com.skyemain.clenzy

import android.content.*
import android.graphics.*
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import androidx.work.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ScanBackgroundTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Test fun backToHomeCancelsQueuedScan(){
        val manager=WorkManager.getInstance(context)
        val task=OneTimeWorkRequestBuilder<SmartWorker>().setInitialDelay(1,TimeUnit.DAYS).build()
        manager.enqueue(task).result.get()
        context.getSharedPreferences("SmartActivity",0).edit().putString("run",task.id.toString()).commit()
        try{
            ActivityScenario.launch(SmartActivity::class.java).use{
                val back=requireNotNull(device.wait(Until.findObject(By.text("Back to Home")),10000))
                assertNotNull(back);assertTrue(back.visibleBounds.bottom>device.displayHeight*.8)
                assertFalse(device.hasObject(By.text("Cancel scan")))
                back.click()
                val deadline=System.currentTimeMillis()+10000
                while(manager.getWorkInfoById(task.id).get().state!=WorkInfo.State.CANCELLED && System.currentTimeMillis()<deadline)Thread.sleep(100)
                assertEquals(WorkInfo.State.CANCELLED,manager.getWorkInfoById(task.id).get().state)
                assertFalse(context.getSharedPreferences("SmartActivity",0).contains("run"))
            }
        }finally{manager.cancelWorkById(task.id).result.get();context.getSharedPreferences("SmartActivity",0).edit().remove("run").commit()}
    }
    @Test fun foregroundScanFinishesAfterSwitchingAway(){
        for(permission in listOf("READ_MEDIA_IMAGES","READ_MEDIA_VIDEO","POST_NOTIFICATIONS"))device.executeShellCommand("pm grant ${context.packageName} android.permission.$permission")
        val resolver=context.contentResolver
        val fixtures=mutableListOf<android.net.Uri>()
        var id:UUID?=null
        val manager=WorkManager.getInstance(context)
        val notices=context.getSystemService(android.app.NotificationManager::class.java)
        try{
            val bitmap=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
            canvas.drawText("Payment confirmation",20f,180f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=62f})
            repeat(12){n->
                val uri=requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{put("_display_name","background-fixture-$n.png");put("mime_type","image/png")}))
                fixtures+=uri;resolver.openOutputStream(uri).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,requireNotNull(it))}
            };bitmap.recycle()
            context.getSharedPreferences("SmartActivity",0).edit().remove("run").putBoolean("askedNotifications",true).commit()
            ActivityScenario.launch(SmartActivity::class.java).use{scenario->
                scenario.onActivity{SmartActivity::class.java.getDeclaredMethod("startScan").apply{isAccessible=true}.invoke(it)}
                id=UUID.fromString(context.getSharedPreferences("SmartActivity",0).getString("run",null))
                val deadline=System.currentTimeMillis()+120000
                while(notices.activeNotifications.none{it.id==1947} && System.currentTimeMillis()<deadline)Thread.sleep(100)
                assertTrue("Foreground progress notification",notices.activeNotifications.any{it.id==1947})
                device.takeScreenshot(java.io.File(context.getExternalFilesDir(null),"Clenzy-AI-progress.png"))
                assertEquals(WorkInfo.State.RUNNING,manager.getWorkInfoById(id!!).get().state)
                device.pressHome()
                while(!manager.getWorkInfoById(id!!).get().state.isFinished && System.currentTimeMillis()<deadline)Thread.sleep(200)
                assertEquals("Scan finishes while another screen is open",WorkInfo.State.SUCCEEDED,manager.getWorkInfoById(id!!).get().state)
                assertTrue("Completion notification",notices.activeNotifications.any{it.id==ScanNotifications.notificationId})
                SmartStore(context).use{db->assertTrue(db.results(id.toString()).keys.map{it.uri}.containsAll(fixtures.map{it.toString()}))}
            }
        }finally{
            id?.let{manager.cancelWorkById(it).result.get()};fixtures.forEach{resolver.delete(it,null,null)}
            notices.cancel(1947);notices.cancel(ScanNotifications.notificationId)
            context.getSharedPreferences("SmartActivity",0).edit().remove("run").commit()
        }
    }
}
