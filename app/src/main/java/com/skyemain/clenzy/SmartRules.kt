package com.skyemain.clenzy

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

internal data class Finding(val category:String, val reason:String, val protected:Boolean=false, val group:String="")
internal object SmartRules {
    const val version = 4
    val important = Regex("""\b(receipt|invoice|paid|payment confirmation|statement|booking|boarding pass|passport|driv(ing|er.s) licen[cs]e|prescription|diagnosis|ticket|reservation|insurance|tax return|appointment|account number|medical report)\b""")
    val promo = Regex("""\b(sale|discount|coupon|clearance|flash sale|special offer|promo code|shop now|free delivery|order now|offer ends|valid until)\b""")
    val greeting = Regex("""\b(good morning|good night|happy birthday|happy diwali|merry christmas|happy new year|happy sunday|daily blessing)\b""")
    fun origin(path:String,name:String):String {
        val p=path.lowercase(Locale.ROOT)
        return when {
            CleanupGroups.isScreenshot(name,p) -> "Screenshot"
            "screen record" in p || "screenrecord" in p -> "Screen recording"
            "whatsapp" in p || Regex("""(?i)^(IMG|VID)-\d{8}-WA\d+""").containsMatchIn(name) -> if("/sent/" in p) "WhatsApp sent" else "WhatsApp"
            "/camera" in p || p.startsWith("dcim") -> "Camera"
            "download" in p -> "Downloads"
            else -> listOf("Telegram","Messenger","Instagram").firstOrNull{it.lowercase() in p} ?: "Other"
        }
    }
    fun assess(source:String,video:Boolean,bytes:Long,ageDays:Long,ocr:String,face:Boolean,code:Boolean,
               labels:Set<String>,quality:String?,today:LocalDate=LocalDate.now()):List<Finding> {
        val t=ocr.lowercase(Locale.ROOT)
        val cameraPhoto=source=="Camera" && !video
        val screen=cameraPhoto && (labels.any{it.lowercase(Locale.ROOT) in setOf("computer","monitor","computer monitor","laptop","screen","desktop computer","computer keyboard","computer hardware","television")} ||
            Regex("""\b(taskbar|task manager|system settings|file explorer|address bar|terminal|localhost|sign out|new tab|view source|start menu|recycle bin|command prompt)\b""").findAll(t).map{it.value}.distinct().count()>=2)
        val protection=when {
            code -> "Contains a barcode or QR code"
            important.containsMatchIn(t) -> "May contain a document, payment or reservation"
            face || labels.any{it in setOf("Person","Baby","Handwriting","Handwritten text","Document")} -> "May contain a person or important information"
            (source=="Camera" && !screen) || source=="WhatsApp sent" -> "Possible personal original"
            else -> null
        }
        if(protection!=null)return listOf(Finding("Review manually",protection,false))
        if(screen)return listOf(Finding("Screen photos","Camera photo with computer or interface cues · review manually"))
        val out=mutableListOf<Finding>()
        val shared=source in setOf("WhatsApp","Telegram","Messenger","Instagram","Downloads")
        if(promo.containsMatchIn(t) && (shared || source=="Screenshot" || promo.findAll(t).count()>1)){
            val expired=Regex("""(?:offer ends|valid until)\s*[: -]?\s*(\d{4}-\d{2}-\d{2})""").find(t)?.groupValues?.get(1)?.let{runCatching{LocalDate.parse(it).isBefore(today)}.getOrNull()}==true
            out+=Finding(if(video)"Video clutter" else "Promotional images",if(expired)"Offer text includes a past end date" else "Promotional language in shared media")
        }
        if (shared && Regex("""\b(rsvp|register now|free entry|live concert|admit one)\b""").containsMatchIn(t) && t.length>20)
            out+=Finding(if(video)"Video clutter" else "Event flyers","Shared graphic with event or registration text")
        if (shared && !video && labels.any{it.equals("Comics",true)||it.equals("Cartoon",true)} && t.length>12)
            out+=Finding("Possible memes","Illustration label and a caption in shared media")
        if(shared && greeting.containsMatchIn(t)) out+=Finding(if(video)"Video clutter" else if(source=="WhatsApp")"WhatsApp clutter" else "Shared greetings","Shared media with greeting text")
        if(source=="Screenshot")out+=Finding("Screenshots",if(ageDays>90)"Screenshot saved over 90 days ago" else "Screenshot for manual review")
        if(shared && !video)out+=Finding("Downloaded images","Source suggests shared or downloaded media")
        if(video && shared)out+=Finding("Shared videos","Likely shared or downloaded video")
        if(video && source=="Screen recording")out+=Finding("Screen recordings","Recording folder or name")
        if(video && bytes>=100L*1024*1024)out+=Finding("Large videos","Storage review · size alone does not indicate clutter")
        if(quality!=null && !video)out+=Finding("Photo quality",quality)
        return out.ifEmpty{listOf(Finding("Review manually","No strong cleanup signal"))}
    }
    fun similar(a:Long,b:Long)=java.lang.Long.bitCount(a xor b)<=4
}
