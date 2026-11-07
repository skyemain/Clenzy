package com.skyemain.clenzy

import org.junit.Assert.*
import org.junit.Test

class SemanticRulesTest {
    @org.junit.Test fun documentFastPathDetectsDocuments(){
        org.junit.Assert.assertTrue(SemanticRules.isDocumentEvidence("payment receipt",false))
        org.junit.Assert.assertTrue(SemanticRules.isDocumentEvidence("",true))
        org.junit.Assert.assertFalse(SemanticRules.isDocumentEvidence("A lovely sunset",false))
    }

    private val photo=Medium("content://test/1","x.jpg",10,4000,800,600,0,"WhatsApp",false,0,0)
    private fun assess(text:String="",topic:String="other",face:Boolean=false,qr:Boolean=false)=
        SemanticRules.assess(photo,100,text,face,qr,emptySet(),null,listOf(SemanticMatch(topic,.2,.04)))
    @Test fun faceOnAdAndPaymentRemainReviewable(){
        assertTrue(assess("flash sale discount", "promotion",true).any{it.category=="Promotional images"})
        assertTrue(assess("sale payment confirmation","promotion",true).all{!it.protected && it.category=="Review manually"})
    }
    @Test fun personalAndUnknownDecisions(){
        assertTrue(assess(topic="people").all{!it.protected && it.category=="Review manually"})
        assertTrue(assess(topic="pet").all{!it.protected && it.category=="Review manually"})
        assertTrue(assess(topic="greeting",qr=true).all{!it.protected && it.category=="Review manually"})
        assertEquals("Review manually",assess().single().category)
    }
    @Test fun screenTextAndSensitiveScreen(){
        assertEquals("Screen photos",assess("File Explorer New tab").first().category)
        assertTrue(assess("File Explorer New tab Invoice").all{!it.protected && it.category=="Review manually"})
    }
    @Test fun hindiDocumentAndGreeting(){
        assertTrue(assess("भुगतान चालान","promotion").all{!it.protected && it.category=="Review manually"})
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
