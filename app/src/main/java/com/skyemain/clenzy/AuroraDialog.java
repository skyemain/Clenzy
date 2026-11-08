package com.skyemain.clenzy;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;
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
        private GradientDrawable surface(int color, int radius) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(dp(radius));
            return drawable;
        }

        private Button action(Dialog dialog, CharSequence name, int which,
                              DialogInterface.OnClickListener listener, boolean row, boolean primary) {
            Button button = new Button(context);
            button.setText(name);
            button.setAllCaps(false);
            button.setTextSize(15);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(dp(48));
            button.setMinimumHeight(dp(48));
            button.setPadding(dp(12), dp(8), dp(12), dp(8));
            button.setGravity(Gravity.CENTER_VERTICAL | (row ? Gravity.START : Gravity.CENTER_HORIZONTAL));
            button.setTextColor(primary ? 0xff14202b : row ? Aurora.INK : 0xffb7dbff);
            button.setStateListAnimator(null);
            button.setBackgroundTintList(null);
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2877baff),
                surface(primary ? 0xffb7dbff : Color.TRANSPARENT, 12), surface(Color.WHITE, 12)));
            button.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) listener.onClick(dialog, which);
            });
            return button;
        }

        public Dialog create() {
            Dialog dialog = new androidx.activity.ComponentDialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            ScrollView scroll = new ScrollView(context) {
                @Override protected void onMeasure(int width, int height) {
                    int limit = context.getResources().getDisplayMetrics().heightPixels * 3 / 4;
                    super.onMeasure(width, MeasureSpec.makeMeasureSpec(
                        MeasureSpec.getMode(height) == MeasureSpec.UNSPECIFIED ? limit
                            : Math.min(limit, MeasureSpec.getSize(height)), MeasureSpec.AT_MOST));
                }
            };
            scroll.setBackground(surface(0xff1c2430, 20));
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(16), dp(14), dp(16), dp(12));
            if (title != null) {
                TextView heading = text(title, 21, Aurora.INK);
                heading.setTypeface(null, Typeface.BOLD);
                content.addView(heading);
            }
            if (message != null) content.addView(text(message, 15, Aurora.MUTED));
            if (items != null) {
                for (int index = 0; index < items.length; index++) {
                    content.addView(action(dialog, items[index], index, itemListener, true, false),
                        new LinearLayout.LayoutParams(-1, -2));
                }
            }
            if (!actions.isEmpty()) {
                LinearLayout footer = new LinearLayout(context);
                boolean stack = actions.size() > 2;
                footer.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
                footer.setGravity(Gravity.END);
                footer.setPadding(0, dp(6), 0, 0);
                for (Action a : actions) {
                    boolean primary = items == null && actions.size() == 2
                        && a.which() == DialogInterface.BUTTON_POSITIVE;
                    Button control = action(dialog, a.text(), a.which(), a.listener(), stack, primary);
                    LinearLayout.LayoutParams params = stack ? new LinearLayout.LayoutParams(-1, -2)
                        : actions.size() == 1 ? new LinearLayout.LayoutParams(-2, -2)
                        : new LinearLayout.LayoutParams(0, -2, 1);
                    footer.addView(control, params);
                }
                content.addView(footer);
            }
            scroll.addView(content);
            dialog.setContentView(scroll);
            Window window = dialog.getWindow();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            dialog.setOnShowListener(d -> window.setLayout(
                Math.min(dp(420), context.getResources().getDisplayMetrics().widthPixels - dp(40)), -2));
            return dialog;
        }
        public Dialog show(){Dialog dialog=create();dialog.show();return dialog;}
    }
}
