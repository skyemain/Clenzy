package com.skyemain.clenzy

import android.content.*
import androidx.test.core.app.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAuditTest {
    @Test fun everySettingsActionAndDismissal(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        fun click(text:String){requireNotNull(device.wait(Until.findObject(By.text(text)),5000)).click()}
        ActivityScenario.launch<HomeActivity>(Intent(context,HomeActivity::class.java).putExtra("settings",true)).use{
            click("Cleanup history");click("Done");assertTrue(device.wait(Until.gone(By.text("Done")),5000))
            click("About Clenzy");assertNotNull(device.wait(Until.findObject(By.text("Clenzy ${BuildConfig.VERSION_NAME}")),5000));click("Done");device.wait(Until.gone(By.text("Done")),5000)
            click("Privacy & access");click("Photos & videos permission")
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")),5000));device.pressBack()
            click("Privacy & access");click("Shared files access")
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")),5000));device.pressBack()
            click("Clenzy cache")
            assertNotNull(device.wait(Until.findObject(By.text("Clenzy cache")),5000));assertFalse(device.hasObject(By.text("Select extra copies")))
            assertTrue(device.wait(Until.hasObject(By.text("No temporary files to clear")),5000))
            assertFalse(device.hasObject(By.text("Sort")))
            assertFalse(device.hasObject(By.text("Clear cache")))
            device.pressBack();assertNotNull(device.wait(Until.findObject(By.text("About Clenzy")),5000))
        }
    }
}
