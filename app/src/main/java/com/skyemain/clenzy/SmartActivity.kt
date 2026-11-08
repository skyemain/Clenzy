package com.skyemain.clenzy

import android.Manifest.permission.*
import android.app.Activity
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

class SmartActivity:ComponentActivity(){
    private val previewSlots=Semaphore(2)
    private val previewCache=object:android.util.LruCache<String,android.graphics.Bitmap>(16*1024*1024){
        override fun sizeOf(key:String,value:android.graphics.Bitmap)=value.allocationByteCount
    }
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
    private var scanning=false
    private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ }
    private fun backgroundPrompt(){
        if(Build.VERSION.SDK_INT>=33 && !granted(android.Manifest.permission.POST_NOTIFICATIONS) && !getPreferences(0).getBoolean("askedNotifications",false)){
            getPreferences(0).edit().putBoolean("askedNotifications",true).apply()
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    private fun homeDuringScan(){
        val active=run
        run="";scanning=false
        getPreferences(0).edit().remove("run").apply()
        lifecycleScope.launch{
            if(active.isNotEmpty())withContext(Dispatchers.IO){manager.cancelWorkById(UUID.fromString(active)).result.get()}
            if(isTaskRoot)startActivity(Intent(this@SmartActivity,HomeActivity::class.java))
            finish()
        }
    }
    private val request=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(access())startScan() else landing()}
    private val trash=registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){response->
        if(response.resultCode!=Activity.RESULT_OK){submitting=false;waiting=emptyList();review();return@registerForActivityResult}
        lifecycleScope.launch{
            val moved=withContext(Dispatchers.IO){waiting.filter{TrashState.isTrashed(contentResolver,it.uri)}}
            val movedUris=moved.map{it.uri}.toSet()
            results=results.filterKeys{it.uri !in movedUris}
            withContext(Dispatchers.IO){SmartStore(this@SmartActivity).use{it.removeResults(run,movedUris)}}
            CleanupHistory.record(this@SmartActivity,moved.sumOf{it.size},moved.size,waiting.size-moved.size)
            waiting=emptyList();submitting=false;selected.clear()
            Toast.makeText(this@SmartActivity,"${moved.size} moved to Trash",Toast.LENGTH_SHORT).show()
            review()

        }
    }
    override fun onCreate(state:Bundle?){super.onCreate(state);run=getPreferences(0).getString("run","").orEmpty()
        if(run.isEmpty())landing() else observe(UUID.fromString(run),state?.getStringArrayList("pending").orEmpty())
    }
    override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);out.putStringArrayList("pending",ArrayList(waiting.map{it.uri}))}
    private fun dp(n:Int)=(resources.displayMetrics.density*n).toInt()
    private fun bytes(n:Long)=Formatter.formatShortFileSize(this,n)
    private fun screen(title:String,edgeToEdge:Boolean=false){
        page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(24),dp(20),dp(20))}
        page.background=Aurora.background();Aurora.window(this)
        setContentView(page)
        ViewCompat.setOnApplyWindowInsetsListener(page){view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding((if(edgeToEdge)0 else dp(10))+bars.left,dp(16)+bars.top,(if(edgeToEdge)0 else dp(10))+bars.right,dp(12)+bars.bottom)
            insets
        }
        label(title,29).apply{setTypeface(null,Typeface.BOLD);if(edgeToEdge)setPadding(dp(12),dp(5),dp(12),dp(7))}
    }
    private fun label(value:String,size:Int=15)=TextView(this).apply{text=value;setTextColor(Aurora.INK);textSize=size.toFloat();setPadding(0,dp(5),0,dp(7));page.addView(this)}
    private fun button(name:String,action:()->Unit)=MaterialButton(this).apply{text=name;Aurora.button(this);setOnClickListener{action()};page.addView(this)}
    private fun granted(p:String)=checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED
    private fun access()=if(Build.VERSION.SDK_INT>=33)granted(READ_MEDIA_IMAGES)||granted(READ_MEDIA_VIDEO)||(Build.VERSION.SDK_INT>=34&&granted(READ_MEDIA_VISUAL_USER_SELECTED)) else granted(READ_EXTERNAL_STORAGE)
    private fun permissions()=when{Build.VERSION.SDK_INT>=34->arrayOf(READ_MEDIA_IMAGES,READ_MEDIA_VIDEO,READ_MEDIA_VISUAL_USER_SELECTED);Build.VERSION.SDK_INT>=33->arrayOf(READ_MEDIA_IMAGES,READ_MEDIA_VIDEO);else->arrayOf(READ_EXTERNAL_STORAGE)}
    private fun landing(){
        category=null;screen("AI Smart Clean");label("Find repeated photos, shared videos and promotional images. Analysis stays on your phone.")
        label(when{!access()->"Media access needed";Build.VERSION.SDK_INT>=33 && !(granted(READ_MEDIA_IMAGES)&&granted(READ_MEDIA_VIDEO))->"Limited media access";else->"Photo and video access enabled"},13)
        button("Scan photos & videos"){if(access())startScan() else request.launch(permissions())}
        if(Build.VERSION.SDK_INT>=34 && access())button("Manage media access"){request.launch(permissions())}
    }
    private fun rescan(){if(access())startScan() else request.launch(permissions())}
    private fun startScan(){ScanNotifications.ensureChannel(this).cancel(ScanNotifications.notificationId);backgroundPrompt();results=emptyMap();screen("Scanning media");page.addView(ProgressBar(this));selected.clear();waiting=emptyList();category=null;val task=OneTimeWorkRequestBuilder<SmartWorker>().setBackoffCriteria(BackoffPolicy.LINEAR,10,java.util.concurrent.TimeUnit.SECONDS).build();run=task.id.toString();getPreferences(0).edit().putString("run",run).apply();manager.enqueueUniqueWork("smart-media",ExistingWorkPolicy.REPLACE,task);observe(task.id)}
    private fun observe(id:UUID,pending:List<String> = emptyList()){
        manager.getWorkInfoByIdLiveData(id).observe(this){work->
            if(run!=id.toString())return@observe
            scanning=work!=null && !work.state.isFinished
            when(work?.state){
                WorkInfo.State.SUCCEEDED->lifecycleScope.launch{
                    if(!access()){landing();return@launch}
                    val fresh=withContext(Dispatchers.IO){SmartStore(this@SmartActivity).use{it.results(id.toString())}}
                    if(run!=id.toString())return@launch
                    results=fresh
                    waiting=results.keys.filter{it.uri in pending};dashboard()
                }
                WorkInfo.State.FAILED,WorkInfo.State.CANCELLED->{landing();label(work.outputData.getString("error")?:"Scan stopped. Completed analysis can be reused.")}
                null->if(getPreferences(0).getString("run","")==id.toString()){screen("Preparing media scan");button("Restart scan",this::rescan)}else landing()
                else->{screen("Analyzing media")
                    val n=work.progress.getInt("total",0)
                    val done=work.progress.getInt("images",0)+work.progress.getInt("videos",0)
                    val grouping=work.progress.getString("stage")=="Grouping matches"
                    label(if(n>0)"${if(grouping)99 else (done*100/n).coerceAtMost(99)}%" else "Preparing",44)
                    val bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
                        max=100;isIndeterminate=n==0;progress=if(n>0)(done*100/n).coerceAtMost(99) else 0
                        progressTintList=android.content.res.ColorStateList.valueOf(Aurora.ACCENT)
                    }
                    page.addView(bar,LinearLayout.LayoutParams(-1,dp(8)))
                    label(work.progress.getString("stage")?:"Preparing scan")
                    if(n>0)label("$done of $n photos & videos",16)
                    val eta=work.progress.getLong("eta",-1)
                    if(eta>=0 && !grouping)label(if(eta<60)"Estimated: less than a minute left" else "Estimated: ${(eta+59)/60} min left",14)
                    else if(!grouping)label("Estimating time…",14)
                    label("Switch apps or turn off the screen. We’ll notify you when it’s ready.",14)
                    if(!ScanNotifications.enabled(this))button("Enable completion alerts"){
                        startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName))
                    }
                    page.addView(Space(this),LinearLayout.LayoutParams(1,0,1f))
                    label("Back to Home cancels this scan.",13).gravity=Gravity.CENTER
                    button("Back to Home",this::homeDuringScan)
                }
            }
        }
    }
    private fun dashboard(){
        category=null;screen("AI Smart Clean")
        val candidates=results.filter{(m,i)->i.findings.any{it.category!="Review manually"}}.keys
        val eligible=candidates.toSet()
        val grouped=linkedMapOf<String,MutableList<Medium>>()
        for((item,insight) in results){
            for(finding in insight.findings.distinctBy{it.category}){
                grouped.getOrPut(finding.category){mutableListOf()}.add(item)
            }
        }
        val hero=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            background=Aurora.glass(this@SmartActivity,true)
            setPadding(dp(18),dp(12),dp(18),dp(14))
        }
        fun heroLine(value:String,size:Int)=TextView(this).apply{text=value;setTextColor(Aurora.INK);textSize=size.toFloat();setTextColor(android.graphics.Color.WHITE);hero.addView(this)}
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
                    background=Aurora.glass(this@SmartActivity,false)
                    clipToOutline=true;isFocusable=true
                    setOnClickListener{category=cat;filter="All";review()}
                }
                val image=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(0xff405d52.toInt());setImageResource(android.R.drawable.ic_menu_gallery)}
                card.addView(image,LinearLayout.LayoutParams(-1,dp(132)));thumbLoader(image,members.first())
                val name=TextView(this).apply{text=cat;textSize=16f;setTypeface(null,Typeface.BOLD);setPadding(dp(14),dp(10),dp(14),0)}
                card.addView(name)
                card.addView(TextView(this).apply{text="${members.size} files · ${bytes(members.sumOf{it.size})}";setPadding(dp(14),dp(4),dp(14),dp(14))})
                row.addView(card,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(3),dp(10),dp(3),0)})
            }
            if(pair.size==1)row.addView(Space(this),LinearLayout.LayoutParams(0,1,1f))
        }
        if(Build.VERSION.SDK_INT>=33 && !(granted(READ_MEDIA_IMAGES)&&granted(READ_MEDIA_VIDEO))){
            button("Limited media access · Manage"){startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+packageName)))}
        }
        button("Scan again",this::rescan)
    }
    private fun thumbLoader(image:ImageView,m:Medium,large:Boolean=false){
        val key=m.uri+"/"+m.stamp+"/"+large
        image.tag=key
        previewCache.get(key)?.let{image.setImageBitmap(it);return}
        val loading=lifecycleScope.launch{
            val bitmap=withContext(Dispatchers.IO){
                previewSlots.withPermit{
                    runCatching{
                        val uri=Uri.parse(m.uri)
                        val edge=if(large)1080 else 240
                        if(large && !m.video && Build.VERSION.SDK_INT>=28){
                            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver,uri)){decoder,info,_->
                                val scale=edge.toFloat()/maxOf(info.size.width,info.size.height).coerceAtLeast(edge)
                                decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1))
                            }
                        }else if(Build.VERSION.SDK_INT>=29){
                            contentResolver.loadThumbnail(uri,android.util.Size(edge,edge),null)
                        }else null
                    }.getOrNull()
                }
            }
            if(bitmap!=null){previewCache.put(key,bitmap);if(image.tag==key)image.setImageBitmap(bitmap)}
        }
        image.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
            override fun onViewAttachedToWindow(v:View)=Unit
            override fun onViewDetachedFromWindow(v:View){loading.cancel();v.removeOnAttachStateChangeListener(this)}
        })
    }
    private fun thumbnail(row:LinearLayout,m:Medium){val image=ImageView(this);image.scaleType=ImageView.ScaleType.CENTER_CROP;row.addView(image,LinearLayout.LayoutParams(dp(58),dp(58)));thumbLoader(image,m)}
    private fun review(){
        val cat=category?:return dashboard();screen(cat,true)
        val rows=results.keys.filter{m->results.getValue(m).findings.any{it.category==cat} && (filter=="All"||m.source==filter||filter==(if(m.video)"Video" else "Photo"))}
        val sorted=when(sort){1->rows.sortedBy{it.stamp};2->rows.sortedByDescending{it.stamp};else->rows.sortedByDescending{it.size}}
        val controls=LinearLayout(this);controls.setPadding(dp(8),0,dp(8),0);page.addView(controls)
        fun chip(name:String,action:()->Unit):MaterialButton{
            val control=MaterialButton(this).apply{text=name;Aurora.button(this);setOnClickListener{action()}}
            controls.addView(control,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(3),0,dp(3),dp(4))})
            return control
        }
        chip("Sort"){choices("Sort",arrayOf("Largest","Oldest","Newest")){n->sort=n;review()}}
        chip("Filter"){val opts=(listOf("All","Photo","Video")+results.keys.map{it.source}.distinct()).toTypedArray();choices("Filter",opts){n->filter=opts[n];review()}}
        val grid=GridView(this);grid.numColumns=3;grid.horizontalSpacing=0;grid.verticalSpacing=0;page.addView(grid,LinearLayout.LayoutParams(-1,0,1f))
        fun eligible()=rows
        lateinit var allButton:MaterialButton
        lateinit var action:MaterialButton
        fun updateActions(){
            val eligible=eligible()
            allButton.isEnabled=eligible.isNotEmpty()
            allButton.text=if(eligible.isNotEmpty() && selected.containsAll(eligible)) "Clear all" else "Select all"
            action.visibility=if(selected.isEmpty()) View.GONE else View.VISIBLE
            action.text="Move to Trash (${selected.size})"
        }
        val adapter=object:BaseAdapter(){override fun getCount()=sorted.size;override fun getItem(p:Int)=sorted[p];override fun getItemId(p:Int)=p.toLong()
            override fun getView(p:Int,v:View?,parent:ViewGroup):View{val m=sorted[p]
                val cell=FrameLayout(this@SmartActivity)
                val edge=((resources.displayMetrics.widthPixels-page.paddingLeft-page.paddingRight)/3).coerceAtLeast(1)
                cell.layoutParams=AbsListView.LayoutParams(-1,edge)
                val photo=ImageView(this@SmartActivity).apply{
                    scaleType=ImageView.ScaleType.CENTER_CROP
                    contentDescription="Photo or video preview"
                }
                cell.addView(photo,FrameLayout.LayoutParams(-1,-1))
                thumbLoader(photo,m,true)
                val tint=View(this@SmartActivity).apply{setBackgroundColor(0x6677aaff);isClickable=false;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
                cell.addView(tint,FrameLayout.LayoutParams(-1,-1))
                fun updateSelection(){
                    tint.visibility=if(m in selected)View.VISIBLE else View.GONE
                    photo.isSelected=m in selected
                    photo.contentDescription=if(m in selected)"Selected photo. Tap to deselect" else "Photo. Tap to select. Hold for options"
                }
                updateSelection()
                photo.setOnClickListener{
                    if(!submitting){
                        if(!selected.add(m))selected.remove(m)
                        updateSelection();updateActions()
                    }
                }
                photo.setOnLongClickListener{
                    choices("Photo options",arrayOf("Preview","Review later")){n->
                        if(n==0)runCatching{val uri=Uri.parse(m.uri);startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,contentResolver.getType(uri)?:if(m.video)"video/*" else "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{AuroraDialog.Builder(this@SmartActivity).setTitle("Preview unavailable").setMessage("No app can open this file, or it is no longer available.").setPositiveButton("Done",null).show()}
                        else lifecycleScope.launch{
                            results=withContext(Dispatchers.IO){SmartStore(this@SmartActivity).use{it.choice(m,"Review later");it.results(run)}}
                            selected.remove(m);review()
                        }
                    };true};return cell}}
        if(sorted.isEmpty())label("No items remaining",16)
        grid.adapter=adapter
        allButton=chip("Select all"){val eligible=eligible();if(selected.containsAll(eligible))selected.removeAll(eligible.toSet()) else selected.addAll(eligible);adapter.notifyDataSetChanged();updateActions()}
        action=button("Move to Trash"){if(selected.isNotEmpty()&&!submitting)confirm()}
        updateActions()
    }
    private fun choices(title:String,options:Array<String>,choose:(Int)->Unit){
        AuroraDialog.Builder(this).setTitle(title).setItems(options){_,index->choose(index)}.setNegativeButton("Close",null).show()
    }
    private fun confirm(){
        if(Build.VERSION.SDK_INT<30){AuroraDialog.Builder(this).setMessage("System Trash requires Android 11 or later. Use Preview to manage media in your gallery.").setPositiveButton("OK",null).show();return}
        if(selected.size>2000){Toast.makeText(this,"Select up to 2,000 files per request.",Toast.LENGTH_LONG).show();return}
        submitting=true
        lifecycleScope.launch{
                val chosen=selected.toList();waiting=withContext(Dispatchers.IO){chosen.filter{m->runCatching{contentResolver.query(Uri.parse(m.uri),arrayOf("_size","date_modified"),null,null,null)?.use{it.moveToFirst()&&it.getLong(0)==m.size&&it.getLong(1)==m.stamp}==true}.getOrDefault(false)}}
                if(waiting.size!=chosen.size){submitting=false;Toast.makeText(this@SmartActivity,"Media changed. Scan again before cleanup.",Toast.LENGTH_LONG).show();selected.clear();landing();return@launch}
                try{val pi=MediaStore.createTrashRequest(contentResolver,waiting.map{Uri.parse(it.uri)},true);trash.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())}
                catch(_:Exception){submitting=false;Toast.makeText(this@SmartActivity,"Android could not open Trash confirmation.",Toast.LENGTH_LONG).show()}
        }
    }
    @Deprecated("Android back navigation") override fun onBackPressed(){if(scanning)homeDuringScan() else if(category!=null)dashboard() else super.onBackPressed()}
}
