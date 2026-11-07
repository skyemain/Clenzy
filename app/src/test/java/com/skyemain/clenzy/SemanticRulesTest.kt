package com.skyemain.clenzy

import org.junit.Assert.*
import org.junit.Test

class SemanticRulesTest {
    @org.junit.Test fun documentFastPathPreservesProtection(){
        org.junit.Assert.assertTrue(SemanticRules.mustProtectDocument("payment receipt",false))
        org.junit.Assert.assertTrue(SemanticRules.mustProtectDocument("",true))
        org.junit.Assert.assertFalse(SemanticRules.mustProtectDocument("A lovely sunset",false))
    }

    private val photo=Medium("content://test/1","x.jpg",10,4000,800,600,0,"WhatsApp",false,0,0)
    private fun assess(text:String="",topic:String="other",face:Boolean=false,qr:Boolean=false)=
        SemanticRules.assess(photo,100,text,face,qr,emptySet(),null,listOf(SemanticMatch(topic,.2,.04)))
    @Test fun faceOnAdCanBeReviewedButPaymentCannot(){
        assertTrue(assess("flash sale discount", "promotion",true).any{it.category=="Promotional images"})
        assertTrue(assess("sale payment confirmation","promotion",true).all{it.protected})
    }
    @Test fun personalAndUnknownDecisions(){
        assertTrue(assess(topic="people").all{it.protected})
        assertTrue(assess(topic="pet").all{it.protected})
        assertTrue(assess(topic="greeting",qr=true).all{it.protected})
        assertEquals("Review manually",assess().single().category)
    }
    @Test fun screenTextAndSensitiveScreen(){
        assertEquals("Screen photos",assess("File Explorer New tab").first().category)
        assertTrue(assess("File Explorer New tab Invoice").all{it.protected})
    }
    @Test fun hindiDocumentAndGreeting(){
        assertTrue(assess("भुगतान चालान","promotion").all{it.protected})
        assertTrue(assess("सुप्रभात शुभकामन","greeting").any{it.category=="Shared greetings"})
    }
    @Test fun ambiguousModelScoresAbstain(){
        val result=SemanticRules.assess(photo,0,"",false,false,emptySet(),null,listOf(SemanticMatch("promotion",.13,.001)))
        assertEquals("Review manually",result.single().category)
    }
    @Test fun onePromotionalFrameDoesNotClassifyWholeVideo(){
        val result=SemanticRules.assess(photo.copy(video=true),0,"sale",false,false,emptySet(),null,listOf(SemanticMatch("promotion",.2,.04)))
        assertFalse(result.any{it.category=="Video clutter"})
    }
}
