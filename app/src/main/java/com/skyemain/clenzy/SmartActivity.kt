package com.skyemain.clenzy

import android.Manifest.permission.*
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.text.format.Formatter
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import java.util.UUID

class SmartActivity:ComponentActivity(){
    private lateinit var page:LinearLayout
    private val manager by lazy{WorkManager.getInstance(this)}
    private var results:Map<Medium,Insight> = emptyMap()
    private val selected=linkedSetOf<Medium>()
    private var category:String?=null
    private var run=""
    private var sort=0
    private var filter="All"
    private var waiting=emptyList<Medium>()
    private var submitting=false
    private val request=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(access())startScan() else landing()}
    private val trash=registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){response->
        if(response.resultCode!=Activity.RESULT_OK){submitting=false;waiting=emptyList();review();return@registerForActivityResult}
        lifecycleScope.launch{
            val moved=withContext(Dispatchers.IO){waiting.filter{m->runCatching{contentResolver.query(Uri.parse(m.uri),arrayOf("is_trashed"),null,null,null)?.use{it.moveToFirst()&&it.getInt(0)==1}==true}.getOrDefault(false)}}
            val categories=moved.flatMap{m->results[m]?.findings.orEmpty().map{it.category}}.distinct()
            screen("Cleanup summary");label("${moved.size} verified in Trash",24);label("${bytes(moved.sumOf{it.size})} moved to Trash. Space is released when Trash is emptied.")
            if(waiting.size!=moved.size)label("${waiting.size-moved.size} could not be verified.")
            if(categories.isNotEmpty())label(categories.joinToString(" · "))
            waiting=emptyList();submitting=false;selected.clear();getPreferences(0).edit().remove("run").apply();button("Done"){finish()}
        }
    }
    override fun onCreate(state:Bundle?){super.onCreate(state);run=getPreferences(0).getString("run","").orEmpty()
        if(run.isEmpty())landing() else observe(UUID.fromString(run),state?.getStringArrayList("pending").orEmpty())
    }
    override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);out.putStringArrayList("pending",ArrayList(waiting.map{it.uri}))}
    private fun dp(n:Int)=(resources.displayMetrics.density*n).toInt()
    private fun bytes(n:Long)=Formatter.formatShortFileSize(this,n)
    private fun screen(title:String){
        page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(24),dp(20),dp(20))}
        setContentView(page)
        ViewCompat.setOnApplyWindowInsetsListener(page){view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(20)+bars.left,dp(16)+bars.top,dp(20)+bars.right,dp(12)+bars.bottom)
            insets
        }
        label(title,29).setTypeface(null,Typeface.BOLD)
    }
    private fun label(value:String,size:Int=15)=TextView(this).apply{text=value;textSize=size.toFloat();setPadding(0,dp(5),0,dp(7));page.addView(this)}
    private fun button(name:String,action:()->Unit)=MaterialButton(this).apply{text=name;isAllCaps=false;cornerRadius=dp(18);setOnClickListener{action()};page.addView(this)}
    private fun granted(p:String)=checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED
    private fun access()=if(Build.VERSION.SDK_INT>=33)granted(READ_MEDIA_IMAGES)||granted(READ_MEDIA_VIDEO)||(Build.VERSION.SDK_INT>=34&&granted(READ_MEDIA_VISUAL_USER_SELECTED)) else granted(READ_EXTERNAL_STORAGE)
    private fun permissions()=when{Build.VERSION.SDK_INT>=34->arrayOf(READ_MEDIA_IMAGES,READ_MEDIA_VIDEO,READ_MEDIA_VISUAL_USER_SELECTED);Build.VERSION.SDK_INT>=33->arrayOf(READ_MEDIA_IMAGES,READ_MEDIA_VIDEO);else->arrayOf(READ_EXTERNAL_STORAGE)}
    private fun landing(){
        category=null;screen("AI Smart Clean");label("Find repeated photos, shared videos and promotional images. Analysis stays on your phone.")
        label(when{!access()->"Media access needed";Build.VERSION.SDK_INT>=33 && !(granted(READ_MEDIA_IMAGES)&&granted(READ_MEDIA_VIDEO))->"Limited media access";else->"Photo and video access enabled"},13)
        button("Scan photos & videos"){if(access())startScan() else request.launch(permissions())}
        if(Build.VERSION.SDK_INT>=34 && access())button("Manage media access"){request.launch(permissions())}
    }
    private fun startScan(){val task=OneTimeWorkRequestBuilder<SmartWorker>().build();run=task.id.toString();getPreferences(0).edit().putString("run",run).apply();manager.enqueueUniqueWork("smart-media",ExistingWorkPolicy.REPLACE,task);observe(task.id)}
    private fun observe(id:UUID,pending:List<String> = emptyList()){
        manager.getWorkInfoByIdLiveData(id).observe(this){work->
            if(run!=id.toString())return@observe
            when(work?.state){
                WorkInfo.State.SUCCEEDED->lifecycleScope.launch{
                    if(!access()){landing();return@launch}
                    results=withContext(Dispatchers.IO){SmartStore(this@SmartActivity).use{it.results(run)}}
                    waiting=results.keys.filter{it.uri in pending};dashboard()
                }
                WorkInfo.State.FAILED,WorkInfo.State.CANCELLED->{landing();label(work.outputData.getString("error")?:"Scan stopped. Completed analysis can be reused.")}
                null->landing()
                else->{screen("Analyzing media");page.addView(ProgressBar(this));label(work.progress.getString("stage")?:"Preparing scan");val n=work.progress.getInt("total",0)
                    if(n>0)label("$n discovered · ${work.progress.getInt("images",0)} photos · ${work.progress.getInt("videos",0)} videos")
                    button("Cancel scan"){manager.cancelWorkById(id)}
                }
            }
        }
    }
    private fun protected(m:Medium)=results.getValue(m).findings.any{it.protected}
    private fun dashboard(){
        category=null;screen("AI Smart Clean")
        val candidates=results.filter{(m,i)->!protected(m)&&i.findings.any{it.category!="Review manually"}}.keys
        val eligible=candidates.toSet()
        val grouped=linkedMapOf<String,MutableList<Medium>>()
        for((item,insight) in results){
            for(finding in insight.findings.distinctBy{it.category}){
                if(finding.category!="Protected")grouped.getOrPut(finding.category){mutableListOf()}.add(item)
            }
        }
        val hero=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            background=GradientDrawable().apply{setColor(0xff163e34.toInt());cornerRadius=dp(18).toFloat()}
            setPadding(dp(18),dp(12),dp(18),dp(14))
        }
        fun heroLine(value:String,size:Int)=TextView(this).apply{text=value;textSize=size.toFloat();setTextColor(android.graphics.Color.WHITE);hero.addView(this)}
        heroLine("${bytes(candidates.sumOf{it.size})} to review",29).setTypeface(null,Typeface.BOLD)
        heroLine("${eligible.size} candidates · ${results.size} media checked",14)
        page.addView(hero)
        val scroll=ScrollView(this);val cards=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        scroll.addView(cards);page.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val dark=(resources.configuration.uiMode and 48)==32
        val priority=listOf("Duplicate photos","Similar photos","Screenshots","Shared videos")
        val names=grouped.keys.sortedWith(compareBy<String>{priority.indexOf(it).let{n->if(n<0)99 else n}}.thenBy{it})
        for(pair in names.chunked(2)){
            val row=LinearLayout(this);cards.addView(row)
            for(cat in pair){
                val members=grouped.getValue(cat)
                val card=LinearLayout(this).apply{
                    orientation=LinearLayout.VERTICAL
                    background=GradientDrawable().apply{cornerRadius=dp(15).toFloat();setColor(if(dark)0xff202d29.toInt() else -1);setStroke(dp(1),if(dark)0xff40524a.toInt() else 0xffd7e2dc.toInt())}
                    clipToOutline=true;isFocusable=true
                    setOnClickListener{category=cat;filter="All";review()}
                }
                val image=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(0xff405d52.toInt());setImageResource(android.R.drawable.ic_menu_gallery)}
                card.addView(image,LinearLayout.LayoutParams(-1,dp(80)));thumbLoader(image,members.first())
                val name=TextView(this).apply{text=cat;textSize=16f;setTypeface(null,Typeface.BOLD);setPadding(dp(10),dp(6),dp(10),0)}
                card.addView(name)
                card.addView(TextView(this).apply{text="${members.size} files · ${bytes(members.sumOf{it.size})}";setPadding(dp(10),0,dp(10),dp(8))})
                row.addView(card,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(3),dp(7),dp(3),0)})
            }
            if(pair.size==1)row.addView(Space(this),LinearLayout.LayoutParams(0,1,1f))
        }
        val kept=results.count{(_,i)->i.findings.any{it.category=="Protected"}}
        if(kept>0)cards.addView(MaterialButton(this).apply{text="Protected   ·   $kept files";isAllCaps=false;setOnClickListener{category="Protected";filter="All";review()}})
        button("Scan again"){landing()}
    }
    private fun thumbLoader(image:ImageView,m:Medium){
        if(Build.VERSION.SDK_INT>=29)lifecycleScope.launch{val b=withContext(Dispatchers.IO){runCatching{contentResolver.loadThumbnail(Uri.parse(m.uri),android.util.Size(160,160),null)}.getOrNull()};if(b!=null)image.setImageBitmap(b)}
    }
    private fun thumbnail(row:LinearLayout,m:Medium){val image=ImageView(this);image.scaleType=ImageView.ScaleType.CENTER_CROP;row.addView(image,LinearLayout.LayoutParams(dp(58),dp(58)));thumbLoader(image,m)}
    private fun review(){
        val cat=category?:return dashboard();screen(cat)
        val rows=results.keys.filter{m->results.getValue(m).findings.any{it.category==cat} && (filter=="All"||m.source==filter||filter==(if(m.video)"Video" else "Photo"))}
        val sorted=when(sort){1->rows.sortedByDescending{it.size};2->rows.sortedBy{it.stamp};3->rows.sortedByDescending{it.stamp};else->rows.sortedWith(compareBy<Medium>{protected(it)}.thenByDescending{it.size})}
        val controls=LinearLayout(this);page.addView(controls)
        fun chip(name:String,action:()->Unit){controls.addView(MaterialButton(this).apply{text=name;isAllCaps=false;setOnClickListener{action()}},LinearLayout.LayoutParams(0,-2,1f))}
        chip("Sort"){AlertDialog.Builder(this).setItems(arrayOf("Recommended","Largest","Oldest","Newest")){_,n->sort=n;review()}.show()}
        chip("Filter"){val opts=(listOf("All","Photo","Video")+results.keys.map{it.source}.distinct()).toTypedArray();AlertDialog.Builder(this).setItems(opts){_,n->filter=opts[n];review()}.show()}
        val grid=GridView(this);grid.numColumns=2;grid.horizontalSpacing=dp(8);grid.verticalSpacing=dp(8);page.addView(grid,LinearLayout.LayoutParams(-1,0,1f))
        lateinit var action:MaterialButton
        val adapter=object:BaseAdapter(){override fun getCount()=sorted.size;override fun getItem(p:Int)=sorted[p];override fun getItemId(p:Int)=p.toLong()
            override fun getView(p:Int,v:View?,parent:ViewGroup):View{val m=sorted[p];val cell=LinearLayout(this@SmartActivity);cell.orientation=LinearLayout.VERTICAL;cell.setPadding(dp(8),dp(8),dp(8),dp(8));val photo=ImageView(this@SmartActivity).apply{scaleType=ImageView.ScaleType.CENTER_CROP};cell.addView(photo,LinearLayout.LayoutParams(-1,dp(110)));thumbLoader(photo,m)
                val t=TextView(this@SmartActivity);t.text="${m.name}\n${bytes(m.size)} · ${m.source}\n${results.getValue(m).findings.first{it.category==cat}.reason}";t.maxLines=5;cell.addView(t)
                val check=CheckBox(this@SmartActivity);check.text=if(protected(m))"Kept" else "Select";check.isEnabled=!protected(m);check.isChecked=m in selected;cell.addView(check)
                check.setOnCheckedChangeListener{_,yes->if(yes)selected.add(m) else selected.remove(m);action.text="Review selection (${selected.size})"}
                cell.setOnClickListener{val group=results.getValue(m).findings.firstOrNull{it.category==cat && it.group.isNotEmpty()}?.group
                    val options=(listOf("Preview","Always keep","Not clutter","Hide from suggestions","Review later")+if(group!=null)listOf("Keep this copy") else emptyList()).toTypedArray()
                    AlertDialog.Builder(this@SmartActivity).setTitle(m.name).setItems(options){_,n->if(n==0)runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(m.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))} else lifecycleScope.launch{
                        results=withContext(Dispatchers.IO){SmartStore(this@SmartActivity).use{if(n==5 && group!=null)it.retain(group,m) else it.choice(m,options[n]);it.results(run)}};selected.removeAll{protected(it)};review()
                    }}.show()};return cell}}
        grid.adapter=adapter
        chip("Select all"){val eligible=rows.filterNot{protected(it)};if(selected.containsAll(eligible))selected.removeAll(eligible.toSet()) else selected.addAll(eligible);adapter.notifyDataSetChanged();action.text="Review selection (${selected.size})"}
        action=button("Review selection (${selected.size})"){if(selected.isNotEmpty()&&!submitting)confirm()};button("Back to results"){dashboard()}
    }
    private fun confirm(){
        if(Build.VERSION.SDK_INT<30){AlertDialog.Builder(this).setMessage("System Trash requires Android 11 or later. Use Preview to manage media in your gallery.").setPositiveButton("OK",null).show();return}
        if(selected.size>2000){Toast.makeText(this,"Select up to 2,000 files per request.",Toast.LENGTH_LONG).show();return}
        AlertDialog.Builder(this).setTitle("Move ${selected.size} files to Trash?").setMessage("${bytes(selected.sumOf{it.size})} selected. Android will ask you to confirm.")
            .setNegativeButton("Keep reviewing",null).setPositiveButton("Continue"){_,_->submitting=true;lifecycleScope.launch{
                val chosen=selected.toList();waiting=withContext(Dispatchers.IO){chosen.filter{m->!protected(m)&&runCatching{contentResolver.query(Uri.parse(m.uri),arrayOf("_size","date_modified"),null,null,null)?.use{it.moveToFirst()&&it.getLong(0)==m.size&&it.getLong(1)==m.stamp}==true}.getOrDefault(false)}}
                if(waiting.size!=chosen.size){submitting=false;Toast.makeText(this@SmartActivity,"Media changed. Scan again before cleanup.",Toast.LENGTH_LONG).show();selected.clear();landing();return@launch}
                try{val pi=MediaStore.createTrashRequest(contentResolver,waiting.map{Uri.parse(it.uri)},true);trash.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())}
                catch(_:Exception){submitting=false;Toast.makeText(this@SmartActivity,"Android could not open Trash confirmation.",Toast.LENGTH_LONG).show()}
            }}.show()
    }
    @Deprecated("Android back navigation") override fun onBackPressed(){if(category!=null)dashboard() else super.onBackPressed()}
}
