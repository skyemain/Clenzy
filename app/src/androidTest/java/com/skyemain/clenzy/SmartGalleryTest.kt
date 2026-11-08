package com.skyemain.clenzy

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
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
    @Test fun imageOverlaySelectionWithoutLocks(){
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
                    for((field,value) in mapOf("run" to "", "category" to "Review manually","results" to (0 until 12).associate { n -> (if(n==0)m else m.copy(name="Fixture $n")) to Insight(listOf(Finding("Review manually","Previously auto-protected keeper",true)),emptyList(),0.0) })){
                        SmartActivity::class.java.getDeclaredField(field).apply{isAccessible=true}.set(activity,value)
                    }
                    SmartActivity::class.java.getDeclaredMethod("review").apply{isAccessible=true}.invoke(activity)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity{activity->
                    val grid=requireNotNull(find(activity.window.decorView,GridView::class.java))
                    assertEquals(3,grid.numColumns)
                    assertTrue(grid.width>=activity.resources.displayMetrics.widthPixels*0.95)
                    assertEquals(12,grid.count)
                    assertTrue(grid.childCount>=9)
                    assertNull(find(grid,CheckBox::class.java))
                    val photo=requireNotNull(find(grid,ImageView::class.java))
                    photo.performClick()
                    val selected=SmartActivity::class.java.getDeclaredField("selected").apply{isAccessible=true}.get(activity) as Set<*>
                    assertTrue(selected.contains(m))
                    assertTrue(photo.isSelected)
                    photo.performClick()
                    assertFalse(selected.contains(m))
                    assertFalse(photo.isSelected)
                    context.getSharedPreferences("locked_media",0).edit().putString(m.uri,"legacy").commit()
                    photo.performClick()
                    assertTrue(selected.contains(m))
                    photo.performClick()


                }
                val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Sort")),5000)).click()
                requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Oldest")),5000)).click()
                device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.text("Oldest")),5000)
                requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Filter")),5000)).click()
                requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Photo")),5000)).click()
                device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.text("Photo")),5000)
                requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Select all")),5000)).click()
                assertNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Move to Trash (12)")),5000))
                requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Clear all")),5000)).click()
                assertTrue(device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.text("Move to Trash (12)")),5000))
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
                Thread.sleep(3000)
                assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(context.getExternalFilesDir(null),"Clenzy-AI-gallery.png")))
                scenario.onActivity{activity->requireNotNull(find(requireNotNull(find(activity.window.decorView,GridView::class.java)),ImageView::class.java)).performLongClick()}
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
                Thread.sleep(500)
                assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(context.getExternalFilesDir(null),"Clenzy-AI-menu.png")))
            }
        }finally{
            context.getSharedPreferences("locked_media",0).edit().clear().commit()
            resolver.delete(uri,null,null)
        }
    }
}
