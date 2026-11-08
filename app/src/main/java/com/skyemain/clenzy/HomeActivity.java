package com.skyemain.clenzy;

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
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        int theme=getSharedPreferences("cleanup",0).getInt("theme",-1);
        applyTheme(theme); super.onCreate(state); getSharedPreferences("locked_media",0).edit().clear().apply(); draw();
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){finish();}});
        if(getIntent().getBooleanExtra("settings",false))settings();
    }
    @Override protected void onResume(){super.onResume(); if(page!=null){if(getIntent().getBooleanExtra("settings",false))settings();else draw();}}
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
        LinearLayout tile=new LinearLayout(this);tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(dp(18),dp(12),dp(14),dp(12));tile.setBackground(Aurora.glass(this,ai));tile.setMinimumHeight(dp(100));tile.setFocusable(true);tile.setContentDescription(title);tile.setOnClickListener(v->action.run());
        LinearLayout words=column();TextView name=text(title,22,ink);name.setTypeface(null,Typeface.BOLD);name.setOnClickListener(v->action.run());words.addView(name);words.addView(text(subtitle,15,muted));tile.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        ImageView icon=new ImageView(this);icon.setImageDrawable(Aurora.symbol(ai));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.setMargins(0,dp(16),0,0);page.addView(tile,layout);
    }
    private void startCleaning(){startActivity(new Intent(this,CleanActivity.class));}
    private void open(String tool){startActivity(new Intent(this,MainActivity.class).putExtra("tool",tool));}
    private void settings(){
        Aurora.window(this);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackground(Aurora.background());
        page=column();scroll.addView(page);scroll.setOnApplyWindowInsetsListener((v,insets)->{page.setPadding(dp(10),insets.getSystemWindowInsetTop()+dp(12),dp(10),insets.getSystemWindowInsetBottom()+dp(24));return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Aurora.iconButton(this,"back","Back",this::finish),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView heading=text("Settings",24,ink);heading.setTypeface(null,Typeface.BOLD);heading.setGravity(Gravity.CENTER);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));header.addView(new Space(this),new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(header);
        storageSummary();
        addSetting("Swipe review","Review all gallery photos","Screenshots",()->startActivity(new Intent(this,SwipeActivity.class)));
        addSetting("Compress media","Replace photos or videos with smaller versions","Videos",()->startActivity(new Intent(this,CompressActivity.class)));
        addSetting("Cleanup history","Recent cleanup results","Downloads",this::history);
        addSetting("Privacy & access",accessSummary(),"settings",this::accessSettings);
        addSetting("Clenzy cache","Manage temporary files","Duplicate copies",()->open("cache"));
        addSetting("About Clenzy","Version "+BuildConfig.VERSION_NAME,"Home",()->message("Clenzy "+BuildConfig.VERSION_NAME,"Offline storage review and cleanup."));
        setContentView(scroll);scroll.requestApplyInsets();
    }
    private String accessSummary(){
        boolean all=Build.VERSION.SDK_INT>=30?Environment.isExternalStorageManager():checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
        boolean photos=checkSelfPermission(Build.VERSION.SDK_INT>=33?android.Manifest.permission.READ_MEDIA_IMAGES:android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
        boolean videos=checkSelfPermission(Build.VERSION.SDK_INT>=33?android.Manifest.permission.READ_MEDIA_VIDEO:android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
        return "Shared files: "+(all?"allowed":"limited")+" · Media: "+(photos&&videos?"all":photos||videos||(Build.VERSION.SDK_INT>=34&&checkSelfPermission(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED)?"limited":"off");
    }
    private void accessSettings(){
        AuroraDialog.Builder dialog=new AuroraDialog.Builder(this).setTitle("Privacy & access").setMessage(accessSummary()+"\n\nAll files access covers shared storage. Other apps’ private data remains separate.");
        if(Build.VERSION.SDK_INT>=30)dialog.setPositiveButton("Shared files access",(d,w)->{try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}});
        dialog.setNeutralButton("Photos & videos permission",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("Done",null).show();
    }
    private void storageSummary(){
        StatFs disk=new StatFs(Environment.getDataDirectory().getAbsolutePath());long total=disk.getTotalBytes(),free=disk.getAvailableBytes();
        LinearLayout card=column();card.setPadding(dp(20),dp(18),dp(20),dp(20));card.setBackground(Aurora.glass(this,false));
        card.addView(text("DEVICE STORAGE",12,muted));card.addView(text(size(free)+" free of "+size(total),23,ink));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(total>0?(int)((total-free)*100/total):0);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Aurora.ACCENT));card.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);space.setMargins(0,dp(8),0,dp(4));page.addView(card,space);
    }
    private void addSetting(String title,String subtitle,String symbol,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(4),dp(12),dp(4));row.setBackground(Aurora.glass(this,false));
        ImageView icon=new ImageView(this);icon.setImageDrawable(new LineIcon(symbol,Aurora.ACCENT));row.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30)));
        LinearLayout words=column();words.setPadding(dp(16),0,0,0);TextView name=text(title,17,ink);name.setTypeface(null,Typeface.BOLD);words.addView(name);words.addView(text(subtitle,12,muted));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.setFocusable(true);row.setContentDescription(title);row.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,0);page.addView(row,lp);
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
    private void message(String title,String body){new AuroraDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("Done",null).show();}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(7),0,dp(7));return v;}
    private LinearLayout card(){LinearLayout v=column();GradientDrawable shape=new GradientDrawable();shape.setColor(surface);shape.setCornerRadius(dp(24));v.setBackground(Aurora.glass(this,false));v.setPadding(dp(22),dp(18),dp(22),dp(18));return v;}
    private LinearLayout.LayoutParams spaced(){LinearLayout.LayoutParams v=new LinearLayout.LayoutParams(-1,-2);v.setMargins(0,dp(18),0,dp(18));return v;}
    private MaterialButton button(String text,Runnable action,boolean primary){MaterialButton b=new MaterialButton(this);b.setText(text);b.setAllCaps(false);b.setCornerRadius(dp(20));b.setMinHeight(dp(48));b.setTextColor(primary?Color.WHITE:((getResources().getConfiguration().uiMode&48)==32?Color.rgb(140,176,255):blue));b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?blue:surface));Aurora.button(b);b.setOnClickListener(v->action.run());return b;}
    private String size(long bytes){return Formatter.formatFileSize(this,bytes);}
}
