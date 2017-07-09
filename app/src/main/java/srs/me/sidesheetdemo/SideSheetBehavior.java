package srs.me.sidesheetdemo;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.AbsSavedState;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * @author Sony Raj on 04-07-2017.
 */

public class SideSheetBehavior<V extends View> extends CoordinatorLayout.Behavior<V> {


    public static final int STATE_DRAGGING = 1;
    public static final int STATE_SETTLING = 2;
    public static final int STATE_EXPANDED = 3;
    public static final int STATE_COLLAPSED = 4;
    public static final int STATE_HIDDEN = 5;
    public static final int PEEK_WIDTH_AUTO = -1;
    private static final float HIDE_THRESHOLD = 0.5f;
    private static final float HIDE_FRICTION = 0.1f;
    int mMinOffset;
    int mMaxOffset;
    boolean mHideable;
    @SideSheetBehavior.State
    int mState = STATE_COLLAPSED;
    ViewDragHelper mViewDragHelper;
    int mParentWidth;
    WeakReference<V> mViewRef;
    WeakReference<View> mNestedScrollingChildRef;
    int mActivePointerId;
    boolean mTouchingScrollingChild;
    private float mMaximumVelocity;
    private int mPeekWidth;
    private boolean mPeekWidthAuto;
    private int mPeekWidthMin;
    private boolean mSkipCollapsed;
    private boolean mIgnoreEvents;
    private int mLastNestedScrollDx;
    private boolean mNestedScrolled;
    private SideSheetCallback mCallback;
    private final ViewDragHelper.Callback mDragCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            if (mState == STATE_DRAGGING) {
                return false;
            }
            if (mTouchingScrollingChild) {
                return false;
            }
            if (mState == STATE_EXPANDED && mActivePointerId == pointerId) {
                View scroll = mNestedScrollingChildRef.get();
                if (scroll != null && ViewCompat.canScrollHorizontally(scroll, -1)) {
                    return false;
                }
            }
            return mViewRef != null && mViewRef.get() == child;
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            dispatchOnSlide(left);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (state == ViewDragHelper.STATE_DRAGGING) {
                setStateInternal(STATE_DRAGGING);
            }
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            int left;
            @State int targetState;
            if (xvel < 0) {
                left = mMinOffset;
                targetState = STATE_EXPANDED;
            } else if (mHideable && shouldHide(releasedChild, xvel)) {
                left = mParentWidth;
                targetState = STATE_HIDDEN;
            } else if (xvel == 0.f) {
                int currentLeft = releasedChild.getLeft();
                if (Math.abs(currentLeft - mMinOffset) < Math.abs(currentLeft - mMaxOffset)) {
                    left = mMinOffset;
                    targetState = STATE_EXPANDED;
                } else {
                    left = mMaxOffset;
                    targetState = STATE_COLLAPSED;
                }
            } else {
                left = mMaxOffset;
                targetState = STATE_COLLAPSED;
            }
            if (mViewDragHelper.settleCapturedViewAt(releasedChild.getTop(), left)) {
                setStateInternal(STATE_SETTLING);
                ViewCompat.postOnAnimation(releasedChild,
                        new SettleRunnable(releasedChild, targetState));
            } else {
                setStateInternal(targetState);
            }
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return child.getTop();
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return MathUtils.constrain(left, mMinOffset, mHideable ? mParentWidth : mMaxOffset);
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            if (mHideable) {
                return mParentWidth - mMinOffset;
            } else {
                return mMaxOffset - mMinOffset;
            }
        }
    };
    private VelocityTracker mVelocityTracker;
    private int mInitialX;

    public SideSheetBehavior() {
    }

    public SideSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.side_sheet_behaviour_layout);
        TypedValue value = ta.peekValue(R.styleable.side_sheet_behaviour_layout_side_sheet_behavior_peek_width);
        if (value != null && value.data == PEEK_WIDTH_AUTO) {
            setPeekWidth(value.data);
        } else {
            setPeekWidth(ta.getDimensionPixelSize(R.styleable.side_sheet_behaviour_layout_side_sheet_behavior_peek_width, PEEK_WIDTH_AUTO));
        }
        setHideable(ta.getBoolean(R.styleable.side_sheet_behaviour_layout_side_sheet_behavior_layout_behavior_hideable, false));
        setSkipCollapsed(ta.getBoolean(R.styleable.side_sheet_behaviour_layout_side_sheet_behavior_layout_behavior_skip_collapsed, false));
        ta.recycle();
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    public static <V extends View> SideSheetBehavior<V> from(V view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof CoordinatorLayout.LayoutParams)) {
            throw new IllegalArgumentException("The view is not a child of CoordinatorLayout");
        }
        CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) params).getBehavior();

        if (!(behavior instanceof SideSheetBehavior)) {
            throw new IllegalArgumentException(
                    "The view is not associated with SideSheetBehavior");
        }
        return (SideSheetBehavior<V>) behavior;
    }

    @Override
    public Parcelable onSaveInstanceState(CoordinatorLayout parent, V child) {
        return new SavedState(super.onSaveInstanceState(parent, child), mState);
    }

    @Override
    public void onRestoreInstanceState(CoordinatorLayout parent, V child, Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(parent, child, state);

        if (ss.state == STATE_DRAGGING || ss.state == STATE_SETTLING) {
            mState = STATE_COLLAPSED;
        } else {
            mState = ss.state;
        }
    }

    @Override
    public boolean onLayoutChild(CoordinatorLayout parent, V child, int layoutDirection) {
        if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
            ViewCompat.setFitsSystemWindows(child, true);
        }

        int savedLeft = child.getLeft();
        parent.onLayoutChild(child, layoutDirection);

        mParentWidth = parent.getWidth();
        int peekWidth;

        if (mPeekWidthAuto) {
            if (mPeekWidthMin == 0) {
                mPeekWidthMin = parent.getResources().getDimensionPixelSize(
                        R.dimen.side_sheet_peek_width_min);
            }

            int parentHeight = parent.getHeight();
            peekWidth = Math.max(mPeekWidthMin, mParentWidth - parentHeight * 9 / 45);
        } else {
            peekWidth = mPeekWidth;
        }

        peekWidth = mParentWidth ;

        mMinOffset = Math.max(0, mParentWidth - child.getWidth());
        mMaxOffset = Math.max(mParentWidth - peekWidth, mMinOffset);

        if (mState == STATE_EXPANDED) {
            ViewCompat.offsetLeftAndRight(child, mMinOffset);
        } else if (mHideable && mState == STATE_HIDDEN) {
            ViewCompat.offsetLeftAndRight(child, mParentWidth);
        } else if (mState == STATE_COLLAPSED) {
            ViewCompat.offsetLeftAndRight(child, mMaxOffset);
        } else if (mState == STATE_DRAGGING || mState == STATE_SETTLING) {
            ViewCompat.offsetLeftAndRight(child, savedLeft - child.getLeft());
        }
        if (mViewDragHelper == null) {
            mViewDragHelper = ViewDragHelper.create(parent, mDragCallback);
        }
        mViewRef = new WeakReference<>(child);
        mNestedScrollingChildRef = new WeakReference<>(findScrollingChild(child));

        return true;
    }


    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (!child.isShown()) {
            mIgnoreEvents = true;
            return false;
        }
        int action = MotionEventCompat.getActionMasked(event);
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        switch (action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchingScrollingChild = false;
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;

                if (mIgnoreEvents) {
                    mIgnoreEvents = false;
                    return false;
                }

                break;

            case MotionEvent.ACTION_DOWN:
                int initialY = (int) event.getY();
                mInitialX = (int) event.getX();
                View scroll = mNestedScrollingChildRef.get();
                if (scroll != null && parent.isPointInChildBounds(child, mInitialX, initialY)) {
                    mActivePointerId = event.getPointerId(event.getActionIndex());
                    mTouchingScrollingChild = true;
                }
                mIgnoreEvents = mActivePointerId == MotionEvent.INVALID_POINTER_ID &&
                        !parent.isPointInChildBounds(child, mInitialX, initialY);
                break;
        }
        if (!mIgnoreEvents && mViewDragHelper.shouldInterceptTouchEvent(event)) {
            return true;
        }

        View scroll = mNestedScrollingChildRef.get();
        return action == MotionEvent.ACTION_MOVE && scroll != null &&
                !mIgnoreEvents && mState == STATE_DRAGGING &&
                Math.abs(mInitialX - event.getX()) > mViewDragHelper.getTouchSlop();

    }

    @Override
    public boolean onTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (!child.isShown()) {
            return false;
        }
        int action = MotionEventCompat.getActionMasked(event);
        if (mState == STATE_DRAGGING && action == MotionEvent.ACTION_DOWN) {
            return true;
        }

        mViewDragHelper.processTouchEvent(event);

        if (action == MotionEvent.ACTION_DOWN) {
            reset();
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }

        mVelocityTracker.addMovement(event);

        if (action == MotionEvent.ACTION_MOVE && !mIgnoreEvents) {
            if (Math.abs(mInitialX - event.getX()) > mViewDragHelper.getTouchSlop()) {
                mViewDragHelper.captureChildView(child, event.getPointerId(event.getActionIndex()));
            }
        }

        return !mIgnoreEvents;
    }

    @Override
    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout, V child, View directTargetChild, View target, int nestedScrollAxes) {
        mLastNestedScrollDx = 0;
        mNestedScrolled = false;

        return (nestedScrollAxes & ViewCompat.SCROLL_AXIS_HORIZONTAL) != 0;
    }

    @Override
    public void onNestedPreScroll(CoordinatorLayout coordinatorLayout, V child, View target, int dx, int dy, int[] consumed) {
        View scrollingChild = mNestedScrollingChildRef.get();
        if (target != scrollingChild) {
            return;
        }

        int currentLeft = child.getLeft();
        int newLeft = currentLeft - dx;

        if (dx > 0) {
            if (newLeft < mMinOffset) {
                consumed[1] = currentLeft - mMinOffset;
                ViewCompat.offsetLeftAndRight(child, -consumed[1]);
                setStateInternal(STATE_EXPANDED);
            } else {
                consumed[1] = dx;
                ViewCompat.offsetLeftAndRight(child, -dx);
                setStateInternal(STATE_DRAGGING);
            }
        } else if (dx < 0) {
            if (ViewCompat.canScrollHorizontally(child, -1)) {
                if (newLeft <= mMaxOffset || mHideable) {
                    consumed[1] = dx;
                    ViewCompat.offsetLeftAndRight(child, -dx);
                    setStateInternal(STATE_DRAGGING);
                } else {
                    consumed[1] = currentLeft - mMaxOffset;
                    ViewCompat.offsetLeftAndRight(child, -consumed[1]);
                    setStateInternal(STATE_COLLAPSED);
                }
            }
        }

        dispatchOnSlide(child.getLeft());
        mLastNestedScrollDx = dx;
        mNestedScrolled = true;
    }

    @Override
    public void onStopNestedScroll(CoordinatorLayout coordinatorLayout, V child, View target) {
        if (child.getLeft() == mMinOffset) {
            setStateInternal(STATE_EXPANDED);
            return;
        }

        if (target != mNestedScrollingChildRef.get() && !mNestedScrolled) {
            return;
        }

        int left;
        int targetState;

        if (mLastNestedScrollDx > 0) {
            left = mMinOffset;
            targetState = STATE_EXPANDED;
        } else if (mHideable && shouldHide(child, getXVelocity())) {
            left = mParentWidth;
            targetState = STATE_HIDDEN;
        } else if (mLastNestedScrollDx == 0) {
            int currentLeft = child.getLeft();
            if (Math.abs(currentLeft - mMinOffset) < Math.abs(currentLeft - mMaxOffset)) {
                left = mMinOffset;
                targetState = STATE_EXPANDED;
            } else {
                left = mMaxOffset;
                targetState = STATE_COLLAPSED;
            }
        } else {
            left = mMaxOffset;
            targetState = STATE_COLLAPSED;
        }

        if (mViewDragHelper.smoothSlideViewTo(child, child.getTop(), left)) {
            setStateInternal(STATE_SETTLING);
            ViewCompat.postOnAnimation(child, new SettleRunnable(child, targetState));
        } else {
            setStateInternal(targetState);
        }
        mNestedScrolled = false;
    }


    @Override
    public boolean onNestedPreFling(CoordinatorLayout coordinatorLayout, V child, View target, float velocityX, float velocityY) {
        return target == mNestedScrollingChildRef.get() &&
                (mState != STATE_EXPANDED ||
                        super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY));
    }

    public final int getPeekWidth() {
        return mPeekWidthAuto ? PEEK_WIDTH_AUTO : mPeekWidth;
    }

    public final void setPeekWidth(int peekWidth) {
        boolean layout = false;
        if (peekWidth == PEEK_WIDTH_AUTO) {
            if (!mPeekWidthAuto) {
                mPeekWidthAuto = true;
                layout = true;
            }
        } else if (mPeekWidthAuto || mPeekWidth != peekWidth) {
            mPeekWidthAuto = false;
            mPeekWidth = Math.max(0, peekWidth);
            mMaxOffset = mParentWidth - peekWidth;
            layout = true;
        }

        if (layout && mState == STATE_COLLAPSED && mViewRef != null) {
            V view = mViewRef.get();
            if (view != null) {
                view.requestLayout();
            }
        }
    }

    public boolean isHideable() {
        return mHideable;
    }

    public void setHideable(boolean hideable) {
        mHideable = hideable;
    }

    public boolean getSkipCollapsed() {
        return mSkipCollapsed;
    }

    private void setSkipCollapsed(boolean skipCollapsed) {
        mSkipCollapsed = skipCollapsed;
    }

    public void setSideSheetCallback(SideSheetCallback callback) {
        mCallback = callback;
    }

    public final void setState(final @State int state) {
        if (state == mState) {
            return;
        }

        if (mViewRef == null) {
            if (state == STATE_COLLAPSED || state == STATE_EXPANDED ||
                    (mHideable && state == STATE_HIDDEN)) {
                mState = state;
            }
            return;
        }

        final View child = mViewRef.get();
        if (child == null) {
            return;
        }

        ViewParent parent = child.getParent();
        if (parent != null && parent.isLayoutRequested() &&
                ViewCompat.isAttachedToWindow(child)) {
            child.post(new Runnable() {
                @Override
                public void run() {
                    startSettlingAnimation(child, state);
                }
            });
        } else {
            startSettlingAnimation(child, state);
        }


    }

    private void startSettlingAnimation(View child, int state) {
        int left;
        if (state == STATE_COLLAPSED) {
            left = mMaxOffset;
        } else if (state == STATE_EXPANDED) {
            left = mMinOffset;
        } else if (mHideable && state == STATE_HIDDEN) {
            left = mParentWidth;
        } else {
            throw new IllegalArgumentException("Illegal state argument " + state);
        }
        setStateInternal(STATE_SETTLING);
        if (mViewDragHelper.smoothSlideViewTo(child, child.getTop(), left)) {
            ViewCompat.postOnAnimation(child, new SettleRunnable(child, state));
        }
    }

    private float getXVelocity() {
        mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
        return VelocityTrackerCompat.getXVelocity(mVelocityTracker, mActivePointerId);
    }

    private void reset() {
        mActivePointerId = ViewDragHelper.INVALID_POINTER;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private View findScrollingChild(View view) {
        if (view instanceof NestedScrollingChild) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                View scrollingChild = findScrollingChild(group.getChildAt(i));
                if (scrollingChild != null) {
                    return scrollingChild;
                }
            }
        }
        return null;
    }

    boolean shouldHide(View child, float xVel) {
        if (mSkipCollapsed) {
            return true;
        }
        if (child.getLeft() < mMaxOffset) {
            return false;
        }

        final float newLeft = child.getLeft() + xVel * HIDE_FRICTION;
        return Math.abs(newLeft - mMaxOffset) / (float) mPeekWidth > HIDE_THRESHOLD;
    }

    void setStateInternal(int state) {
        if (mState == state) {
            return;
        }
        mState = state;
        View sideSheet = mViewRef.get();
        if (sideSheet != null && mCallback != null) {
            mCallback.onStateChanged(sideSheet, state);
        }
    }

    void dispatchOnSlide(int left) {
        View sideSheet = mViewRef.get();
        if (sideSheet != null && mCallback != null) {
            if (left > mMaxOffset) {
                mCallback.onSlide(sideSheet, (float) (mMaxOffset - left) / (mParentWidth - mMaxOffset));
            } else {
                mCallback.onSlide(sideSheet, (float) (mMaxOffset - left) / (mMaxOffset - mMinOffset));
            }
        }
    }

    @IntDef({STATE_EXPANDED, STATE_COLLAPSED, STATE_DRAGGING, STATE_SETTLING, STATE_HIDDEN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    public abstract static class SideSheetCallback {

        public abstract void onStateChanged(@NonNull View sideSheet, @State int newState);

        public abstract void onSlide(@NonNull View sideSheet, float slideOffset);
    }

    protected static class SavedState extends AbsSavedState {
        public static final Creator<SavedState> CREATOR = ParcelableCompat.newCreator(
                new ParcelableCompatCreatorCallbacks<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                        return new SavedState(in, loader);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                });
        @BottomSheetBehavior.State
        final int state;

        public SavedState(Parcel source) {
            this(source, null);
        }

        public SavedState(Parcel source, ClassLoader loader) {
            super(source, loader);
            //noinspection ResourceType
            state = source.readInt();
        }

        public SavedState(Parcelable superState, @BottomSheetBehavior.State int state) {
            super(superState);
            this.state = state;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(state);
        }
    }

    private static class MathUtils {

        static int constrain(int amount, int low, int high) {
            return amount < low ? low : (amount > high ? high : amount);
        }

        static float constrain(float amount, float low, float high) {
            return amount < low ? low : (amount > high ? high : amount);
        }

    }

    private class SettleRunnable implements Runnable {

        private final View mView;

        @State
        private final int mTargetState;

        private SettleRunnable(View view, @State int targetState) {
            mView = view;
            mTargetState = targetState;
        }


        @Override
        public void run() {
            if (mViewDragHelper != null && mViewDragHelper.continueSettling(true)) {
                ViewCompat.postOnAnimation(mView, this);
            } else {
                setStateInternal(mTargetState);
            }
        }
    }

}
