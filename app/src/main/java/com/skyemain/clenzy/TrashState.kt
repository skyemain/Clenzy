package com.skyemain.clenzy

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

internal object TrashState {
    fun isTrashed(resolver:ContentResolver,uri:String):Boolean {
        if(Build.VERSION.SDK_INT<30)return false
        val args=Bundle().apply{putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_INCLUDE)}
        return runCatching{resolver.query(Uri.parse(uri),arrayOf(MediaStore.MediaColumns.IS_TRASHED),args,null)?.use{
            it.moveToFirst() && it.getInt(0)==1
        }==true}.getOrDefault(false)
    }
}
