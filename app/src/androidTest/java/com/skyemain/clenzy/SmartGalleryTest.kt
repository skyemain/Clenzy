package com.skyemain.clenzy

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ToggleButton
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SmartGalleryTest {
    private fun <T:View> find(view:View,type:Class<T>):T?{
        if(type.isInstance(view))return type.cast(view)
        if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i),type)?.let{return it}
        return null
    }
    @Test fun imageOverlaySelectionAndLock(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val resolver=context.contentResolver
        val uri=requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{
            put("_display_name","Clenzy gallery fixture.png");put("mime_type","image/png")
        }))
        val m=Medium(uri.toString(),"Clenzy gallery fixture.png",1,100,512,512,0,"Other",false,0,0)
        try{
            InstrumentationRegistry.getInstrumentation().context.assets.open("pet.png").use{input->resolver.openOutputStream(uri).use{input.copyTo(requireNotNull(it))}}
            ActivityScenario.launch(SmartActivity::class.java).use{scenario->
                scenario.onActivity{activity->
                    for((field,value) in mapOf("run" to "", "category" to "Review manually","results" to mapOf(m to Insight(listOf(Finding("Review manually","Test review")),emptyList(),0.0)))){
                        SmartActivity::class.java.getDeclaredField(field).apply{isAccessible=true}.set(activity,value)
                    }
                    SmartActivity::class.java.getDeclaredMethod("review").apply{isAccessible=true}.invoke(activity)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity{activity->
                    val check=requireNotNull(find(activity.window.decorView,CheckBox::class.java))
                    assertEquals("",check.text.toString())
                    check.performClick()
                    val selected=SmartActivity::class.java.getDeclaredField("selected").apply{isAccessible=true}.get(activity) as Set<*>
                    assertTrue(selected.contains(m))
                    requireNotNull(find(activity.window.decorView,ToggleButton::class.java)).performClick()
                    assertFalse(selected.contains(m))
                    assertTrue(MediaLocks(context).contains(m.uri))
                }
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
                Thread.sleep(1500)
                assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(context.getExternalFilesDir(null),"Clenzy-AI-gallery.png")))
            }
        }finally{
            MediaLocks(context).set(m,false)
            resolver.delete(uri,null,null)
        }
    }
}
