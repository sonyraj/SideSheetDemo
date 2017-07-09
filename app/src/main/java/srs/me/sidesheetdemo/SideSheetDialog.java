package srs.me.sidesheetdemo;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v7.app.AppCompatDialog;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * @author Sony Raj on 04-07-2017.
 */

public class SideSheetDialog extends AppCompatDialog {

    private SideSheetBehavior<FrameLayout> mBehavior;

    boolean mCancelable;
    private boolean mCancelledOnTouchOutSide = true;
    private boolean mCancelledOnTouchOutSideSet;
    private SideSheetBehavior.SideSheetCallback mCallback =
            new SideSheetBehavior.SideSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View sideSheet, @SideSheetBehavior.State int newState) {
                    if (newState == SideSheetBehavior.STATE_HIDDEN){
                        cancel();
                    }
                }

                @Override
                public void onSlide(@NonNull View sideSheet, float slideOffset) {

                }
            };


    public SideSheetDialog(Context context) {
        this(context, 0);
    }

    public SideSheetDialog(Context context, int theme) {
        super(context, getThemeResId(context, theme));
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    protected SideSheetDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        mCancelable = cancelable;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (window != null) {
            if (Build.VERSION.SDK_INT >= 21) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            }
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
    }


    @Override
    public void setContentView(View view) {
        super.setContentView(wrapInSideSheet(0,view,null));
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(wrapInSideSheet(0,view,params));
    }

    @Override
    public void setCancelable(boolean cancelable) {
        super.setCancelable(cancelable);
        if (mCancelable != cancelable){
            mCancelable = cancelable;
            if (mBehavior != null){
                mBehavior.setHideable(cancelable);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mBehavior != null){
            mBehavior.setState(SideSheetBehavior.STATE_COLLAPSED);
        }
    }

    @Override
    public void setCanceledOnTouchOutside(boolean cancel) {
        super.setCanceledOnTouchOutside(cancel);
        if (cancel && !mCancelable){
            mCancelable = true;
        }
        mCancelledOnTouchOutSide  =cancel;
        mCancelledOnTouchOutSideSet = true;
    }

    private View wrapInSideSheet(int layoutResId, View view, ViewGroup.LayoutParams params){
        final CoordinatorLayout coordinatorLayout = (CoordinatorLayout)View.inflate(getContext(),
                R.layout.side_sheet_dialog,null);

        if (layoutResId != 0 && view == null){
            view = getLayoutInflater().inflate(layoutResId,coordinatorLayout,false);
        }

        FrameLayout sideSheet = (FrameLayout) coordinatorLayout.findViewById(R.id.side_sheet);
        mBehavior = SideSheetBehavior.from(sideSheet);
        mBehavior.setSideSheetCallback(mCallback);
        mBehavior.setHideable(mCancelable);
        if (params == null){
            sideSheet.addView(view);
        } else {
            sideSheet.addView(view,params);
        }
        coordinatorLayout.findViewById(R.id.side_sheet_touch_out_side)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mCancelable && isShowing() &&
                                shouldWindowCloseOnTouchOutSide()){
                            cancel();
                        }
                    }
                });

        ViewCompat.setAccessibilityDelegate(sideSheet, new AccessibilityDelegateCompat(){
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                                                          AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (mCancelable) {
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_DISMISS);
                    info.setDismissable(true);
                } else {
                    info.setDismissable(false);
                }
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == AccessibilityNodeInfoCompat.ACTION_DISMISS && mCancelable) {
                    cancel();
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });


        return coordinatorLayout;
    }

    boolean shouldWindowCloseOnTouchOutSide(){
        if(!mCancelledOnTouchOutSideSet){
            TypedArray a = getContext().obtainStyledAttributes(
                    new int[]{android.R.attr.windowCloseOnTouchOutside}
            );
            mCancelledOnTouchOutSide = a.getBoolean(0,true);
            a.recycle();
            mCancelledOnTouchOutSideSet = true;
        }
        return mCancelledOnTouchOutSide;
    }


    private static int getThemeResId(Context context, int themeId) {
        if (themeId == 0) {
            // If the provided theme is 0, then retrieve the dialogTheme from our theme
            TypedValue outValue = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    R.attr.sideSheetDialogTheme, outValue, true)) {
                themeId = outValue.resourceId;
            } else {
                // bottomSheetDialogTheme is not provided; we default to our light theme
                themeId = R.style.Theme_Me_Light_SideSheetDialog;
            }
        }
        return themeId;
    }



}
