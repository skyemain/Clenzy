package com.skyemain.clenzy

import org.junit.Assert.*
import org.junit.Test

class SmartRulesTest {
    private fun assess(text:String="",source:String="WhatsApp",face:Boolean=false,qr:Boolean=false,video:Boolean=false)=
        SmartRules.assess(source,video,160L*1024*1024,120,text,face,qr,emptySet(),"Low detail")
    @Test fun photographedScreenRequiresReviewUnlessSensitive(){
        fun classify(text:String,labels:Set<String>)=SmartRules.assess("Camera",false,100,0,text,false,false,labels,null).single()
        assertEquals("Screen photos",classify("New tab File Explorer",emptySet()).category)
        assertFalse(classify("",setOf("Laptop")).protected)
        assertFalse(classify("payment confirmation",setOf("Laptop")).protected)
        assertFalse(classify("",emptySet()).protected)
    }
    @Test fun paymentAndBoardingTakePriorityOverSale(){assertTrue(assess("sale payment confirmation","Screenshot").all{!it.protected && it.category=="Review manually"});assertTrue(assess("boarding pass",qr=true).all{!it.protected && it.category=="Review manually"})}
    @Test fun personalMediaProtected(){assertTrue(assess(face=true).all{!it.protected && it.category=="Review manually"});assertTrue(assess(source="Camera",video=true).all{!it.protected && it.category=="Review manually"})}
    @Test fun expiredPoster(){assertTrue(assess("Flash sale valid until 2020-01-01").any{"past end date" in it.reason})}
    @Test fun sharedGreeting(){assertTrue(assess("Happy Diwali").any{it.category=="WhatsApp clutter"})}
    @Test fun sharedFolderAloneDoesNotImplyAd(){assertFalse(assess().any{it.category=="Promotional images"||it.category=="WhatsApp clutter"})}
    @Test fun originAndSimilarity(){assertEquals("WhatsApp",SmartRules.origin("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images","file.jpg"));assertEquals("Screenshot",SmartRules.origin("DCIM/Screen captures","x.png"));assertFalse(SmartRules.similar(0,255));assertTrue(SmartRules.similar(0,7))}
}
