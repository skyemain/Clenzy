package com.skyemain.clenzy

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.face.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.Locale
import kotlin.math.*

internal class SmartVision(val context:Context, private val parallel:Boolean=true):java.io.Closeable {
    private val text=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagari=TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    private val faces=FaceDetection.getClient(FaceDetectorOptions.Builder().build())
    private val barcode=BarcodeScanning.getClient()
    private val semantic by lazy { SemanticVision(context) }
    private var semanticUsed=false
    override fun close(){
        text.close();devanagari.close();faces.close();barcode.close()
        if(semanticUsed)semantic.close()
    }
    private fun image(m:Medium):Bitmap {
        val uri=Uri.parse(m.uri)
        val resolver=context.contentResolver
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        resolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,options)}
        require(options.outWidth>0 && options.outHeight>0)
        options.inJustDecodeBounds=false;options.inSampleSize=1
        while(max(options.outWidth,options.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2
        val bitmap=requireNotNull(resolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,options)})
        val exif=runCatching{resolver.openInputStream(uri)?.use{ExifInterface(it)}}.getOrNull()
        val matrix=Matrix()
        val rotation=exif?.rotationDegrees?.takeIf{it!=0}?:m.orientation
        matrix.postRotate(rotation.toFloat())
        if(exif?.isFlipped==true)matrix.postScale(-1f,1f)
        if(matrix.isIdentity)return bitmap
        val corrected=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        if(corrected!==bitmap)bitmap.recycle()
        return corrected
    }
    fun analyze(m:Medium,check:()->Unit):Insight {
        check()
        val hashes=mutableListOf<Long>()
        val words=StringBuilder()
        val labels=mutableSetOf<String>()
        val semantics=mutableListOf<SemanticMatch>()
        var person=false;var qr=false;var sharp=0.0;var quality:String?=null
        fun frame(bitmap:Bitmap) {
            try {
                check()
                val input=InputImage.fromBitmap(bitmap,0)
                // Start independent recognizers together; retain the bitmap until every task finishes.
                val tasks=mutableListOf<com.google.android.gms.tasks.Task<*>>()
                fun <T> launch(task:com.google.android.gms.tasks.Task<T>):com.google.android.gms.tasks.Task<T>{
                    tasks.add(task)
                    if(!parallel)Tasks.await(task)
                    return task
                }
                try {
                    val latin=launch(text.process(input))
                    val hindi=launch(devanagari.process(input))
                    val codes=launch(barcode.process(input))
                    val detectedFaces=launch(faces.process(input))
                    Tasks.await(com.google.android.gms.tasks.Tasks.whenAllComplete(tasks))
                    words.append(' ').append(Tasks.await(latin).text).append(' ').append(Tasks.await(hindi).text)
                    qr=qr || Tasks.await(codes).isNotEmpty()
                    person=person || Tasks.await(detectedFaces).isNotEmpty()
                    if(!SemanticRules.isDocumentEvidence(words.toString(),qr)){
                        check();semanticUsed=true;semantics+=semantic.classify(bitmap)
                    }
                    check()
                } finally {
                    // Native ML tasks cannot be cancelled by recycling their input.
                    if(tasks.isNotEmpty())Tasks.await(com.google.android.gms.tasks.Tasks.whenAllComplete(tasks))
                }
                val small=Bitmap.createScaledBitmap(bitmap,256,256,true)
                val pixels=IntArray(256*256)
                small.getPixels(pixels,0,256,0,0,256,256)
                if(small!==bitmap)small.recycle()
                val luminance=IntArray(pixels.size){i->val c=pixels[i];(Color.red(c)*30+Color.green(c)*59+Color.blue(c)*11)/100}
                var sum=0.0;var squared=0.0
                for(y in 1..254)for(x in 1..254){
                    val i=y*256+x
                    val delta=4*luminance[i]-luminance[i-1]-luminance[i+1]-luminance[i-256]-luminance[i+256]
                    sum+=delta;squared+=delta.toDouble()*delta
                }
                sharp=squared/64516-(sum/64516).pow(2)
                quality=when {
                    luminance.count{it<8}>pixels.size*.98 -> "Very dark exposure · review manually"
                    luminance.count{it>249}>pixels.size*.98 -> "Severely clipped highlights · review manually"
                    sharp<8 && luminance.max()-luminance.min()>50 -> "Low detail across the image · review manually"
                    m.width in 1..159 && m.height in 1..159 -> "Very low resolution"
                    else -> null
                }
                val hashImage=Bitmap.createScaledBitmap(bitmap,9,8,true)
                val points=IntArray(72);hashImage.getPixels(points,0,9,0,0,9,8)
                var hash=0L
                fun brightness(c:Int)=Color.red(c)*30+Color.green(c)*59+Color.blue(c)*11
                for(y in 0..7)for(x in 0..7)if(brightness(points[y*9+x])>brightness(points[y*9+x+1]))hash=hash or (1L shl (y*8+x))
                hashImage.recycle();hashes+=hash
            } finally { bitmap.recycle() }
        }
        if(!m.video)frame(image(m))
        else {
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(context,Uri.parse(m.uri))
                val scale=960.0/max(1,max(m.width,m.height))
                if(Build.VERSION.SDK_INT<27)throw java.io.IOException("Frame sampling unavailable")
                val samples=if(m.duration>30000)listOf(5,25,50,75,95) else if(m.duration>3000)listOf(15,50,85) else listOf(50)
                for(percent in samples){
                    check()
                    frame(requireNotNull(retriever.getScaledFrameAtTime(m.duration*10*percent,MediaMetadataRetriever.OPTION_CLOSEST,
                        (m.width*scale).toInt().coerceIn(1,960),(m.height*scale).toInt().coerceIn(1,960))))
                }
            } finally {retriever.release()}
        }
        val age=(System.currentTimeMillis()/1000-m.stamp)/86400
        val findings=SemanticRules.assess(m,age,words.toString(),person,qr,labels,quality,semantics)
        return Insight(findings,hashes,sharp)
    }
}
