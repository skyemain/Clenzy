package com.skyemain.clenzy

import android.content.*
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.core.app.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class SystemTrashTest {
    @Test fun androidTrashHandoffAndVerifiedHistory(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val resolver=context.contentResolver
        val uri=requireNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),ContentValues().apply{put("_display_name","system-trash-fixture.png");put("mime_type","image/png")}))
        try{
            val bitmap=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
            resolver.openOutputStream(uri).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,requireNotNull(it))};bitmap.recycle()
            val medium=resolver.query(uri,arrayOf("_size","date_modified"),null,null,null)!!.use{it.moveToFirst();Medium(uri.toString(),"system-trash-fixture.png",it.getLong(1),it.getLong(0),20,20,0,"Other",false,0,0)}
            context.getSharedPreferences("SmartActivity",0).edit().remove("run").commit()
            val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            ActivityScenario.launch(SmartActivity::class.java).use{scenario->
                scenario.onActivity{activity->
                    for((field,value) in mapOf("run" to "system-trash-test","category" to "Review manually","results" to mapOf(medium to Insight(listOf(Finding("Review manually","Fixture")),emptyList(),0.0))))SmartActivity::class.java.getDeclaredField(field).apply{isAccessible=true}.set(activity,value)
                    @Suppress("UNCHECKED_CAST")
                    val selected=SmartActivity::class.java.getDeclaredField("selected").apply{isAccessible=true}.get(activity) as MutableSet<Medium>
                    selected.add(medium)
                    SmartActivity::class.java.getDeclaredMethod("review").apply{isAccessible=true}.invoke(activity)
                }
                requireNotNull(device.wait(Until.findObject(By.text("Move to Trash (1)")),5000)).click()
                val cancel=device.wait(Until.findObject(By.text(Pattern.compile("(?i)cancel"))),3000)
                device.takeScreenshot(java.io.File(context.getExternalFilesDir(null),"Clenzy-system-trash.png"))
                if(cancel!=null){
                    assertNotEquals(context.packageName,cancel.applicationPackage)
                    cancel.click();assertTrue(device.wait(Until.gone(By.text(Pattern.compile("(?i)cancel"))),5000));device.waitForIdle()
                    scenario.onActivity{assertFalse(SmartActivity::class.java.getDeclaredField("submitting").apply{isAccessible=true}.getBoolean(it))}
                    assertFalse(TrashState.isTrashed(resolver,uri.toString()))
                    requireNotNull(device.wait(Until.findObject(By.text("Move to Trash (1)")),5000)).click()
                    val allow=device.wait(Until.findObject(By.text(Pattern.compile("(?i)(move to trash|allow|trash)"))),5000)
                    requireNotNull(allow).click()
                }
                val deadline=System.currentTimeMillis()+10000
                while(!TrashState.isTrashed(resolver,uri.toString())&&System.currentTimeMillis()<deadline)Thread.sleep(100)
                if(!TrashState.isTrashed(resolver,uri.toString())){device.takeScreenshot(java.io.File(context.getExternalFilesDir(null),"Clenzy-trash-failure.png"));device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null),"trash-failure.xml"))}
                assertTrue(TrashState.isTrashed(resolver,uri.toString()))
                assertTrue(device.wait(Until.hasObject(By.text("No items remaining")),5000))
                val history=org.json.JSONArray(context.getSharedPreferences("cleanup",0).getString("history","[]"))
                assertEquals(1,history.getJSONObject(0).getInt("files"))
            }
        }finally{resolver.delete(uri,Bundle().apply{putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_INCLUDE)})}
    }
}
