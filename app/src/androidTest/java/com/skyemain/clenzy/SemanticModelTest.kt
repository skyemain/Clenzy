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
                    assertTrue(file,prediction.score.isFinite())
                    // The score may correctly abstain across image resamplers; protection is an end-to-end decision.
                    val resolver=context.contentResolver
                    val uri=requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,android.content.ContentValues().apply{
                        put("_display_name","Clenzy semantic "+file)
                        put("mime_type","image/png")
                    }))
                    try{
                        resolver.openOutputStream(uri).use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,requireNotNull(it))}
                        val medium=Medium(uri.toString(),file,1,100,bitmap.width,bitmap.height,0,"WhatsApp",false,0,0)
                        val start=android.os.SystemClock.elapsedRealtime()
                        val insight=SmartVision(context).use{it.analyze(medium){}}
                        val parallelMs=android.os.SystemClock.elapsedRealtime()-start
                        val serialStart=android.os.SystemClock.elapsedRealtime()
                        val serial=SmartVision(context,false).use{it.analyze(medium){}}
                        val serialMs=android.os.SystemClock.elapsedRealtime()-serialStart
                        android.util.Log.i("ClenzyBenchmark","$file serial=$serialMs ms overlapped=$parallelMs ms")
                        assertEquals(serial,insight)
                        assertTrue("Personal media stays reviewable: "+file+" "+insight.findings,insight.findings.all{!it.protected && it.category in setOf("Review manually",if(expected=="people")"People" else "Animals")})
                    }finally{resolver.delete(uri,null,null)}
                }finally{bitmap.recycle()}
            }
        }
    }
}
