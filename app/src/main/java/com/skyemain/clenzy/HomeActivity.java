package com.skyemain.clenzy;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.button.MaterialButton;
import java.util.*;

public final class HomeActivity extends AppCompatActivity {
    private int ink,muted,surface,bg; private final int blue=Color.rgb(50,104,232);
    private LinearLayout page;
    private boolean waitingUsage;
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        int theme=getSharedPreferences("cleanup",0).getInt("theme",-1);
        applyTheme(theme); super.onCreate(state); draw();
    }
    @Override protected void onResume(){super.onResume(); if(page!=null) draw();if(waitingUsage){waitingUsage=false;loadApps();}}
    private static void applyTheme(int mode){
        if(mode==1)AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        else if(mode==2)AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    private void draw(){
        boolean dark=(getResources().getConfiguration().uiMode&48)==32;
        ink=Color.parseColor(dark?"#F3F6FF":"#172137");muted=Color.parseColor(dark?"#A7B3C9":"#647087");surface=Color.parseColor(dark?"#192437":"#FFFFFF");bg=Color.parseColor(dark?"#0E1726":"#F3F6FC");
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
        page=column();page.setPadding(dp(24),dp(22),dp(24),dp(24));scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{page.setPadding(dp(24),insets.getSystemWindowInsetTop()+dp(16),dp(24),insets.getSystemWindowInsetBottom()+dp(20));return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("✦  Clenzy",26,ink);brand.setTypeface(null,Typeface.BOLD);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        MaterialButton settings=button("Settings",this::settings,false);header.addView(settings);page.addView(header);
        TextView heading=text("A little space.\nA lighter day.",30,ink);heading.setGravity(Gravity.CENTER);heading.setTypeface(null,Typeface.BOLD);page.addView(heading,spaced());
        StatFs disk=new StatFs(Environment.getDataDirectory().getAbsolutePath());long total=disk.getTotalBytes(),free=disk.getAvailableBytes(),used=total-free;
        FrameLayout ring=new FrameLayout(this);ring.addView(new StorageRing(this,total>0?(float)used/total:0,dark?Color.rgb(44,57,75):Color.rgb(231,235,242),dark?Color.rgb(140,169,255):blue),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout numbers=column();numbers.setGravity(Gravity.CENTER);TextView amount=text(size(free),38,ink);amount.setTypeface(null,Typeface.BOLD);amount.setGravity(Gravity.CENTER);numbers.addView(amount);TextView available=text("available space",13,muted);available.setGravity(Gravity.CENTER);numbers.addView(available);ring.addView(numbers,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams ringParams=new LinearLayout.LayoutParams(dp(218),dp(218));ringParams.gravity=Gravity.CENTER;ringParams.setMargins(0,dp(8),0,dp(24));page.addView(ring,ringParams);
        TextView privacy=text("Your files stay on your phone",12,muted);privacy.setGravity(Gravity.CENTER);page.addView(privacy);
        TextView capacity=text(size(used)+" used  ·  "+size(total)+" total",13,muted);capacity.setGravity(Gravity.CENTER);page.addView(capacity,spaced());
        MaterialButton start=button("Scan my phone",this::startCleaning,true);start.setMinHeight(dp(58));page.addView(start,new LinearLayout.LayoutParams(-1,-2));
        TextView explanation=text("Find space to free up. You choose what goes.",12,muted);explanation.setGravity(Gravity.CENTER);page.addView(explanation);
        TextView memories=text("Your memories come first",15,ink);page.addView(memories,spaced());page.addView(text("Review personal files before removing them.",12,muted));
        LinearLayout navigation=new LinearLayout(this);navigation.addView(button("Home",this::draw,false),new LinearLayout.LayoutParams(0,-2,1));navigation.addView(button("Apps",this::apps,false),new LinearLayout.LayoutParams(0,-2,1));page.addView(navigation,spaced());
        for(int n=0;n<navigation.getChildCount();n++)navigation.getChildAt(n).setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));
        page.removeView(navigation);
        LinearLayout shell=column();shell.setBackgroundColor(bg);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));shell.addView(navigation);
        scroll.setOnApplyWindowInsetsListener(null);page.setPadding(dp(24),dp(16),dp(24),dp(10));
        shell.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        setContentView(shell);shell.requestApplyInsets();
    }
    private void startCleaning(){startActivity(new Intent(this,CleanActivity.class));}
    private void open(String tool){startActivity(new Intent(this,MainActivity.class).putExtra("tool",tool));}
    private void settings(){
        new AlertDialog.Builder(this).setTitle("Settings").setItems(new String[]{"Theme","Cleanup history","Privacy & access","System storage cleanup","About Clenzy","Photos & videos","Clenzy cache"},(d,w)->{
            if(w==0)new AlertDialog.Builder(this).setTitle("Theme").setSingleChoiceItems(new String[]{"System","Light","Dark"},getSharedPreferences("cleanup",0).getInt("theme",-1)==-1?0:getSharedPreferences("cleanup",0).getInt("theme",-1)==1?1:2,(dialog,index)->{int mode=index==0?-1:index==1?1:2;getSharedPreferences("cleanup",0).edit().putInt("theme",mode).apply();dialog.dismiss();applyTheme(mode);}).show();
            if(w==1)history();
            if(w==2)message("Private by design","Files stay on your device. Folder access is limited to locations you choose. Media access can be partial. No advertising, tracking, root or Accessibility service. Shared storage scanning uses optional all-files access. Trash may retain storage until emptied in the gallery.");
            if(w==3){try{startActivity(new Intent(android.os.storage.StorageManager.ACTION_MANAGE_STORAGE));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));}}
            if(w==5)open("photos");
            if(w==6)open("cache");
            if(w==4)message("Clenzy 1.4.0","Offline storage review and cleanup. Other apps’ private cache cannot be cleared by Clenzy. No speed or battery claims.");
        }).show();
    }
    private void history(){
        try {
            org.json.JSONArray entries=new org.json.JSONArray(getSharedPreferences("cleanup",0).getString("history","[]"));
            if(entries.length()==0){message("Cleanup history","No completed cleanup yet.");return;}
            String[] rows=new String[entries.length()];
            for(int n=0;n<entries.length();n++){var entry=entries.getJSONObject(n);rows[n]=android.text.format.DateFormat.format("MMM d, h:mm a",entry.getLong("time"))+" · "+size(entry.getLong("bytes"))+" · "+entry.getInt("files")+" files";}
            new AlertDialog.Builder(this).setTitle("Cleanup history · on this device").setItems(rows,(d,w)->{try{var item=entries.getJSONObject(w);message("Cleanup details",rows[w]+"\n"+item.getInt("skipped")+" skipped or unavailable. Source bytes deleted may differ from device free-space changes.");}catch(org.json.JSONException e){message("Unavailable","This history entry could not be read.");}}).setPositiveButton("Done",null).show();
        }catch(org.json.JSONException e){message("History unavailable","Saved cleanup history could not be read.");}
    }
    private boolean usageAllowed(){
        android.app.AppOpsManager ops=getSystemService(android.app.AppOpsManager.class);
        return ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),getPackageName())==android.app.AppOpsManager.MODE_ALLOWED;
    }
    private void apps(){
        if(usageAllowed()){loadApps();return;}
        new AlertDialog.Builder(this).setTitle("Find apps taking up space")
            .setMessage("Optional Usage access lets Clenzy show app sizes and recent use, so you can decide what to uninstall. This information stays on your phone.")
            .setPositiveButton("Enable usage access",(d,w)->{waitingUsage=true;startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));})
            .setNeutralButton("Continue without sizes",(d,w)->loadApps()).setNegativeButton("Cancel",null).show();
    }
    private record AppEntry(String name,String pkg,long bytes,long cache,long lastUsed) {}
    private void loadApps(){
        android.app.AlertDialog loading=new AlertDialog.Builder(this).setTitle("Analyzing apps…").setMessage("Reading storage reported by Android").setNegativeButton("Cancel",null).create();loading.show();
        worker.execute(()->{
            boolean allowed=usageAllowed();List<AppEntry> entries=new ArrayList<>();Set<String> seen=new HashSet<>();
            Map<String,android.app.usage.UsageStats> usage=Map.of();
            if(allowed)try{var stats=getSystemService(android.app.usage.UsageStatsManager.class).queryAndAggregateUsageStats(System.currentTimeMillis()-90L*86400000,System.currentTimeMillis());if(stats!=null)usage=stats;}catch(SecurityException ignored){}
            Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for(ResolveInfo info:getPackageManager().queryIntentActivities(launcher,0)){
                String pkg=info.activityInfo.packageName;if(!seen.add(pkg))continue;
                long bytes=-1,cache=-1,last=0;
                if(allowed)try{var app=getPackageManager().getApplicationInfo(pkg,0);var stats=getSystemService(android.app.usage.StorageStatsManager.class).queryStatsForPackage(app.storageUuid==null?android.os.storage.StorageManager.UUID_DEFAULT:app.storageUuid,pkg,android.os.Process.myUserHandle());bytes=stats.getAppBytes()+stats.getDataBytes();cache=stats.getCacheBytes();}catch(Exception ignored){}
                if(usage.containsKey(pkg))last=usage.get(pkg).getLastTimeUsed();
                entries.add(new AppEntry(info.loadLabel(getPackageManager()).toString(),pkg,bytes,cache,last));
            }
            entries.sort(Comparator.comparingLong(AppEntry::bytes).reversed().thenComparing(AppEntry::name));
            runOnUiThread(()->{if(isFinishing()||isDestroyed()||!loading.isShowing())return;loading.dismiss();
                showApps(entries);
            });
        });
    }
    private void showApps(List<AppEntry> entries){
        page.removeAllViews();page.addView(button("‹  Home",this::draw,false));
        page.addView(text("Keep your favourites.\nFind your space.",28,ink));page.addView(text("Largest apps first",13,muted));
        if(entries.isEmpty())page.addView(text("No visible apps were reported by Android.",14,muted));
        for(AppEntry app:entries){
            LinearLayout row=card();row.addView(text(app.name()+"  ·  "+(app.bytes()<0?"Size unavailable":size(app.bytes())),16,ink));
            row.addView(text(app.lastUsed()>0?"Last used "+android.text.format.DateFormat.format("MMM d",app.lastUsed()):"No usage record available",12,muted));
            MaterialButton manage=button("Manage app",()->new AlertDialog.Builder(this).setTitle(app.name()).setMessage((app.cache()<0?"":size(app.cache())+" cache included in app data.\n")+"Android Settings handles cache removal. Uninstalling may remove local app data.")
                .setPositiveButton("App settings",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.pkg()))))
                .setNeutralButton("Uninstall",(d,w)->{try{startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+app.pkg())));}catch(ActivityNotFoundException e){message("Unavailable","Use Android app settings to uninstall.");}}).setNegativeButton("Cancel",null).show(),false);
            row.addView(manage);page.addView(row,spaced());
        }
    }
    private void message(String title,String body){new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("Done",null).show();}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(7),0,dp(7));return v;}
    private LinearLayout card(){LinearLayout v=column();GradientDrawable shape=new GradientDrawable();shape.setColor(surface);shape.setCornerRadius(dp(24));v.setBackground(shape);v.setPadding(dp(22),dp(18),dp(22),dp(18));return v;}
    private LinearLayout.LayoutParams spaced(){LinearLayout.LayoutParams v=new LinearLayout.LayoutParams(-1,-2);v.setMargins(0,dp(18),0,dp(18));return v;}
    private MaterialButton button(String text,Runnable action,boolean primary){MaterialButton b=new MaterialButton(this);b.setText(text);b.setAllCaps(false);b.setCornerRadius(dp(20));b.setMinHeight(dp(48));b.setTextColor(primary?Color.WHITE:((getResources().getConfiguration().uiMode&48)==32?Color.rgb(140,176,255):blue));b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?blue:surface));b.setOnClickListener(v->action.run());return b;}
    private String size(long bytes){return Formatter.formatFileSize(this,bytes);}
}
