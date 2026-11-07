package com.skyemain.clenzy

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartCleanTest {
    @Test fun oldProtectedResultsRemainReviewable(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val m=Medium("content://test/old","old.jpg",2,123,20,20,0,"Camera",false,0,0)
        SmartStore(context).use{db->
            db.save("migration-test",mapOf(m to Insight(listOf(Finding("Protected","Legacy",true)),emptyList(),0.0)))
            val findings=db.results("migration-test").getValue(m).findings
            assertEquals("Review manually",findings.single().category)
            assertFalse(findings.single().protected)
        }
    }
    @Test fun actualBundledInferenceAndCacheInvalidation(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val resolver=context.contentResolver
        val uri=requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply{
            put("_display_name","Clenzy synthetic receipt.png");put("mime_type","image/png")
        }))
        try{
            val image=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(image);canvas.drawColor(Color.WHITE)
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=62f}
            canvas.drawText("Payment confirmation",20f,180f,paint);canvas.drawText("Invoice 12345",20f,300f,paint)
            resolver.openOutputStream(uri).use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,requireNotNull(it)))};image.recycle()
            val m=Medium(uri.toString(),"receipt.png",1,300,800,600,0,"WhatsApp",false,0,0)
            val finding=SmartVision(context).use{it.analyze(m){}}
            assertTrue(finding.findings.all{!it.protected && it.category=="Review manually"});assertEquals(1,finding.frames.size)
            SmartStore(context).use{db->db.cache(m,finding,"first-run");assertTrue(db.matchingCache(listOf(m),"new-run").isEmpty());assertEquals(finding,db.matchingCache(listOf(m),"first-run")[m.uri]);assertEquals(finding,db.cached(m));assertNull(db.cached(m.copy(stamp=2)));assertEquals(finding,db.matchingCache(listOf(m))[m.uri]);assertTrue(db.matchingCache(listOf(m.copy(stamp=2))).isEmpty())}
        }finally{resolver.delete(uri,null,null)}
    }
}
