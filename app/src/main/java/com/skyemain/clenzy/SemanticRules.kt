package com.skyemain.clenzy

import java.util.Locale

internal object SemanticRules {
    fun assess(m:Medium,age:Long,ocr:String,face:Boolean,code:Boolean,labels:Set<String>,quality:String?,matches:List<SemanticMatch>):List<Finding>{
        val text=ocr.lowercase(Locale.ROOT)
        val reliable=matches.filter{it.reliable}
        val topics=reliable.map{it.label}.toSet()
        val hindiDocument=Regex("रसीद|चालान|भुगतान|खाता संख्या|आधार|पासपोर्ट|चिकित्सा|आरक्षण").containsMatchIn(text)
        val document=code || SmartRules.important.containsMatchIn(text) || hindiDocument ||
            topics.any{it in setOf("identity","receipt","document")}
        if(document)return listOf(Finding("Protected",if(code)"Contains a QR code or barcode" else "Document or payment information detected",true))
        val personal=topics.any{it in setOf("people","pet","landscape","food")}
        if(personal)return listOf(Finding("Protected","Visual model suggests personal memories or everyday photographs",true))
        val graphical=topics.any{it in setOf("promotion","greeting","meme","event","shopping","screen","chat")}
        if(face && !graphical)return listOf(Finding("Protected","Person detected without a clear graphic or screen context",true))
        val corroborated=if(m.video) reliable.groupingBy{it.label}.eachCount().filterValues{it>=2}.keys else topics
        val result=mutableListOf<Finding>()
        val screenText=Regex("""\b(taskbar|task manager|file explorer|new tab|localhost|recycle bin|command prompt)\b""").findAll(text).map{it.value}.distinct().count()>=2
        val offers=SmartRules.promo.findAll(text).map{it.value}.distinct().count()

        if("screen" in corroborated || screenText)result+=Finding(if(m.video)"Screen recordings" else "Screen photos","Visual model recognizes a screen or software interface · review")
        if(("promotion" in corroborated || offers>=2) && (SmartRules.promo.containsMatchIn(text)||Regex("छूट|ऑफर|बिक्री").containsMatchIn(text)))
            result+=Finding(if(m.video)"Video clutter" else "Promotional images","Promotional visual layout and offer text agree")
        if("greeting" in corroborated && (SmartRules.greeting.containsMatchIn(text)||Regex("सुप्रभात|शुभ रात्रि|जन्मदिन|शुभकामन").containsMatchIn(text)))
            result+=Finding("Shared greetings","Greeting-card appearance and greeting text agree")
        if("meme" in corroborated && text.length>12)result+=Finding("Possible memes","Captioned humor or cartoon appearance · review")
        if("event" in corroborated && text.length>12)result+=Finding("Event flyers","Event-style graphic with visible text · review dates")
        if("shopping" in corroborated)result+=Finding("Shopping images","Visual model suggests a product or shopping page")
        if("chat" in corroborated)result+=Finding("Chat screenshots","Messaging interface · review for important information")
        if(m.source=="Screenshot")result+=Finding("Screenshots","Screenshot origin · manual review")
        // Uncertain predictions are explicitly reviewable and never automatically selected.
        if(result.isEmpty()){
            if(m.source=="Camera"||face)return listOf(Finding("Protected","Possible personal original · content uncertain",true))
            result+=Finding("Review manually","Visual evidence is inconclusive")
        }
        if(quality!=null && !m.video && !face)result+=Finding("Photo quality",quality)
        if(m.video && m.size>=100L*1024*1024)result+=Finding("Large videos","Large file · review its content before deciding")
        return result
    }
}
