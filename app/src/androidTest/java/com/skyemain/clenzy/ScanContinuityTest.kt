package com.skyemain.clenzy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanContinuityTest {
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
