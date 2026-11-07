package com.skyemain.clenzy

import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.work.*
import kotlinx.coroutines.*
import java.security.MessageDigest
import kotlin.math.abs

class SmartWorker(context:Context,p:WorkerParameters):CoroutineWorker(context,p){
    private val resolver=context.contentResolver
    private suspend fun progress(stage:String,n:Int=0,images:Int=0,videos:Int=0,eta:Long=-1){
        setProgress(workDataOf("stage" to stage,"total" to n,"images" to images,"videos" to videos,"eta" to eta))
        setForeground(ScanNotifications.foreground(applicationContext,id,stage,images+videos,n))
    }
    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        val job=currentCoroutineContext()
        val started=android.os.SystemClock.elapsedRealtime()
        try{
            progress("Finding photos and videos")
            val all=mutableListOf<Medium>();var accessible=0
            for(video in listOf(false,true)){
                val volumes=if(Build.VERSION.SDK_INT>=29)MediaStore.getExternalVolumeNames(applicationContext) else setOf("external")
                for(volume in volumes){
                    val collection=if(video)MediaStore.Video.Media.getContentUri(volume) else MediaStore.Images.Media.getContentUri(volume)
                    val cols=mutableListOf("_id","_display_name","date_modified","_size","width","height","bucket_display_name","datetaken",if(video)"duration" else "orientation")
                    if(Build.VERSION.SDK_INT>=29)cols+="relative_path"
                    val where=when{Build.VERSION.SDK_INT>=30->"is_pending=0 AND is_trashed=0";Build.VERSION.SDK_INT==29->"is_pending=0";else->null}
                    try{resolver.query(collection,cols.toTypedArray(),where,null,null)?.use{c->
                        accessible++
                        while(c.moveToNext()){
                            job.ensureActive();val name=c.getString(1)?:"Media"
                            val path=(if(cols.size>9)c.getString(9).orEmpty() else "")+"/"+c.getString(6).orEmpty()
                            all+=Medium(ContentUris.withAppendedId(collection,c.getLong(0)).toString(),name,c.getLong(2),c.getLong(3),c.getInt(4),c.getInt(5),if(video)c.getLong(8) else 0,SmartRules.origin(path,name),video,c.getLong(7),if(video)0 else c.getInt(8))
                        }
                    }}catch(_:SecurityException){continue}
                }
            }
            if(accessible==0)return@withContext Result.failure(workDataOf("error" to "Media access is unavailable."))
            val sizes=all.groupingBy{it.size}.eachCount();val results=linkedMapOf<Medium,Insight>();var photos=0;var videos=0;var last=0L
            SmartStore(applicationContext).use{db->SmartVision(applicationContext).use{vision->
                val cachedItems=db.matchingCache(all,id.toString())
                var measured=0;var analysisMillis=0L
                progress("Checking accessible photos and videos",all.size)
                for(m in all){
                    job.ensureActive();val cached=cachedItems.remove(m.uri)
                    // Resume from this run's persisted analyses before the WorkManager time limit.
                    if(cached==null && android.os.SystemClock.elapsedRealtime()-started>7*60_000L)return@withContext Result.retry()
                    val itemStarted=android.os.SystemClock.elapsedRealtime()
                    var i=cached?.takeIf{it.complete}?:try{vision.analyze(m){job.ensureActive()}}catch(e:Exception){
                        job.ensureActive();Insight(listOf(Finding("Review manually","Analysis unavailable · review manually",false)),emptyList(),0.0,complete=false)
                    }
                    if(i.complete && i.digest.isEmpty() && m.size>0 && sizes[m.size]!!>1){
                        val digest=try{val sha=MessageDigest.getInstance("SHA-256");resolver.openInputStream(Uri.parse(m.uri))?.use{stream->
                            val buf=ByteArray(65536);var total=0L;while(true){job.ensureActive();val n=stream.read(buf);if(n<0)break;sha.update(buf,0,n);total+=n}
                            if(total==m.size)android.util.Base64.encodeToString(sha.digest(),android.util.Base64.NO_WRAP) else ""
                        }.orEmpty()}catch(_:java.io.IOException){""}
                        i=i.copy(digest=digest)
                    }
                    if(cached==null){measured++;analysisMillis+=android.os.SystemClock.elapsedRealtime()-itemStarted}
                    results[m]=i;if(i!=cached)db.cache(m,i,id.toString())
                    if(m.video)videos++ else photos++
                    if(android.os.SystemClock.elapsedRealtime()-last>900){progress("Analyzing media on this device",all.size,photos,videos,if(measured>=5)analysisMillis/measured*(all.size-photos-videos)/1000 else -1);last=android.os.SystemClock.elapsedRealtime()}
                }
            }
            progress("Grouping matches",all.size,photos,videos)
            fun group(items:List<Medium>,category:String){
                if(items.size<2)return
                val ranked=items.sortedWith(compareByDescending<Medium>{it.width.toLong()*it.height}.thenByDescending{results.getValue(it).sharp}.thenBy{it.stamp})
                ranked.forEachIndexed{index,m->
                    val i=results.getValue(m)
                    results[m]=i.copy(findings=i.findings+Finding(category,"Similar content · compare before selecting",false,category+"/"+ranked.first().uri))
                }
            }
            results.keys.filter{results.getValue(it).digest.isNotEmpty()}.groupBy{results.getValue(it).digest}.values.forEach{group(it,if(it.first().video)"Duplicate videos" else "Duplicate photos")}
            val buckets=HashMap<String,MutableList<Medium>>();val matches=linkedMapOf<Medium,MutableList<Medium>>()
            for((m,i) in results){
                job.ensureActive();if(i.frames.isEmpty()||i.frames.any{java.lang.Long.bitCount(it) !in 5..59})continue
                val h=i.frames.first();val keys=(0..4).map{"${m.video}/$it/${(h ushr (it*13)) and 4095}"}
                val match=keys.asSequence().flatMap{buckets[it].orEmpty().asSequence()}.distinct().firstOrNull{p->
                    val other=results.getValue(p)
                    (i.digest.isEmpty()||i.digest!=other.digest) && abs(p.width.toDouble()/p.height.coerceAtLeast(1)-m.width.toDouble()/m.height.coerceAtLeast(1))<0.03 &&
                    (!m.video||abs(p.duration-m.duration)<700) && i.frames.size==other.frames.size && i.frames.indices.all{SmartRules.similar(i.frames[it],other.frames[it])}
                }
                if(match!=null)matches.getOrPut(match){mutableListOf(match)}.add(m)
                else keys.forEach{val bucket=buckets.getOrPut(it){mutableListOf()};if(bucket.size<48)bucket.add(m)}
            }
            matches.values.forEach{group(it,if(it.first().video)"Similar videos" else "Similar photos")}
            job.ensureActive();db.save(id.toString(),results)}
            job.ensureActive()
            if(applicationContext.getSharedPreferences("SmartActivity",0).getString("run",null)==id.toString()){
                try{ScanNotifications.completed(applicationContext)}catch(_:SecurityException){ /* Notifications can be disabled during a scan. */ }
            }
            Result.success()
        }catch(e:CancellationException){throw e}catch(e:Exception){Result.failure(workDataOf("error" to "Analysis stopped. Check media access and available storage, then retry."))}
    }
}
