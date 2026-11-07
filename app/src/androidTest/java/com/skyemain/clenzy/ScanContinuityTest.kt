package com.skyemain.clenzy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanContinuityTest {
    @Test fun verifiesActualTrashedMedia(){
        if(android.os.Build.VERSION.SDK_INT<30)return
        val context=ApplicationProvider.getApplicationContext<Context>()
        val resolver=context.contentResolver
        val uri=requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,android.content.ContentValues().apply{
            put("_display_name","trash-state-fixture.png");put("mime_type","image/png")
        }))
        try{
            val bitmap=android.graphics.Bitmap.createBitmap(10,10,android.graphics.Bitmap.Config.ARGB_8888)
            resolver.openOutputStream(uri).use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,requireNotNull(it))};bitmap.recycle()
            assertFalse(TrashState.isTrashed(resolver,uri.toString()))
            assertEquals(1,resolver.update(uri,android.content.ContentValues().apply{put("is_trashed",1)},null,null))
            assertTrue(TrashState.isTrashed(resolver,uri.toString()))
        }finally{
            val args=android.os.Bundle().apply{putInt(android.provider.MediaStore.QUERY_ARG_MATCH_TRASHED,android.provider.MediaStore.MATCH_INCLUDE)}
            resolver.delete(uri,args)
        }
    }
    @Test fun trashUpdatesPersistedResultsWithoutLosingRemainingChoices(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val a=Medium("content://clenzy/test/a","a",1,100,50,50,0,"Other",false,0,0)
        val b=a.copy(uri="content://clenzy/test/b",name="b")
        val insight=Insight(listOf(Finding("Screenshots","Screenshot")),listOf(12L),10.0)
        SmartStore(context).use{db->
            db.save("continuity-test",mapOf(a to insight,b to insight))
            db.choice(b,"Review later")
            db.removeResults("continuity-test",setOf(a.uri))
        }
        SmartStore(context).use{db->
            val remaining=db.results("continuity-test")
            assertEquals(setOf(b),remaining.keys)
            assertEquals("Review later",remaining.getValue(b).findings.single().category)
            db.removeResults("continuity-test",setOf(b.uri))
            assertTrue(db.results("continuity-test").isEmpty())
        }
    }
}
