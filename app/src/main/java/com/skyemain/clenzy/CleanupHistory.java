package com.skyemain.clenzy;

import android.content.Context;
import org.json.*;

public final class CleanupHistory {
    private CleanupHistory(){}
    public static synchronized void record(Context context,long bytes,int files,int skipped){
        if(files<=0)return;
        var prefs=context.getSharedPreferences("cleanup",0);
        try{
            JSONArray old;
            try{old=new JSONArray(prefs.getString("history","[]"));}catch(JSONException e){old=new JSONArray();}
            long now=System.currentTimeMillis();JSONArray next=new JSONArray();
            next.put(new JSONObject().put("time",now).put("bytes",bytes).put("files",files).put("skipped",skipped));
            for(int n=0;n<Math.min(19,old.length());n++)next.put(old.get(n));
            prefs.edit().putString("history",next.toString()).putLong("lastClean",now).putLong("lastBytes",bytes).putInt("lastFiles",files).apply();
        }catch(JSONException e){android.util.Log.w("Clenzy","Could not record cleanup history",e);}
    }
}
