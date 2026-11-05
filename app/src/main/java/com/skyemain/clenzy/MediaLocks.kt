package com.skyemain.clenzy

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.io.File

/** App-private locks shared by the AI, MediaStore, filesystem and document cleanup flows. */
internal class MediaLocks(private val context:Context){
    private val prefs=context.getSharedPreferences("locked_media",Context.MODE_PRIVATE)
    fun contains(uri:String)=prefs.contains(uri)
    fun set(m:Medium,locked:Boolean){
        if(!locked){prefs.edit().remove(m.uri).apply();return}
        val path=runCatching{context.contentResolver.query(Uri.parse(m.uri),arrayOf("_data"),null,null,null)?.use{
            if(it.moveToFirst())it.getString(0) else null
        }}.getOrNull().orEmpty()
        prefs.edit().putString(m.uri,JSONArray(listOf(path,m.name,m.size)).toString()).apply()
    }
    fun unlocked(files:List<ReviewFile>):List<ReviewFile>{
        val locks=prefs.all
        if(locks.isEmpty())return files
        val paths=HashSet<String>()
        val signatures=HashSet<Pair<String,Long>>()
        locks.values.forEach{val row=JSONArray(it as String);paths.add(row.getString(0));signatures.add(row.getString(1) to row.getLong(2))}
        return files.filter{file->
            val uri=Uri.parse(file.uri())
            !locks.containsKey(file.uri()) && uri.path !in paths &&
                (file.name() to file.bytes()) !in signatures
        }
    }
}
