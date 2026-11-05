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
    @Test fun personalOriginalsProtectedWithoutOpeningMedia(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        SmartVision(context).use{vision->
            for(source in listOf("Camera","WhatsApp sent")){
                val item=Medium("content://invalid/unopened","personal.mp4",1,20,100,100,5000,source,true,0,0)
                val insight=vision.analyze(item){}
                assertTrue(insight.complete && insight.findings.single().protected && insight.frames.isEmpty())
            }
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
            assertTrue(finding.findings.any{it.protected});assertTrue(finding.frames.isEmpty())
            SmartStore(context).use{db->db.cache(m,finding);assertEquals(finding,db.cached(m));assertNull(db.cached(m.copy(stamp=2)));assertEquals(finding,db.matchingCache(listOf(m))[m.uri]);assertTrue(db.matchingCache(listOf(m.copy(stamp=2))).isEmpty())}
        }finally{resolver.delete(uri,null,null)}
    }
}
