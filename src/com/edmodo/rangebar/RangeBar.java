/*
 * Copyright 2013, Edmodo, Inc. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with the License.
 * You may obtain a copy of the License in the LICENSE file, or at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" 
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language 
 * governing permissions and limitations under the License. 
 */

package com.edmodo.rangebar;

import org.bogus.geocaching.egpx.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * The RangeBar is a double-sided version of a {@link android.widget.SeekBar}
 * with discrete values. Whereas the thumb for the SeekBar can be dragged to any
 * position in the bar, the RangeBar only allows its thumbs to be dragged to
 * discrete positions (denoted by tick marks) in the bar. When released, a
 * RangeBar thumb will snap to the nearest tick mark.
 * <p>
 * Clients of the RangeBar can attach a
 * {@link RangeBar#OnRangeBarChangeListener} to be notified when the thumbs have
 * been moved.
 */
public class RangeBar extends View {

    // Member Variables ////////////////////////////////////////////////////////

    private static final String LOG_TAG = "RangeBar";

    // Default values for variables
    private static final int DEFAULT_TICK_COUNT = 3;
    private static final float DEFAULT_TICK_HEIGHT_DP = 24;
    private static final float DEFAULT_TICK_WIDTH_DP = 1;
    private static final float DEFAULT_BAR_WEIGHT_DP = 2;
    private static final int DEFAULT_BAR_COLOR = Color.LTGRAY;
    private static final float DEFAULT_CONNECTING_LINE_WEIGHT_DP = 4;

    // Corresponds to android.R.color.holo_blue_light.
    private static final int DEFAULT_CONNECTING_LINE_COLOR = 0xff33b5e5;

    // Instance variables for all of the customizable attributes
    private int mTickCount;
    private float mTickHeight, mTickWidth;
    private float mBarWeight;
    private int mBarColor;
    private float mConnectingLineWeight;
    private int mConnectingLineColor;
    private int mThumbDrawableResId;

    // setTickCount only resets indices before a thumb has been pressed or a
    // setThumbIndices() is called, to correspond with intended usage
    private boolean mFirstSetTickCount = true;

    private int mDefaultWidth = 500;

    private Thumb mLeftThumb;
    private Thumb mRightThumb;
    private Bar mBar;
    private ConnectingLine mConnectingLine;

    private RangeBar.OnRangeBarChangeListener mListener;
    private int mLeftIndex = 0;
    private int mRightIndex = mTickCount - 1;

    private int mDrawableWidth, mDrawableHeight;
    
    // Constructors ////////////////////////////////////////////////////////////

    public RangeBar(Context context) {
        super(context);
    }

    public RangeBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        rangeBarInit(context, attrs);
    }

    public RangeBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        rangeBarInit(context, attrs);
    }

    // View Methods ////////////////////////////////////////////////////////////

    /*
    OMFG! What actually should I save here?
    + current thumbs position?
    @Override
    public Parcelable onSaveInstanceState() {

        final Bundle bundle = new Bundle();

        bundle.putParcelable("instanceState", super.onSaveInstanceState());

        bundle.putInt("TICK_COUNT", mTickCount);
        bundle.putFloat("TICK_HEIGHT", mTickHeight);
        bundle.putFloat("TICK_WIDTH", mTickWidth);
        bundle.putFloat("BAR_WEIGHT", mBarWeight);
        bundle.putInt("BAR_COLOR", mBarColor);
        bundle.putFloat("CONNECTING_LINE_WEIGHT", mConnectingLineWeight);
        bundle.putInt("CONNECTING_LINE_COLOR", mConnectingLineColor);

        bundle.putInt("THUMB_IMAGE_NORMAL", mThumbImageNormal);
        bundle.putInt("THUMB_IMAGE_PRESSED", mThumbImagePressed);

        bundle.putInt("LEFT_INDEX", mLeftIndex);
        bundle.putInt("RIGHT_INDEX", mRightIndex);

        bundle.putBoolean("FIRST_SET_TICK_COUNT", mFirstSetTickCount);

        return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {

        if (state instanceof Bundle) {

            final Bundle bundle = (Bundle) state;

            mTickCount = bundle.getInt("TICK_COUNT");
            mTickHeight = bundle.getFloat("TICK_HEIGHT");
            mTickWidth = bundle.getFloat("TICK_WIDTH");
            mBarWeight = bundle.getFloat("BAR_WEIGHT");
            mBarColor = bundle.getInt("BAR_COLOR");
            mConnectingLineWeight = bundle.getFloat("CONNECTING_LINE_WEIGHT");
            mConnectingLineColor = bundle.getInt("CONNECTING_LINE_COLOR");

            mThumbImageNormal = bundle.getInt("THUMB_IMAGE_NORMAL");
            mThumbImagePressed = bundle.getInt("THUMB_IMAGE_PRESSED");

            mLeftIndex = bundle.getInt("LEFT_INDEX");
            mRightIndex = bundle.getInt("RIGHT_INDEX");
            mFirstSetTickCount = bundle.getBoolean("FIRST_SET_TICK_COUNT");

            setThumbIndices(mLeftIndex, mRightIndex);

            super.onRestoreInstanceState(bundle.getParcelable("instanceState"));

        } else {

            super.onRestoreInstanceState(state);
        }
    }
    */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int width;
        int height;

        // Get measureSpec mode and size values.
        final int measureWidthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int measureHeightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int measureWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int measureHeight = MeasureSpec.getSize(heightMeasureSpec);

        // The RangeBar width should be as large as possible.
        if (measureWidthMode == MeasureSpec.AT_MOST) {
            width = measureWidth;
        } else if (measureWidthMode == MeasureSpec.EXACTLY) {
            width = measureWidth;
        } else {
            width = mDefaultWidth;
        }

        int mDefaultHeight = calcHeight2();
        
        // The RangeBar height should be as small as possible.
        if (measureHeightMode == MeasureSpec.AT_MOST) {
            height = Math.min(mDefaultHeight, measureHeight);
        } else if (measureHeightMode == MeasureSpec.EXACTLY) {
            height = measureHeight;
        } else {
            height = mDefaultHeight;
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {

        super.onSizeChanged(w, h, oldw, oldh);

        final Context ctx = getContext();

        // This is the initial point at which we know the size of the View.

        int paddingTop = getPaddingTop();
        
        // Create the two thumb objects.
        final float yPos = h / 2f + paddingTop;
        mLeftThumb = new Thumb(ctx, yPos, mThumbDrawableResId);
        mRightThumb = new Thumb(ctx, yPos, mThumbDrawableResId);

        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        
        // Create the underlying bar.
        final float marginLeft = mDrawableWidth / 2f + paddingLeft;
        final float barLength = w - mDrawableWidth - paddingLeft - paddingRight;
        mBar = new Bar(ctx, marginLeft, yPos, barLength, mTickCount, mTickHeight, mTickWidth, mBarWeight, mBarColor);

        // Initialize thumbs to the desired indices
        mLeftThumb.setX(marginLeft + (mLeftIndex / (float) (mTickCount - 1)) * barLength);
        mRightThumb.setX(marginLeft + (mRightIndex / (float) (mTickCount - 1)) * barLength);

        // Set the thumb indices.
        final int newLeftIndex = mBar.getNearestTickIndex(mLeftThumb);
        final int newRightIndex = mBar.getNearestTickIndex(mRightThumb);

        // Call the listener.
        if (newLeftIndex != mLeftIndex || newRightIndex != mRightIndex) {

            mLeftIndex = newLeftIndex;
            mRightIndex = newRightIndex;

            if (mListener != null) {
                mListener.onIndexChangeListener(this, mLeftIndex, mRightIndex);
            }
        }

        // Create the line connecting the two thumbs.
        mConnectingLine = new ConnectingLine(ctx, yPos, mConnectingLineWeight, mConnectingLineColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        mBar.draw(canvas);

        mConnectingLine.draw(canvas, mLeftThumb, mRightThumb);

        mLeftThumb.draw(canvas);
        mRightThumb.draw(canvas);

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // If this View is not enabled, don't allow for touch interactions.
        if (!isEnabled()) {
            return false;
        }

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                onActionDown(event.getX(), event.getY());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                this.getParent().requestDisallowInterceptTouchEvent(false);
                onActionUp();
                return true;

            case MotionEvent.ACTION_MOVE:
                onActionMove(event.getX());
                this.getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            default:
                return false;
        }
    }

    // Public Methods //////////////////////////////////////////////////////////

    /**
     * Sets a listener to receive notifications of changes to the RangeBar. This
     * will overwrite any existing set listeners.
     * 
     * @param listener the RangeBar notification listener; null to remove any
     *            existing listener
     */
    public void setOnRangeBarChangeListener(RangeBar.OnRangeBarChangeListener listener) {
        mListener = listener;
    }

    /**
     * Sets the location of each thumb according to the developer's choice.
     * Numbered from 0 to mTickCount - 1 from the left.
     * 
     * @param leftThumbIndex Integer specifying the index of the left thumb
     * @param rightThumbIndex Integer specifying the index of the right thumb
     */
    public void setThumbIndices(int leftThumbIndex, int rightThumbIndex)
    {
        if (indexOutOfRange(leftThumbIndex, rightThumbIndex))
        {
            Log.e(LOG_TAG, "A thumb index is out of bounds. Check that it is between 0 and mTickCount - 1");
            throw new IllegalArgumentException("A thumb index is out of bounds. Check that it is between 0 and mTickCount - 1");
        }

        else {
            if (mFirstSetTickCount == true)
                mFirstSetTickCount = false;

            mLeftIndex = leftThumbIndex;
            mRightIndex = rightThumbIndex;

            if (mListener != null) {
                mListener.onIndexChangeListener(this, mLeftIndex, mRightIndex);
            }
        }

        invalidate();
        requestLayout();
    }

    /**
     * Gets the index of the left-most thumb.
     * 
     * @return the 0-based index of the left thumb
     */
    public int getLeftIndex() {
        return mLeftIndex;
    }

    /**
     * Gets the index of the right-most thumb.
     * 
     * @return the 0-based index of the right thumb
     */
    public int getRightIndex() {
        return mRightIndex;
    }

    // Private Methods /////////////////////////////////////////////////////////

    /**
     * Does all the functions of the constructor for RangeBar. Called by both
     * RangeBar constructors in lieu of copying the code for each constructor.
     * 
     * @param context Context from the constructor.
     * @param attrs AttributeSet from the constructor.
     * @return none
     */
    private void rangeBarInit(Context context, AttributeSet attrs)
    {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.RangeBar, 0, 0);

        try {
            final DisplayMetrics dm = context.getResources().getDisplayMetrics(); 
            // Sets the values of the user-defined attributes based on the XML
            // attributes.
            final Integer tickCount = ta.getInteger(R.styleable.RangeBar_tickCount, DEFAULT_TICK_COUNT);

            if (isValidTickCount(tickCount)) {

                // Similar functions performed above in setTickCount; make sure
                // you know how they interact
                mTickCount = tickCount;
                mLeftIndex = 0;
                mRightIndex = mTickCount - 1;

                if (mListener != null) {
                    mListener.onIndexChangeListener(this, mLeftIndex, mRightIndex);
                }

            } else {

                Log.e(LOG_TAG, "tickCount less than 2; invalid tickCount. XML input ignored.");
            }

            mTickHeight = ta.getDimension(R.styleable.RangeBar_tickHeight, 
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_TICK_HEIGHT_DP, dm));
            mTickWidth = ta.getDimension(R.styleable.RangeBar_tickWidth, 
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_TICK_WIDTH_DP, dm));
            mBarWeight = ta.getDimension(R.styleable.RangeBar_barWeight,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_BAR_WEIGHT_DP, dm));
            mBarColor = ta.getColor(R.styleable.RangeBar_barColor, DEFAULT_BAR_COLOR);
            mConnectingLineWeight = ta.getDimension(R.styleable.RangeBar_connectingLineWeight,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_CONNECTING_LINE_WEIGHT_DP, dm));
            mConnectingLineColor = ta.getColor(R.styleable.RangeBar_connectingLineColor,
                                               DEFAULT_CONNECTING_LINE_COLOR);
            mThumbDrawableResId = ta.getResourceId(R.styleable.RangeBar_thumbImage,
                R.drawable.seek_thumb_selector);

            if (isInEditMode()){
                mDrawableWidth = mDrawableHeight = 48;
            } else {
                Drawable mThumbDrawable = context.getResources().getDrawable(mThumbDrawableResId);
                mDrawableWidth = mThumbDrawable.getIntrinsicHeight(); 
                mDrawableHeight = mThumbDrawable.getIntrinsicWidth();
            }
        } finally {
            ta.recycle();
        }
    }

    int calcHeight2()
    {
        int mDefaultHeight = (int)Math.max(mConnectingLineWeight, Math.max(mDrawableHeight, mTickHeight + mBarWeight));
        mDefaultHeight += getPaddingTop();
        mDefaultHeight += getPaddingBottom();
        return mDefaultHeight;
    }

    /**
     * Returns if either index is outside the range of the tickCount.
     * 
     * @param leftThumbIndex Integer specifying the left thumb index.
     * @param rightThumbIndex Integer specifying the right thumb index.
     * @return boolean If the index is out of range.
     */
    private boolean indexOutOfRange(int leftThumbIndex, int rightThumbIndex) {
        return (leftThumbIndex < 0 || leftThumbIndex >= mTickCount
                || rightThumbIndex < 0
                || rightThumbIndex >= mTickCount);
    }

    /**
     * If is invalid tickCount, rejects. TickCount must be greater than 1
     * 
     * @param tickCount Integer
     * @return boolean: whether tickCount > 1
     */
    private boolean isValidTickCount(int tickCount) {
        return (tickCount > 1);
    }

    /**
     * Handles a {@link MotionEvent#ACTION_DOWN} event.
     * 
     * @param x the x-coordinate of the down action
     * @param y the y-coordinate of the down action
     */
    private void onActionDown(float x, float y) {

        if (!mLeftThumb.isPressed() && mLeftThumb.isInTargetZone(x, y)) {
            pressThumb(mLeftThumb);
        } else if (!mLeftThumb.isPressed() && mRightThumb.isInTargetZone(x, y)) {
            pressThumb(mRightThumb);
        }
    }

    /**
     * Handles a {@link MotionEvent#ACTION_UP} or
     * {@link MotionEvent#ACTION_CANCEL} event.
     */
    private void onActionUp() {

        if (mLeftThumb.isPressed()) {
            releaseThumb(mLeftThumb);
        } else if (mRightThumb.isPressed()) {
            releaseThumb(mRightThumb);
        }
    }

    /**
     * Handles a {@link MotionEvent#ACTION_MOVE} event.
     * 
     * @param x the x-coordinate of the move event
     */
    private void onActionMove(float x) {

        // Move the pressed thumb to the new x-position.
        if (mLeftThumb.isPressed()) {
            moveThumb(mLeftThumb, x);
        } else if (mRightThumb.isPressed()) {
            moveThumb(mRightThumb, x);
        }

        // If the thumbs have switched order, fix the references.
        if (mLeftThumb.getX() > mRightThumb.getX()) {
            final Thumb temp = mLeftThumb;
            mLeftThumb = mRightThumb;
            mRightThumb = temp;
        }

        // Get the updated nearest tick marks for each thumb.
        final int newLeftIndex = mBar.getNearestTickIndex(mLeftThumb);
        final int newRightIndex = mBar.getNearestTickIndex(mRightThumb);

        // If either of the indices have changed, update and call the listener.
        if (newLeftIndex != mLeftIndex || newRightIndex != mRightIndex) {

            mLeftIndex = newLeftIndex;
            mRightIndex = newRightIndex;

            if (mListener != null) {
                mListener.onIndexChangeListener(this, mLeftIndex, mRightIndex);
            }
        }
    }

    /**
     * Set the thumb to be in the pressed state and calls invalidate() to redraw
     * the canvas to reflect the updated state.
     * 
     * @param thumb the thumb to press
     */
    private void pressThumb(Thumb thumb) {
        if (mFirstSetTickCount == true)
            mFirstSetTickCount = false;
        thumb.press();
        invalidate();
    }

    /**
     * Set the thumb to be in the normal/un-pressed state and calls invalidate()
     * to redraw the canvas to reflect the updated state.
     * 
     * @param thumb the thumb to release
     */
    private void releaseThumb(Thumb thumb) {

        final float nearestTickX = mBar.getNearestTickCoordinate(thumb);
        thumb.setX(nearestTickX);
        thumb.release();
        invalidate();
    }

    /**
     * Moves the thumb to the given x-coordinate.
     * 
     * @param thumb the thumb to move
     * @param x the x-coordinate to move the thumb to
     */
    private void moveThumb(Thumb thumb, float x) {

        // If the user has moved their finger outside the range of the bar,
        // do not move the thumbs past the edge.
        if (x < mBar.getLeftX() || x > mBar.getRightX()) {
            // Do nothing.
        } else {
            thumb.setX(x);
            invalidate();
        }
    }

    // Inner Classes ///////////////////////////////////////////////////////////

    /**
     * A callback that notifies clients when the RangeBar has changed. The
     * listener will only be called when either thumb's index has changed - not
     * for every movement of the thumb.
     */
    public static interface OnRangeBarChangeListener {

        public void onIndexChangeListener(RangeBar rangeBar, int leftThumbIndex, int rightThumbIndex);
    }
}
