package com.skyemain.clenzy

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ScanNotificationTest {
    @Test fun workerCompletesAndPostsResultsWithoutAnActivity() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        if(Build.VERSION.SDK_INT>=33){
            listOf(Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO).forEach{
                automation.grantRuntimePermission(context.packageName,it)
            }
        }else automation.grantRuntimePermission(context.packageName,Manifest.permission.READ_EXTERNAL_STORAGE)
        val manager=WorkManager.getInstance(context)
        val alerts=context.getSystemService(NotificationManager::class.java)
        val task=OneTimeWorkRequestBuilder<SmartWorker>().build()
        val prefs=context.getSharedPreferences("SmartActivity",0)
        val previous=prefs.getString("run",null)
        try{
            alerts.cancel(ScanNotifications.notificationId)
            prefs.edit().putString("run",task.id.toString()).commit()
            manager.enqueue(task).result.get(10,TimeUnit.SECONDS)
            val deadline=android.os.SystemClock.elapsedRealtime()+60000
            while(android.os.SystemClock.elapsedRealtime()<deadline && !manager.getWorkInfoById(task.id).get().state.isFinished)Thread.sleep(200)
            assertEquals(WorkInfo.State.SUCCEEDED,manager.getWorkInfoById(task.id).get().state)
            val posted=alerts.activeNotifications.single{it.id==ScanNotifications.notificationId}
            assertNotNull(posted.notification.contentIntent)
            assertEquals(ScanNotifications.channel,posted.notification.channelId)
        }finally{
            alerts.cancel(ScanNotifications.notificationId)
            prefs.edit().putString("run",previous).commit()
        }
    }
}
