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
        applyTheme(theme); super.onCreate(state); getSharedPreferences("locked_media",0).edit().clear().apply(); draw();
        if(getIntent().getBooleanExtra("settings",false))settings();
    }
    @Override protected void onResume(){super.onResume(); if(page!=null){if(getIntent().getBooleanExtra("settings",false))settings();else draw();}if(waitingUsage){waitingUsage=false;loadApps();}}
    private static void applyTheme(int mode){AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);}
    private void draw(){
        ink=Aurora.INK;muted=Aurora.MUTED;surface=Aurora.SURFACE;bg=Aurora.BASE;Aurora.window(this);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackground(Aurora.background());
        page=column();page.setPadding(dp(10),dp(32),dp(10),dp(24));scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{page.setPadding(dp(10),insets.getSystemWindowInsetTop()+dp(28),dp(10),insets.getSystemWindowInsetBottom()+dp(24));return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView brand=text("Clenzy",32,ink);brand.setTypeface(null,Typeface.BOLD);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));header.addView(Aurora.iconButton(this,"settings","Settings",()->startActivity(new Intent(this,HomeActivity.class).putExtra("settings",true))),new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(header);
        page.addView(new Space(this),new LinearLayout.LayoutParams(1,0,1));
        addScanCard("Scan","Files & storage",false,this::startCleaning);
        addScanCard("Scan with AI","Photos & videos",true,()->startActivity(new Intent(this,SmartActivity.class)));
        page.addView(new Space(this),new LinearLayout.LayoutParams(1,0,1));
        setContentView(scroll);scroll.requestApplyInsets();
    }
    private void addScanCard(String title,String subtitle,boolean ai,Runnable action){
        LinearLayout tile=new LinearLayout(this);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(dp(24),dp(28),dp(18),dp(28));tile.setBackground(Aurora.glass(this,ai));tile.setMinimumHeight(dp(200));tile.setFocusable(true);tile.setContentDescription(title);tile.setOnClickListener(v->action.run());
        LinearLayout words=column();TextView name=text(title,28,ink);name.setTypeface(null,Typeface.BOLD);name.setOnClickListener(v->action.run());words.addView(name);words.addView(text(subtitle,15,muted));tile.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        ImageView icon=new ImageView(this);icon.setImageDrawable(Aurora.symbol(ai));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(icon,new LinearLayout.LayoutParams(dp(82),dp(82)));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.setMargins(0,dp(16),0,0);page.addView(tile,layout);
    }
    private LinearLayout navItem(String title,Runnable action){LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(0,dp(10),0,dp(10));ImageView icon=new ImageView(this);icon.setImageDrawable(new LineIcon(title,blue));item.addView(icon,new LinearLayout.LayoutParams(dp(23),dp(23)));TextView label=text(title,11,muted);label.setGravity(Gravity.CENTER);item.addView(label);item.setContentDescription(title);item.setFocusable(true);item.setOnClickListener(v->action.run());return item;}
    private void startCleaning(){startActivity(new Intent(this,CleanActivity.class));}
    private void open(String tool){startActivity(new Intent(this,MainActivity.class).putExtra("tool",tool));}
    private void settings(){
        Aurora.window(this);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackground(Aurora.background());
        page=column();scroll.addView(page);scroll.setOnApplyWindowInsetsListener((v,insets)->{page.setPadding(dp(10),insets.getSystemWindowInsetTop()+dp(12),dp(10),insets.getSystemWindowInsetBottom()+dp(24));return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Aurora.iconButton(this,"back","Back",this::finish),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView heading=text("Settings",24,ink);heading.setTypeface(null,Typeface.BOLD);heading.setGravity(Gravity.CENTER);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));header.addView(new Space(this),new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(header);
        storageSummary();
        addSetting("Cleanup history","Recent cleanup results","Downloads",this::history);
        addSetting("Privacy & access",accessSummary(),"settings",this::accessSettings);
        addSetting("Clenzy cache","Manage temporary files","Duplicate copies",()->open("cache"));
        addSetting("About Clenzy","Version 2.2.0","Home",()->message("Clenzy 2.2.0","Offline storage review and cleanup."));
        setContentView(scroll);scroll.requestApplyInsets();
    }
    private String accessSummary(){
        boolean all=Build.VERSION.SDK_INT>=30 && Environment.isExternalStorageManager();
        boolean photos=checkSelfPermission(Build.VERSION.SDK_INT>=33?android.Manifest.permission.READ_MEDIA_IMAGES:android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
        boolean videos=checkSelfPermission(Build.VERSION.SDK_INT>=33?android.Manifest.permission.READ_MEDIA_VIDEO:android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
        return "Shared files: "+(all?"allowed":"limited")+" · Media: "+(photos&&videos?"all":"limited");
    }
    private void accessSettings(){
        android.app.Dialog dialog=new android.app.Dialog(this);LinearLayout content=column();content.setPadding(dp(20),dp(20),dp(20),dp(16));content.setBackground(Aurora.glass(this,true));
        content.addView(text("Privacy & access",24,ink));content.addView(text(accessSummary(),15,muted));
        content.addView(text("All files access covers shared storage. Android keeps other apps’ private data separate.",13,muted));
        if(Build.VERSION.SDK_INT>=30)content.addView(button("Shared files access",()->{dialog.dismiss();try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}},false));
        content.addView(button("Photos & videos permission",()->{dialog.dismiss();startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));},false));
        content.addView(button("Done",dialog::dismiss,false));dialog.setContentView(content);dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));dialog.show();dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(20),-2);
    }
    private void storageSummary(){
        StatFs disk=new StatFs(Environment.getDataDirectory().getAbsolutePath());long total=disk.getTotalBytes(),free=disk.getAvailableBytes();
        LinearLayout card=column();card.setPadding(dp(20),dp(18),dp(20),dp(20));card.setBackground(Aurora.glass(this,false));
        card.addView(text("DEVICE STORAGE",12,muted));card.addView(text(size(free)+" free of "+size(total),23,ink));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(total>0?(int)((total-free)*100/total):0);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Aurora.ACCENT));card.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));page.addView(card,spaced());
    }
    private void addSetting(String title,String subtitle,String symbol,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(18),dp(18),dp(18));row.setBackground(Aurora.glass(this,false));
        ImageView icon=new ImageView(this);icon.setImageDrawable(new LineIcon(symbol,Aurora.ACCENT));row.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30)));
        LinearLayout words=column();words.setPadding(dp(16),0,0,0);TextView name=text(title,17,ink);name.setTypeface(null,Typeface.BOLD);words.addView(name);words.addView(text(subtitle,12,muted));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.setFocusable(true);row.setContentDescription(title);row.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(12),0,0);page.addView(row,lp);
    }
    private void history(){
        try {
            org.json.JSONArray entries=new org.json.JSONArray(getSharedPreferences("cleanup",0).getString("history","[]"));
            if(entries.length()==0){message("Cleanup history","No completed cleanup yet.");return;}
            String[] rows=new String[entries.length()];
            for(int n=0;n<entries.length();n++){var entry=entries.getJSONObject(n);rows[n]=android.text.format.DateFormat.format("MMM d, h:mm a",entry.getLong("time"))+" · "+size(entry.getLong("bytes"))+" · "+entry.getInt("files")+" files";}
            message("Cleanup history",String.join("\n\n",rows));
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
        page.addView(text("Apps",28,ink));page.addView(text("Largest apps first",13,muted));
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
    private void message(String title,String body){
        android.app.Dialog dialog=new android.app.Dialog(this);LinearLayout content=column();content.setPadding(dp(24),dp(24),dp(24),dp(20));content.setBackground(Aurora.glass(this,true));
        TextView name=text(title,24,ink);name.setTypeface(null,Typeface.BOLD);content.addView(name);content.addView(text(body,15,muted),spaced());content.addView(button("Done",dialog::dismiss,false));dialog.setContentView(content);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(40),-2);dialog.show();dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(40),-2);
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(7),0,dp(7));return v;}
    private LinearLayout card(){LinearLayout v=column();GradientDrawable shape=new GradientDrawable();shape.setColor(surface);shape.setCornerRadius(dp(24));v.setBackground(Aurora.glass(this,false));v.setPadding(dp(22),dp(18),dp(22),dp(18));return v;}
    private LinearLayout.LayoutParams spaced(){LinearLayout.LayoutParams v=new LinearLayout.LayoutParams(-1,-2);v.setMargins(0,dp(18),0,dp(18));return v;}
    private MaterialButton button(String text,Runnable action,boolean primary){MaterialButton b=new MaterialButton(this);b.setText(text);b.setAllCaps(false);b.setCornerRadius(dp(20));b.setMinHeight(dp(48));b.setTextColor(primary?Color.WHITE:((getResources().getConfiguration().uiMode&48)==32?Color.rgb(140,176,255):blue));b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?blue:surface));Aurora.button(b);b.setOnClickListener(v->action.run());return b;}
    private String size(long bytes){return Formatter.formatFileSize(this,bytes);}
}
