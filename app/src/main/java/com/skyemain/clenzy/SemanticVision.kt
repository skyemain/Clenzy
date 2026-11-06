package com.skyemain.clenzy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.*
import java.io.File
import java.nio.FloatBuffer
import org.json.JSONArray
import kotlin.math.sqrt

/** SigLIP scores are cosine similarities, not calibrated deletion probabilities. */
internal data class SemanticMatch(val label:String,val score:Double,val margin:Double) {
    val reliable get()=score>=0.07 && margin>=0.01
}
internal class SemanticVision(context:Context):java.io.Closeable {
    private val env=OrtEnvironment.getEnvironment()
    private val session:OrtSession
    private val labels:List<Pair<String,FloatArray>>
    init {
        val target=File(context.noBackupFilesDir,"siglip2-5f2b401c.onnx")
        if(!target.exists()){
            val temporary=File(target.path+".tmp")
            context.assets.open("siglip2-vision.onnx").use{input->temporary.outputStream().use{input.copyTo(it)}}
            check(temporary.renameTo(target))
        }
        session=OrtSession.SessionOptions().use{
            it.setIntraOpNumThreads(2)
            env.createSession(target.path,it)
        }
        val array=JSONArray(context.assets.open("semantic-vectors.json").bufferedReader().use{it.readText()})
        labels=(0 until array.length()).map{
            val obj=array.getJSONObject(it);val values=obj.getJSONArray("vector")
            obj.getString("label") to FloatArray(values.length()){j->values.getDouble(j).toFloat()}
        }
    }
    fun classify(image:Bitmap):SemanticMatch {
        val scaled=Bitmap.createScaledBitmap(image,224,224,true)
        val pixels=IntArray(224*224)
        scaled.getPixels(pixels,0,224,0,0,224,224)
        if(scaled!==image)scaled.recycle()
        val input=FloatArray(pixels.size*3)
        pixels.forEachIndexed{i,color->
            input[i]=Color.red(color)/127.5f-1f
            input[i+pixels.size]=Color.green(color)/127.5f-1f
            input[i+pixels.size*2]=Color.blue(color)/127.5f-1f
        }
        OnnxTensor.createTensor(env,FloatBuffer.wrap(input),longArrayOf(1,3,224,224)).use{tensor->
            session.run(mapOf("pixel_values" to tensor),setOf("pooler_output")).use{output->
                @Suppress("UNCHECKED_CAST")
                val vector=(output[0].value as Array<FloatArray>)[0]
                val norm=sqrt(vector.sumOf{it.toDouble()*it}).coerceAtLeast(1e-12)
                val scores=labels.map{(name,reference)->name to vector.indices.sumOf{vector[it].toDouble()*reference[it]}/norm}.sortedByDescending{it.second}
                return SemanticMatch(scores[0].first,scores[0].second,scores[0].second-scores[1].second)
            }
        }
    }
    override fun close(){session.close()}
}
