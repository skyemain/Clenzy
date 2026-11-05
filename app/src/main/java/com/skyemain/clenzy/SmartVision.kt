package com.skyemain.clenzy

import android.content.ContentResolver
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.face.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import android.content.Context
import kotlin.math.*

internal class SmartVision(val context:Context):java.io.Closeable{
    private val text=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val faces=FaceDetection.getClient(FaceDetectorOptions.Builder().build())
    private val barcode=BarcodeScanning.getClient()
    private val label=ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    override fun close(){text.close();faces.close();barcode.close();label.close()}
    private fun image(uri:Uri):Bitmap{
        val r=context.contentResolver
        val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        r.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,opts)}
        require(opts.outWidth>0 && opts.outHeight>0)
        opts.inJustDecodeBounds=false;opts.inSampleSize=1
        while(max(opts.outWidth,opts.outHeight)/opts.inSampleSize>1200)opts.inSampleSize*=2
        return requireNotNull(r.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,opts)})
    }
    fun analyze(m:Medium,check:()->Unit):Insight{
        check()
        if (m.source == "Camera" || m.source == "WhatsApp sent") {
            return Insight(listOf(Finding("Protected","Possible personal original",true)), emptyList(),0.0)
        }
        val hashes=mutableListOf<Long>();val words=StringBuilder();val labels=mutableSetOf<String>()
        var person=false;var qr=false;var sharp=0.0;var quality:String?=null
        fun frame(b:Bitmap,rotation:Int){
            try{
                check();val visual=InputImage.fromBitmap(b,rotation)
                words.append(' ').append(Tasks.await(text.process(visual)).text)
                val document = SmartRules.important.containsMatchIn(words.toString().lowercase(java.util.Locale.ROOT))
                if (document) return
                person=person || Tasks.await(faces.process(visual)).isNotEmpty()
                if (person) return
                qr=qr || Tasks.await(barcode.process(visual)).isNotEmpty()
                if (qr) return
                labels.addAll(Tasks.await(label.process(visual)).map{it.text})
                val small=Bitmap.createScaledBitmap(b,64,64,true)
                val pixels=IntArray(4096);small.getPixels(pixels,0,64,0,0,64,64)
                if(small!==b)small.recycle()
                val l=pixels.map{(Color.red(it)*30+Color.green(it)*59+Color.blue(it)*11)/100}
                var sum=0.0;var squared=0.0
                for(y in 1..62)for(x in 1..62){val i=y*64+x;val d=4*l[i]-l[i-1]-l[i+1]-l[i-64]-l[i+64];sum+=d;squared+=d*d}
                sharp=squared/3844-(sum/3844).pow(2)
                quality=when{l.count{it<12}>3900->"Nearly all pixels are dark";l.count{it>245}>3900->"Nearly all pixels are overexposed";sharp<10 && l.max()-l.min()>40->"Very low edge detail · review sharpness";m.width in 1..159 && m.height in 1..159->"Very low resolution";else->null}
                var hash=0L;for(y in 0..7)for(x in 0..7)if(l[y*512+x*7]>l[y*512+x*7+7])hash=hash or (1L shl (y*8+x))
                hashes+=hash
            }finally{b.recycle()}
        }
        val uri=Uri.parse(m.uri)
        if(!m.video)frame(image(uri),m.orientation.takeIf{it in listOf(0,90,180,270)}?:0)
        else{
            val v=MediaMetadataRetriever()
            try{v.setDataSource(context,uri)
                if(Build.VERSION.SDK_INT>=27){
                    val scale=720.0/max(1,max(m.width,m.height))
                    for(percent in if(m.duration>3000)listOf(15,50,85) else listOf(50)){
                        if(person || qr || SmartRules.important.containsMatchIn(words.toString().lowercase(java.util.Locale.ROOT))) break
                        check();frame(requireNotNull(v.getScaledFrameAtTime(m.duration*10*percent,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,(m.width*scale).toInt().coerceIn(1,720),(m.height*scale).toInt().coerceIn(1,720))),0)
                    }
                }else throw java.io.IOException("Frame sampling unavailable")
            }finally{v.release()}
        }
        val age=(System.currentTimeMillis()/1000-m.stamp)/86400
        return Insight(SmartRules.assess(m.source,m.video,m.size,age,words.toString(),person,qr,labels,quality),hashes,sharp)
    }
}
