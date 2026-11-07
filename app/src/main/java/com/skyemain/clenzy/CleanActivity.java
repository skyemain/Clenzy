package com.skyemain.clenzy;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.util.*;

public final class CleanActivity extends AppCompatActivity {
    private CleanupViewModel model;
    private int ink,muted,bg,surface,accent;
    private LinearLayout page,body,footer;
    private TextView selectedLabel;
    private MaterialButton cleanButton;
    private boolean waitingStorage;
    private BottomSheetDialog sheet;
    private String renderedPhase="";
    private TextView busyStatus, sheetSelection;
    private boolean categoryOpened;
    private final Map<String,TextView> categoryCounts=new HashMap<>();
    private List<CleanupGroups.Group> currentGroups=List.of();
    private final java.util.concurrent.ExecutorService previews=java.util.concurrent.Executors.newFixedThreadPool(2);
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private String size(long bytes){return Formatter.formatFileSize(this,Math.max(0,bytes));}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);model=new ViewModelProvider(this).get(CleanupViewModel.class);
        ink=Aurora.INK;muted=Aurora.MUTED;bg=Aurora.BASE;surface=Aurora.SURFACE;accent=Aurora.ACCENT;Aurora.window(this);
        waitingStorage=saved!=null&&saved.getBoolean("waitingStorage");categoryOpened=saved!=null&&saved.getBoolean("categoryOpened");
        model.updates.observe(this,n->render());
        if(saved==null)begin();
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(model.busy){new AlertDialog.Builder(CleanActivity.this).setTitle(model.deleting?"Stop cleanup?":"Stop scan?").setMessage(model.deleting?"Files already removed cannot be restored. Wait for the current operation to finish stopping.":"No files have been changed.").setNegativeButton("Continue",null).setPositiveButton("Stop",(d,w)->model.cancel()).show();}else finish();}});
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("waitingStorage",waitingStorage);out.putBoolean("categoryOpened",categoryOpened);super.onSaveInstanceState(out);}
    @Override protected void onResume(){super.onResume();if(waitingStorage){waitingStorage=false;if(hasStorage())startScan();else render();}}
    private boolean hasStorage(){return Build.VERSION.SDK_INT>=30?Environment.isExternalStorageManager():Build.VERSION.SDK_INT<29&&checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
    private void begin(){
        if(hasStorage()){startScan();return;}
        if(Build.VERSION.SDK_INT==29){new AlertDialog.Builder(this).setTitle("Review photos and videos").setMessage("Android 10 supports media review. Automatic shared-file scanning is available on Android 11 and later.").setPositiveButton("Continue",(d,w)->{startActivity(new Intent(this,MainActivity.class).putExtra("tool","photos"));finish();}).setNegativeButton("Back",(d,w)->finish()).show();return;}
        permissionSheet();
    }
    private void permissionSheet(){
        BottomSheetDialog dialog=new BottomSheetDialog(this);sheet=dialog;LinearLayout content=column();content.setPadding(dp(24),dp(24),dp(24),dp(28));content.setBackground(Aurora.glass(this,false));
        content.addView(text("Storage access",26,ink));content.addView(text("Find large files, downloads and duplicates without choosing folders.",14,muted));
        
        content.addView(button("Continue to storage access",true,()->{dialog.dismiss();if(Build.VERSION.SDK_INT>=30){waitingStorage=true;try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}}else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},21);}));
        content.addView(button("Not now",false,dialog::dismiss));dialog.setContentView(content);dialog.show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==21){if(hasStorage())startScan();else render();}}
    private void startScan(){categoryOpened=false;model.category=ReviewRules.Category.ALL;model.sharedStorage=true;model.ownCache=false;model.media=false;model.downloadsOnly=false;model.scan(false);}
    private void scaffold(String heading){
        page=column();page.setBackground(Aurora.background());page.setPadding(dp(24),dp(12),dp(24),dp(12));page.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(24),insets.getSystemWindowInsetTop()+dp(12),dp(24),insets.getSystemWindowInsetBottom()+dp(10));return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);MaterialButton back=button("‹",false,()->getOnBackPressedDispatcher().onBackPressed());back.setContentDescription("Back");header.addView(back,new LinearLayout.LayoutParams(dp(52),dp(48)));TextView title=text(heading,17,ink);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));MaterialButton options=button("⋯",false,()->startActivity(new Intent(this,HomeActivity.class).putExtra("settings",true)));options.setContentDescription("Settings");header.addView(options,new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));footer=column();page.addView(footer);setContentView(page);page.requestApplyInsets();page.post(page::requestApplyInsets);
    }
    private void render(){
        String phase=model.busy?(model.deleting?"cleaning":"scan"):model.completed?"results":model.scanFailed?"error":model.scanFinished?"review":"access";
        if((phase.equals("scan")||phase.equals("cleaning"))&&phase.equals(renderedPhase)&&busyStatus!=null){busyStatus.setText(model.deleting?"Removing selected files…":model.status);return;}
        renderedPhase=phase;scaffold(phase.equals("results")?"Clenzy":phase.equals("review")?"Your cleanup":phase.equals("cleaning")?"Cleaning up":"Find your space");
        if(model.busy){
            if(sheet!=null)sheet.dismiss();
            TextView title=text(model.deleting?"Cleaning…":"Scanning…",30,ink);title.setGravity(Gravity.CENTER);body.addView(title,gap(46));
            ProgressBar progress=new ProgressBar(this);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(accent));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(90),dp(90));lp.gravity=Gravity.CENTER;lp.setMargins(0,dp(24),0,dp(24));body.addView(progress,lp);
            busyStatus=text("Checking files…",12,muted);busyStatus.setGravity(Gravity.CENTER);body.addView(busyStatus);
            footer.addView(button(model.deleting?"Stop cleanup":"Cancel scan",false,model::cancel));return;
        }
        if(model.completed){
            TextView mark=text("✓",50,accent);mark.setGravity(Gravity.CENTER);body.addView(mark,gap(20));
            TextView title=text(model.removedFiles>0?"Cleanup complete":"Nothing removed",29,ink);title.setGravity(Gravity.CENTER);body.addView(title);
            TextView amount=text(size(model.recoveredBytes),40,ink);amount.setGravity(Gravity.CENTER);body.addView(amount);TextView caption=text("verified files deleted",13,muted);caption.setGravity(Gravity.CENTER);body.addView(caption);
            body.addView(text(model.removedFiles+(model.removedFiles==1?" file removed":" files removed"),16,ink),gap(25));if(model.skippedFiles>0)body.addView(text(model.skippedFiles+" files changed or could not be removed.",13,muted));
            StatFs disk=new StatFs(Environment.getDataDirectory().getAbsolutePath());body.addView(text(size(disk.getAvailableBytes())+" available now",16,ink));footer.addView(button("Back to home",true,this::finish));return;
        }
        if(model.scanFailed){body.addView(text(model.deleting?"Cleanup stopped.":"Let’s try again.",29,ink),gap(35));body.addView(text(model.status,14,muted));footer.addView(button("Storage access",false,this::permissionSheet));footer.addView(button("Scan again",true,this::begin));return;}
        if(!model.scanFinished){body.addView(text("Storage access",26,ink),gap(45));body.addView(text("Allow access to scan shared files.",14,muted));footer.addView(button("Scan my phone",true,this::begin));return;}
        List<CleanupGroups.Group> groups=CleanupGroups.from(model.files);currentGroups=groups;categoryCounts.clear();long total=CleanupGroups.reviewableBytes(groups);
        body.addView(text(size(total),38,ink),gap(16));body.addView(text("Available to review",12,muted));if(model.unreadableFolders>0||model.scanTruncated)body.addView(text("Partial scan · "+model.unreadableFolders+" folders unreadable"+(model.scanTruncated?" · scan limit reached":""),12,muted));
        for(var group:groups){
            LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);styleCard(card);card.setPadding(dp(16),dp(10),dp(16),dp(10));
            ImageView icon=new ImageView(this);icon.setImageDrawable(new LineIcon(group.title(),accent));card.addView(icon,new LinearLayout.LayoutParams(dp(28),dp(28)));
            LinearLayout labels=column();labels.setPadding(dp(12),0,dp(8),0);TextView name=text(group.title(),15,ink);name.setTypeface(null,Typeface.BOLD);name.setPadding(0,dp(2),0,dp(2));labels.addView(name);TextView count=text("",11,muted);count.setPadding(0,dp(2),0,dp(2));labels.addView(count);categoryCounts.put(group.title(),count);card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(text(size(group.bytes())+"  ›",13,ink));card.setFocusable(true);card.setContentDescription(group.title()+", "+size(group.bytes()));card.setOnClickListener(v->showGroup(group));body.addView(card,gap(10));
        }
        MaterialButton details=button("Scan details",false,()->new AlertDialog.Builder(this).setTitle("Scan details").setMessage(model.status+"\nPrimary shared storage only. Categories can overlap; the total counts each file once.").setPositiveButton("Done",null).show());details.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));body.addView(details);
        selectedLabel=text("",12,muted);footer.addView(selectedLabel);cleanButton=button("Clean selected",true,this::confirm);footer.addView(cleanButton);selection();
        String requested=getIntent().getStringExtra("category");if(!categoryOpened&&requested!=null){categoryOpened=true;for(var group:groups)if(group.title().equals(requested)){page.post(()->{if(!isFinishing()&&!isDestroyed())showGroup(group);});break;}}
    }
    private void selection(){if(sheetSelection!=null){sheetSelection.setVisibility(model.selected.isEmpty()?View.GONE:View.VISIBLE);sheetSelection.setText(model.selected.size()+" selected · "+size(ReviewRules.selectedBytes(model.files,model.selected)));}for(var group:currentGroups){TextView count=categoryCounts.get(group.title());if(count!=null){long n=group.files().stream().filter(f->model.selected.contains(f.uri())).count();count.setText(group.files().size()+(group.files().size()==1?" file":" files")+(n>0?" · "+n+" selected":""));}}if(cleanButton==null||selectedLabel==null)return;long bytes=ReviewRules.selectedBytes(model.files,model.selected);selectedLabel.setText(model.selected.size()+" files selected · "+size(bytes));cleanButton.setText(model.selected.isEmpty()?"Clean selected":"Clean selected · "+size(bytes));cleanButton.setEnabled(!model.selected.isEmpty()&&!model.busy);cleanButton.setAlpha(model.selected.isEmpty()?.45f:1f);}
    private void confirm(){new AlertDialog.Builder(this).setTitle("Clear "+size(ReviewRules.selectedBytes(model.files,model.selected))+"?").setMessage("You selected "+model.selected.size()+" files. Shared-file deletion is permanent. Originals in duplicate groups will be kept.").setNegativeButton("Keep reviewing",null).setPositiveButton("Confirm cleanup",(d,w)->model.delete()).show();}
    private void showGroup(CleanupGroups.Group group){
        sheet=new BottomSheetDialog(this);LinearLayout content=column();content.setBackground(Aurora.glass(this,false));content.setPadding(dp(22),dp(16),dp(22),dp(16));
        View handle = new View(this);
        GradientDrawable grip = new GradientDrawable();
        grip.setColor(muted);
        grip.setCornerRadius(dp(3));
        handle.setBackground(grip);
        LinearLayout.LayoutParams gripSize = new LinearLayout.LayoutParams(dp(32),dp(4));
        gripSize.gravity = Gravity.CENTER_HORIZONTAL;
        gripSize.bottomMargin = dp(14);
        content.addView(handle,gripSize);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView categoryIcon = new ImageView(this);
        categoryIcon.setImageDrawable(new LineIcon(group.title(),accent));
        heading.addView(categoryIcon,new LinearLayout.LayoutParams(dp(32),dp(32)));
        LinearLayout titleBlock = column();
        titleBlock.setPadding(dp(14),0,0,0);
        TextView title = text(group.title(),24,ink);
        title.setTypeface(null,Typeface.BOLD);
        title.setPadding(0,0,0,0);
        titleBlock.addView(title);
        titleBlock.addView(text(group.files().size()+(group.files().size()==1?" file":" files"),13,muted));
        heading.addView(titleBlock);
        content.addView(heading);

        if(group.files().isEmpty()){
            content.addView(text(model.unreadableFolders>0||model.scanTruncated?"Scan incomplete. Some folders could not be checked.":"No matching files in scanned phone storage.",14,muted));
            if(Build.VERSION.SDK_INT>=30&&!Environment.isExternalStorageManager()) content.addView(button("Storage access",false,()->{sheet.dismiss();permissionSheet();}));
            content.addView(button("Scan again",false,()->{sheet.dismiss();begin();}));
        } else {
        LinearLayout controls=new LinearLayout(this);RecyclerView list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));FileAdapter adapter=new FileAdapter(group);list.setAdapter(adapter);
        MaterialButton sort=button("Sort",false,()->new AlertDialog.Builder(this).setTitle("Sort files").setItems(new String[]{"Largest first","Oldest first","Newest first","Name"},(d,w)->{adapter.sort=w;adapter.refresh();}).show());controls.addView(sort);
        sheetSelection=text("",12,muted);selection();
        controls.addView(button(group.title().equals("Duplicate copies")?"Select extra copies":"Select all",false,()->{for(var f:adapter.visible)if(!group.keepers().contains(f.uri()))model.selected.add(f.uri());adapter.notifyDataSetChanged();selection();}),new LinearLayout.LayoutParams(0,-2,1));
        controls.addView(button("Clear",false,()->{for(var f:adapter.visible)model.selected.remove(f.uri());adapter.notifyDataSetChanged();selection();}));content.addView(controls);content.addView(list,new LinearLayout.LayoutParams(-1,0,1));content.addView(sheetSelection);
        }
        content.addView(button("Done",true,()->sheet.dismiss()));
        GradientDrawable sheetBackground=new GradientDrawable();sheetBackground.setColor(surface);sheetBackground.setCornerRadius(dp(24));content.setBackground(Aurora.glass(this,false));content.setClipToOutline(true);sheet.setContentView(content,new ViewGroup.LayoutParams(-1,-1));sheet.setOnDismissListener(d->{sheetSelection=null;selection();});sheet.show();android.widget.FrameLayout bottom=sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);if(bottom!=null){bottom.getLayoutParams().height=(int)(getResources().getDisplayMetrics().heightPixels*.85);if(group.files().isEmpty())bottom.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;bottom.requestLayout();sheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);}
    }
    private final class FileAdapter extends RecyclerView.Adapter<FileAdapter.Holder>{
        final CleanupGroups.Group group;List<ReviewFile> visible;int sort;FileAdapter(CleanupGroups.Group group){this.group=group;visible=new ArrayList<>(group.files());}
        void refresh(){visible=new ArrayList<>(group.files());Comparator<ReviewFile> order=switch(sort){case 1->Comparator.comparingLong(ReviewFile::modified);case 2->Comparator.comparingLong(ReviewFile::modified).reversed();case 3->Comparator.comparing(ReviewFile::name,String.CASE_INSENSITIVE_ORDER);default->Comparator.comparingLong(ReviewFile::bytes).reversed();};visible.sort(order);notifyDataSetChanged();}
        final class Holder extends RecyclerView.ViewHolder{TextView name,detail;CheckBox check;ImageView image;Holder(LinearLayout row){super(row);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));image=new ImageView(CleanActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(image,new LinearLayout.LayoutParams(dp(54),dp(58)));LinearLayout labels=column();labels.setPadding(dp(12),0,dp(6),0);name=text("",15,ink);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(0,0,0,dp(3));detail=text("",12,muted);detail.setPadding(0,0,0,0);labels.addView(name);labels.addView(detail);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));check=new CheckBox(CleanActivity.this);check.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));row.addView(check);}}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){LinearLayout row=new LinearLayout(CleanActivity.this);row.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(row);}
        @Override public int getItemCount(){return visible.size();}
        @Override public void onBindViewHolder(Holder holder,int position){ReviewFile file=visible.get(position);boolean keep=group.keepers().contains(file.uri());holder.name.setText(file.name());holder.detail.setText(size(file.bytes())+(keep?" · Original kept":""));holder.check.setOnCheckedChangeListener(null);holder.check.setChecked(model.selected.contains(file.uri()));holder.check.setEnabled(!keep);holder.check.setContentDescription(keep?"Original kept":"Select "+file.name());holder.check.setOnCheckedChangeListener((v,on)->{if(on)model.selected.add(file.uri());else model.selected.remove(file.uri());selection();});holder.image.setImageDrawable(new LineIcon(file.name().toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mov|mkv|webm|avi|3gp)$")?"Videos":"file",accent));holder.image.setTag(file.uri());holder.image.setContentDescription("Preview "+file.name());holder.image.setOnClickListener(v->preview(file));
            previews.execute(()->{try{java.io.File local=new java.io.File(java.net.URI.create(file.uri()));if(Build.VERSION.SDK_INT>=29&&file.name().toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mov|mkv|webm|avi|3gp)$")){Bitmap thumbnail=android.media.ThumbnailUtils.createVideoThumbnail(local,new android.util.Size(160,160),null);runOnUiThread(()->{if(!isDestroyed()&&file.uri().equals(holder.image.getTag()))holder.image.setImageBitmap(thumbnail);});return;}BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(local.getPath(),bounds);if(bounds.outWidth<1)return;BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/160);Bitmap image=BitmapFactory.decodeFile(local.getPath(),options);if(image!=null)runOnUiThread(()->{if(!isDestroyed()&&file.uri().equals(holder.image.getTag()))holder.image.setImageBitmap(image);});}catch(Exception ignored){}});
        }
    }
    private void preview(ReviewFile file){try{Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".files",new java.io.File(java.net.URI.create(file.uri())));String mime=getContentResolver().getType(uri);startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime==null?"*/*":mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception e){new AlertDialog.Builder(this).setMessage("No preview app is available for this file.").setPositiveButton("Done",null).show();}}
    private LinearLayout column(){LinearLayout result=new LinearLayout(this);result.setOrientation(LinearLayout.VERTICAL);return result;}
    private TextView text(String value,int size,int color){TextView result=new TextView(this);result.setText(value);result.setTextSize(size);result.setTextColor(color);result.setPadding(0,dp(7),0,dp(7));return result;}
    private MaterialButton button(String label,boolean primary,Runnable action){MaterialButton result=new MaterialButton(this);result.setText(label);result.setAllCaps(false);result.setCornerRadius(dp(18));result.setMinHeight(dp(primary?56:48));result.setTextColor(primary?Color.WHITE:accent);result.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?Color.rgb(56,99,235):surface));Aurora.button(result);result.setOnClickListener(v->action.run());return result;}
    private LinearLayout.LayoutParams gap(int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(top),0,dp(8));return lp;}
    private void styleCard(View view){GradientDrawable shape=new GradientDrawable();shape.setColor(surface);shape.setCornerRadius(dp(20));view.setBackground(Aurora.glass(this,false));view.setPadding(dp(18),dp(14),dp(18),dp(14));}
    @Override protected void onDestroy(){if(sheet!=null)sheet.dismiss();previews.shutdownNow();super.onDestroy();}
}
