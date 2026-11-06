package com.skyemain.clenzy
import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SemanticModelTest {
    @Test fun bundledModelRecognizesPublicPortraitPet(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        SemanticVision(context).use{engine->
            for((file,expected) in listOf("person.png" to "people","pet.png" to "pet")){
                val bitmap=assets.open(file).use{BitmapFactory.decodeStream(it)}
                try{
                    val prediction=engine.classify(bitmap)
                    assertEquals(file,expected,prediction.label)
                    assertTrue(file,prediction.reliable)
                }finally{bitmap.recycle()}
            }
        }
    }
}
