package com.skyemain.clenzy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

internal data class Medium(val uri:String,val name:String,val stamp:Long,val size:Long,val width:Int,val height:Int,
    val duration:Long,val source:String,val video:Boolean,val taken:Long,val orientation:Int) {
    val analysisRevision get() = if(source=="Camera" && !video) "/screen-v1" else ""
    val freshness get() = "$stamp/$size/$orientation/$source/$name/${SmartRules.version}"
}
internal data class Insight(val findings:List<Finding>,val frames:List<Long>,val sharp:Double,val digest:String="",val complete:Boolean=true){
    fun encode()=JSONObject().apply{
        put("labels",JSONArray().also{a->findings.forEach{a.put(JSONArray(listOf(it.category,it.reason,it.protected,it.group)))}})
        put("frames",JSONArray(frames.map{it.toString()}));put("sharp",sharp);put("hash",digest);put("ok",complete)
    }.toString()
    companion object {
        fun decode(s:String):Insight{
            val j=JSONObject(s);val a=j.getJSONArray("labels");val h=j.getJSONArray("frames")
            return Insight((0 until a.length()).map{val f=a.getJSONArray(it);Finding(f.getString(0),f.getString(1),f.getBoolean(2),f.optString(3))},
                (0 until h.length()).map{h.getString(it).toLong()},j.getDouble("sharp"),j.getString("hash"),j.getBoolean("ok"))
        }
    }
}
internal class SmartStore(context:Context):SQLiteOpenHelper(context,"smart-media.db",null,1){
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE cache(uri TEXT PRIMARY KEY, freshness TEXT, analysis TEXT)")
        db.execSQL("CREATE TABLE results(run TEXT, uri TEXT, metadata TEXT, analysis TEXT, PRIMARY KEY(run,uri))")
        db.execSQL("CREATE TABLE choices(uri TEXT, freshness TEXT, choice TEXT, PRIMARY KEY(uri,freshness))")
    }
    override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int)=Unit
    fun cached(m:Medium):Insight?=readableDatabase.rawQuery("SELECT analysis FROM cache WHERE uri=? AND freshness=?",arrayOf(m.uri,m.freshness+m.analysisRevision)).use{if(it.moveToFirst()) Insight.decode(it.getString(0)) else null}
    fun matchingCache(media:List<Medium>):MutableMap<String,Insight>{
        val wanted=media.associateBy{it.uri}
        val found=HashMap<String,Insight>()
        readableDatabase.rawQuery("SELECT uri,freshness,analysis FROM cache",null).use{cursor->
            while(cursor.moveToNext()){
                val uri=cursor.getString(0)
                if(wanted[uri]?.let{it.freshness+it.analysisRevision}==cursor.getString(1)) found[uri]=Insight.decode(cursor.getString(2))
            }
        }
        return found
    }
    fun cache(m:Medium,i:Insight){writableDatabase.execSQL("INSERT OR REPLACE INTO cache VALUES(?,?,?)",arrayOf(m.uri,m.freshness+m.analysisRevision,i.encode()))}
    fun retain(group:String,m:Medium){
        writableDatabase.execSQL("INSERT OR REPLACE INTO choices VALUES(?,?,?)",arrayOf(group,"keeper",m.uri))
    }
    fun choice(m:Medium,value:String){writableDatabase.execSQL("INSERT OR REPLACE INTO choices VALUES(?,?,?)",arrayOf(m.uri,m.freshness,value))}
    fun save(run:String,all:Map<Medium,Insight>){
        val db=writableDatabase;db.beginTransaction()
        try{db.execSQL("DELETE FROM results");all.forEach{(m,i)->
            val meta=JSONArray(listOf(m.name,m.stamp,m.size,m.width,m.height,m.duration,m.source,m.video,m.taken,m.orientation))
            db.execSQL("INSERT INTO results VALUES(?,?,?,?)",arrayOf(run,m.uri,meta.toString(),i.encode()))
        };db.execSQL("DELETE FROM cache WHERE uri NOT IN (SELECT uri FROM results)");db.setTransactionSuccessful()}finally{db.endTransaction()}
    }
    fun results(run:String):Map<Medium,Insight>{
        val out=linkedMapOf<Medium,Insight>()
        readableDatabase.rawQuery("SELECT uri, metadata, analysis FROM results WHERE run=?",arrayOf(run)).use{c->while(c.moveToNext()){
            val j=JSONArray(c.getString(1));val m=Medium(c.getString(0),j.getString(0),j.getLong(1),j.getLong(2),j.getInt(3),j.getInt(4),j.getLong(5),j.getString(6),j.getBoolean(7),j.getLong(8),j.getInt(9))
            out[m]=Insight.decode(c.getString(2))
        }}
        readableDatabase.rawQuery("SELECT uri,freshness,choice FROM choices",null).use{c->
            val byUri=out.keys.associateBy{it.uri};while(c.moveToNext()){
                if(c.getString(1)=="keeper"){
                    val group=c.getString(0);val chosen=byUri[c.getString(2)]?:continue
                    if(out[chosen]?.findings?.any{it.group==group}!=true)continue
                    for((file,insight) in out.entries){
                        if(insight.findings.none{it.group==group})continue
                        out[file]=insight.copy(findings=insight.findings.map{if(it.group!=group)it else it.copy(protected=file==chosen,reason=if(file==chosen)"Keeper · chosen by you" else "Matches "+chosen.name)})
                    }
                    continue
                }
                val m=byUri[c.getString(0)]?:continue;if(m.freshness!=c.getString(1))continue
                val i=out.getValue(m);out[m]=i.copy(findings=listOf(Finding(if(c.getString(2)=="Review later")"Review later" else "Protected",c.getString(2),true)))
            }
        }
        return out
    }
}
