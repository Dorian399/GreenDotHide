package com.dorian15.greendothide;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ModuleStatusView extends LinearLayout {

    private ImageView icon;
    private TextView statusText;

    public ModuleStatusView(Context context) {
        this(context, null);
    }

    public ModuleStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ModuleStatusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
    private void init(Context context, AttributeSet attrs) {

        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                (int) (130 * getResources().getDisplayMetrics().density)
        );

        setLayoutParams(params);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setBackgroundResource(R.drawable.module_status_loading);

        this.icon = new ImageView(context);
        LayoutParams imgParams = new LayoutParams(
                dpToPx(50),
                dpToPx(50),
                1f
        );
        this.icon.setLayoutParams(imgParams);
        this.icon.setImageResource(R.drawable.baseline_info);

        this.statusText = new TextView(context);
        LayoutParams textParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                1f
        );
        this.statusText.setLayoutParams(textParams);
        this.statusText.setText("Loading module status...");
        this.statusText.setTextSize(20);
        this.statusText.setTextColor( getResources().getColor( R.color.white, getContext().getTheme() ) );

        addView(this.icon);
        addView(this.statusText);
    }
}