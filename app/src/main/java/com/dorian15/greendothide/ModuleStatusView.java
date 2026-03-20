package com.dorian15.greendothide;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ModuleStatusView extends LinearLayout {

    public static class Status {
        public static final int STATUS_ACTIVE = 0;
        public static final int STATUS_INACTIVE = 1;
        public static final int STATUS_LOADING = 2;
    }

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

    public void setStatus(int status) {
        switch(status){
            case Status.STATUS_ACTIVE:
                this.statusText.setText(R.string.module_status_active);
                this.icon.setImageResource(R.drawable.baseline_check_circle);
                setBackgroundResource(R.drawable.module_status_active);
                break;

            case Status.STATUS_LOADING:
                this.statusText.setText(R.string.module_status_loading);
                this.icon.setImageResource(R.drawable.baseline_info);
                setBackgroundResource(R.drawable.module_status_loading);
                break;

            case Status.STATUS_INACTIVE:
            default:
                this.statusText.setText(R.string.module_status_inactive);
                this.icon.setImageResource(R.drawable.baseline_error);
                setBackgroundResource(R.drawable.module_status_inactive);
                break;
        }
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

        this.icon = new ImageView(context);
        LayoutParams imgParams = new LayoutParams(
                dpToPx(50),
                dpToPx(50),
                1f
        );
        this.icon.setLayoutParams(imgParams);

        this.statusText = new TextView(context);
        LayoutParams textParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                1f
        );
        this.statusText.setLayoutParams(textParams);
        this.statusText.setTextSize(20);
        this.statusText.setTextColor( getResources().getColor( R.color.white, getContext().getTheme() ) );

        setStatus(Status.STATUS_LOADING);

        addView(this.icon);
        addView(this.statusText);
    }
}