/*
 * Copyright (C) 2013 Spreadtrum Communications Inc. 
 *
 */

package com.sprd.dialer;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;

import android.view.LayoutInflater;
import android.view.View.MeasureSpec;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.dialer.R;
import com.android.dialer.dialpad.DialpadKeyButton;

public class DialpadButtonLayout extends FrameLayout {
    private final static int BUTTON_COLUMNS = 3;
    private final static int BUTTON_ROWS = 4;

    private int mButtonWidth;
    private int mButtonHeight;

    private int mWidthInc;
    private int mHeightInc;

    private int mWindowWidth;
    private int mButtonMargin;
    private int mButtonLayoutHeight;
    private DisplayMetrics mDisplayMetrics = new DisplayMetrics();
    private WindowManager mWindowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);

    private boolean mIsLayouted = false;

    public DialpadButtonLayout(Context context) {
        super(context);
        init();
    }

    public DialpadButtonLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DialpadButtonLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init(){
        mWindowManager.getDefaultDisplay().getMetrics(mDisplayMetrics);
        Resources resources = getContext().getResources();
        mButtonMargin = resources.getDimensionPixelSize(R.dimen.dialpad_button_layout_button_margin_sprd);
        mWindowWidth = mDisplayMetrics.widthPixels;
        mButtonWidth = (mWindowWidth-mButtonMargin*2)/BUTTON_COLUMNS;
        if(mWindowWidth < 500){
            mButtonHeight = resources.getDimensionPixelSize(R.dimen.dialpad_button_layout_button_height_sprd);
            mButtonLayoutHeight = getContext().getResources().getDimensionPixelSize(R.dimen.dialpad_button_layout_height_sprd);
        } else {
            mButtonHeight = resources.getDimensionPixelSize(R.dimen.dialpad_button_layout_button_height_qhd_sprd);
            mButtonLayoutHeight = getContext().getResources().getDimensionPixelSize(R.dimen.dialpad_button_layout_height_qhd_sprd);
        }
        mWidthInc = mButtonWidth;
        mHeightInc = mButtonHeight;
        Log.d("DialpadButtonLayout","mButtonHeight="+mButtonHeight+
                " mButtonMargin="+mButtonMargin+" mWindowWidth="+mWindowWidth+
                " mButtonWidth="+mButtonWidth+" mButtonLayoutHeight="+mButtonLayoutHeight);
    }

    public int getButtonHeight(){
        return mButtonHeight;
    }

    protected DialpadKeyButton createDialpadButton(int id) {
        LayoutInflater inflater =  (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        DialpadKeyButton button = (DialpadKeyButton)inflater.inflate(R.layout.dialpad_key_sprd, DialpadButtonLayout.this, false);
        button.setId(id);
        button.setSoundEffectsEnabled(false);
        FrameLayout.LayoutParams lParams = new FrameLayout.LayoutParams(mButtonWidth, mButtonHeight);
        button.setLayoutParams(lParams);
        return button;
    }
 

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        DialpadKeyButton dialpadButton;
        TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(new int[] {android.R.attr.selectableItemBackground});
        Drawable drawable = typedArray.getDrawable(0);
        typedArray.recycle();
        dialpadButton = createDialpadButton(R.id.one);
        addView(dialpadButton);

        dialpadButton = createDialpadButton(R.id.two);
        addView(dialpadButton);

        dialpadButton = createDialpadButton(R.id.three);
        addView(dialpadButton);

        dialpadButton = createDialpadButton(R.id.four);
        addView(dialpadButton);

        dialpadButton = createDialpadButton(R.id.five);
        addView(dialpadButton);

        dialpadButton = createDialpadButton(R.id.six);
        addView(dialpadButton);

        dialpadButton = createDialpadButton(R.id.seven);
        addView(dialpadButton);

        dialpadButton = createDialpadButton(R.id.eight);
        addView(dialpadButton);

        dialpadButton = createDialpadButton(R.id.nine);
        addView(dialpadButton);

        dialpadButton = createDialpadButton(R.id.star);
        addView(dialpadButton);

        dialpadButton = createDialpadButton(R.id.zero);
        addView(dialpadButton);

        dialpadButton = createDialpadButton(R.id.pound);
        addView(dialpadButton);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if(mIsLayouted)
            return;

        mIsLayouted = true;
        int i = 0;
        int y = 0;

        int left,buttom;

        for (int row = 0; row < BUTTON_ROWS; row++) {
            int x = 0;
            if(row > 0){
                y += mButtonMargin;
            } 
            for (int col = 0; col < BUTTON_COLUMNS; col++) {
                View child = getChildAt(i);
                if(col < BUTTON_COLUMNS-1){
                    left = x + mButtonMargin;
                } else {
                    left = x;
                }
                child.layout(left+mButtonMargin, y, x + mButtonWidth, y + mButtonHeight);
                x += mWidthInc;
                i++;
            }
            y += mHeightInc;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mWindowWidth, mButtonLayoutHeight);
    }
}
