package com.skyemain.clenzy;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;

/** Shared app-owned dialogs. Android permission and Trash sheets remain system-owned. */
public final class AuroraDialog {
    private AuroraDialog(){}
    public static final class Builder {
        private final Context context;
        private CharSequence title,message;
        private String[] items;
        private DialogInterface.OnClickListener itemListener;
        private final ArrayList<Action> actions=new ArrayList<>();
        private record Action(CharSequence text,int which,DialogInterface.OnClickListener listener){}
        public Builder(Context context){this.context=context;}
        public Builder setTitle(CharSequence text){title=text;return this;}
        public Builder setMessage(CharSequence text){message=text;return this;}
        public Builder setItems(String[] values,DialogInterface.OnClickListener listener){items=values;itemListener=listener;return this;}
        public Builder setPositiveButton(CharSequence text,DialogInterface.OnClickListener listener){actions.add(new Action(text,DialogInterface.BUTTON_POSITIVE,listener));return this;}
        public Builder setNegativeButton(CharSequence text,DialogInterface.OnClickListener listener){actions.add(new Action(text,DialogInterface.BUTTON_NEGATIVE,listener));return this;}
        public Builder setNeutralButton(CharSequence text,DialogInterface.OnClickListener listener){actions.add(new Action(text,DialogInterface.BUTTON_NEUTRAL,listener));return this;}
        private int dp(int n){return Aurora.dp(context,n);}
        private TextView text(CharSequence value,int size,int color){TextView view=new TextView(context);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setPadding(0,dp(6),0,dp(12));return view;}
        private MaterialButton action(Dialog dialog,CharSequence name,int which,DialogInterface.OnClickListener listener){
            MaterialButton button=new MaterialButton(context);button.setText(name);Aurora.button(button);
            button.setOnClickListener(v->{dialog.dismiss();if(listener!=null)listener.onClick(dialog,which);});return button;
        }
        public Dialog create(){
            Dialog dialog=new androidx.activity.ComponentDialog(context);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(16),dp(20),dp(16));root.setBackground(Aurora.panel(context,true));
            ScrollView scroll=new ScrollView(context){@Override protected void onMeasure(int width,int height){super.onMeasure(width,MeasureSpec.makeMeasureSpec(context.getResources().getDisplayMetrics().heightPixels*3/4,MeasureSpec.AT_MOST));}};
            LinearLayout content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
            if(title!=null){TextView heading=text(title,23,Aurora.INK);heading.setTypeface(null,Typeface.BOLD);content.addView(heading);}
            if(message!=null)content.addView(text(message,15,Aurora.MUTED));
            if(items!=null)for(int n=0;n<items.length;n++){MaterialButton button=action(dialog,items[n],n,itemListener);button.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);content.addView(button,new LinearLayout.LayoutParams(-1,-2));}
            for(Action a:actions)content.addView(action(dialog,a.text(),a.which(),a.listener()),new LinearLayout.LayoutParams(-1,-2));
            scroll.addView(content);root.addView(scroll);dialog.setContentView(root);dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.setOnShowListener(d->dialog.getWindow().setLayout(context.getResources().getDisplayMetrics().widthPixels-dp(24),ViewGroup.LayoutParams.WRAP_CONTENT));
            return dialog;
        }
        public Dialog show(){Dialog dialog=create();dialog.show();return dialog;}
    }
}
