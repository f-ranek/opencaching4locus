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

package org.bogus.android;

import org.bogus.geocaching.egpx.R;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;

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
 * <p>
 * Based on https://github.com/edmodo/range-bar
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
    private static final float DEFAULT_LABEL_TOP_SPACE_DP = 2;

    // Corresponds to android.R.color.holo_blue_light.
    private static final int DEFAULT_CONNECTING_LINE_COLOR = 0xff33b5e5;

    /**
     * Represents a thumb in the RangeBar slider. This is the handle for the slider
     * that is pressed and slid.
     */
    class Thumb {

        // Private Constants ///////////////////////////////////////////////////////

        // The radius (in dp) of the touchable area around the thumb. We are basing
        // this value off of the recommended 48dp Rhythm. See:
        // http://developer.android.com/design/style/metrics-grids.html#48dp-rhythm
        private static final int MINIMUM_TARGET_RADIUS_DP = 24;

        // Member Variables ////////////////////////////////////////////////////////

        // Radius (in pixels) of the touch area of the thumb.
        private final float mTargetRadiusPx;

        // The images to display for the thumbs.
        private final Drawable mDrawable;

        // Indicates whether this thumb is currently pressed and active.
        private boolean mIsPressed = false;

        // The y-position of the thumb in the parent view. This should not change.
        private final float mY;

        // The current x-position of the thumb in the parent view.
        private float mX;

        // Constructors ////////////////////////////////////////////////////////////

        Thumb(Context ctx,
              float y) {

            final Resources res = ctx.getResources();

            mDrawable = res.getDrawable(mThumbDrawableResId);

            float mHalfWidthNormal = mDrawable.getIntrinsicWidth() / 2f;

            // Sets the minimum touchable area, but allows it to expand based on
            // image size

            int normalDrawableSize = Math.max(mDrawable.getIntrinsicWidth(), mDrawable.getIntrinsicHeight());
            mTargetRadiusPx = Math.max(normalDrawableSize, 
                    TypedValue.complexToDimensionPixelSize(MINIMUM_TARGET_RADIUS_DP,
                        res.getDisplayMetrics()));

            mX = mHalfWidthNormal;
            mY = y;
        }

        // Package-Private Methods /////////////////////////////////////////////////

        void setX(float x) {
            mX = x;
        }

        float getX() {
            return mX;
        }

        boolean isPressed() {
            return mIsPressed;
        }

        void press() {
            mIsPressed = true;
        }

        void release() {
            mIsPressed = false;
        }

        /**
         * Determines if the input coordinate is close enough to this thumb to
         * consider it a press.
         * 
         * @param x the x-coordinate of the user touch
         * @param y the y-coordinate of the user touch
         * @return true if the coordinates are within this thumb's target area;
         *         false otherwise
         */
        boolean isInTargetZone(float x, float y) {

            if (Math.abs(x - mX) <= mTargetRadiusPx && Math.abs(y - mY) <= mTargetRadiusPx) {
                return true;
            }
            return false;
        }

        /**
         * Draws this thumb on the provided canvas.
         * 
         * @param canvas Canvas to draw on; should be the Canvas passed into {#link
         *            View#onDraw()}
         */
        void draw(Canvas canvas) {

            if (mDrawable.isStateful()){
                mDrawable.setState(new int[]{mIsPressed ? android.R.attr.state_pressed : 0});
            }
            final int topPressed = (int)(mY - mDrawable.getIntrinsicHeight()/2f);
            final int leftPressed = (int)(mX - mDrawable.getIntrinsicWidth()/2f);
            mDrawable.setBounds(leftPressed, topPressed,
                leftPressed+mDrawable.getIntrinsicWidth(),
                topPressed+mDrawable.getIntrinsicHeight());
            mDrawable.draw(canvas);
        }
    }

    // Instance variables for all of the customizable attributes
    private int mTickCount;
    private float mTickHeight, mTickWidth;
    private int mSecondaryTickCount;
    private float mSecondaryTickHeight, mSecondaryTickWidth;
    private float mBarWeight;
    private int mBarColor;
    private float mConnectingLineWeight;
    private int mConnectingLineColor;
    int mThumbDrawableResId;

    private int mDefaultWidth = 500;

    private Thumb mLeftThumb;
    private Thumb mRightThumb;

    private RangeBar.OnRangeBarChangeListener mListener;
    private int mLeftIndex;
    private int mRightIndex;

    private int mDrawableWidth, mDrawableHeight;
    
    // Member Variables ////////////////////////////////////////////////////////

    private Paint mBarPaint;
    private Paint mBarTickPaint;
    private Paint mBarSecondaryTickPaint;
    private Paint mLabelPaint;
    private Paint mLinePaint;

    // Left-coordinate of the horizontal bar.
    private float mBarLeftX;
    private float mBarRightX;
    private float mBarY;

    private String[] mLabels;
    private int mLabelsHeight = -1;
    private float mLabelsTopSpace;

    private ColorStateList mTextColor;
    private int mTextSize = 15;
    private String mFontFamily;
    private int mTypefaceIndex = -1;
    private int mFontStyleIndex = Typeface.NORMAL;
    
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

    protected void createPaints()
    {
        // Initialize the paint.
        mBarPaint = new Paint();
        mBarPaint.setColor(mBarColor);
        mBarPaint.setStrokeWidth(mBarWeight);
        mBarPaint.setAntiAlias(true);
        
        if (mTickWidth > 0){
            mBarTickPaint = new Paint();
            mBarTickPaint.setColor(mBarColor);
            mBarTickPaint.setStrokeWidth(mTickWidth);
            mBarTickPaint.setAntiAlias(true);
        } else {
            mBarTickPaint = null;
        }
        if (mSecondaryTickWidth > 0){
            mBarSecondaryTickPaint = new Paint();
            mBarSecondaryTickPaint.setColor(mBarColor);
            mBarSecondaryTickPaint.setStrokeWidth(mSecondaryTickWidth);
            mBarSecondaryTickPaint.setAntiAlias(true);
        } else {
            mBarSecondaryTickPaint = null;
        }
        
        mLinePaint = new Paint();
        mLinePaint.setColor(mConnectingLineColor);
        mLinePaint.setStrokeWidth(mConnectingLineWeight);
        mLinePaint.setAntiAlias(true);
        
        if (mLabels != null){
            mLabelPaint = new Paint();
            mLabelPaint.setColor(mTextColor.getDefaultColor());
            mLabelPaint.setTextAlign(Align.CENTER);
            mLabelPaint.setTextSize(mTextSize);
            // typeface
            Typeface tf = null;
            if (mFontFamily != null) {
                tf = Typeface.create(mFontFamily, mFontStyleIndex);
            }
            if (tf == null){
                switch (mTypefaceIndex) {
                    case 1: // sans
                        tf = Typeface.SANS_SERIF;
                        break;
    
                    case 2: // serif
                        tf = Typeface.SERIF;
                        break;
    
                    case 3: // monospace
                        tf = Typeface.MONOSPACE;
                        break;
                }
                if (mFontStyleIndex >= 0) {
                    if (tf == null) {
                        tf = Typeface.defaultFromStyle(mFontStyleIndex);
                    } else {
                        tf = Typeface.create(tf, mFontStyleIndex);
                    }
                }
            }
            if (tf != null) {
                mLabelPaint.setTypeface(tf);
            }
            mLabelPaint.setAntiAlias(true);
        } else {
            mLabelPaint = null;
        }
        
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {

        super.onSizeChanged(w, h, oldw, oldh);

        final Context ctx = getContext();

        // This is the initial point at which we know the size of the View.

        int paddingTop = getPaddingTop();
        
        final float yPos = Math.max(mConnectingLineWeight, 
            Math.max(mDrawableHeight, Math.max(mTickHeight, mSecondaryTickHeight) + mBarWeight)) / 2f + paddingTop;
        // Create the two thumb objects.
        mLeftThumb = new Thumb(ctx, yPos);
        mRightThumb = new Thumb(ctx, yPos);

        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        
        // Create the underlying bar.
        final float marginLeft = mDrawableWidth / 2f + paddingLeft;
        final float barLength = w - mDrawableWidth - paddingLeft - paddingRight;

        mBarLeftX = marginLeft;
        mBarRightX = marginLeft + barLength;
        mBarY = yPos;
        
        // Initialize thumbs to the desired indices
        float mTickDistance = (mBarRightX-mBarLeftX) / (getTotalTickCount() - 1);
        mLeftThumb.setX(mBarLeftX + (mLeftIndex * mTickDistance));
        mRightThumb.setX(mBarLeftX + (mRightIndex * mTickDistance));
        
    }

    /**
     * Gets the x-coordinate of the nearest tick to the given x-coordinate.
     * 
     * @param x the x-coordinate to find the nearest tick for
     * @return the x-coordinate of the nearest tick
     */
    float getNearestTickCoordinate(Thumb thumb) {
        final int nearestTickIndex = getNearestTickIndex(thumb);
        float mTickDistance = (mBarRightX-mBarLeftX) / (getTotalTickCount()-1);
        final float nearestTickCoordinate = mBarLeftX + (nearestTickIndex * mTickDistance);
        return nearestTickCoordinate;
    }

    /**
     * Gets the zero-based index of the nearest tick to the given thumb.
     * 
     * @param thumb the Thumb to find the nearest tick for
     * @return the zero-based index of the nearest tick
     */
    int getNearestTickIndex(Thumb thumb) {
        float mTickDistance = (mBarRightX-mBarLeftX) / (getTotalTickCount()-1);
        final int nearestTickIndex = (int) ((thumb.getX() - mBarLeftX + mTickDistance / 2f) / mTickDistance);
        return nearestTickIndex;
    }
    
    protected  void drawBar(Canvas canvas) {

        final int totalTickCount = getTotalTickCount();
        final float barTickHeight = mTickHeight + mBarWeight;
        final float secondaryBarTickHeight = mSecondaryTickHeight + mBarWeight;
        final float barTickStartY = mBarY - barTickHeight / 2f;
        final float barTickEndY = mBarY + barTickHeight / 2f;
        final float barSecondaryTickStartY = mBarY - secondaryBarTickHeight / 2f;
        final float barSecondaryTickEndY = mBarY + secondaryBarTickHeight / 2f;
        final float mickDistance = (mBarRightX-mBarLeftX) / (totalTickCount-1);
        final float barLabelStartY = mLabelsHeight + Math.max(barTickEndY, barSecondaryTickEndY) + mLabelsTopSpace;

        canvas.drawLine(mBarLeftX, mBarY, mBarRightX, mBarY, mBarPaint);
    
        // Loop through and draw each tick (except final tick).
        for (int i = 0; i < totalTickCount-1; i++) {
            final float x = i * mickDistance + mBarLeftX;
            final Paint barPaint;
            final float barStartY, barEndY;
            if (mSecondaryTickCount == 0 || (i%(mSecondaryTickCount+1)) == 0){
                barPaint = mBarTickPaint;
                barStartY = barTickStartY;
                barEndY = barTickEndY;
            } else {
                barPaint = mBarSecondaryTickPaint;
                barStartY = barSecondaryTickStartY;
                barEndY = barSecondaryTickEndY;
            }
            if (barPaint != null){
                canvas.drawLine(x, barStartY, x, barEndY, barPaint);
            }
            if (mLabels != null && mLabels[i] != null){
                canvas.drawText(mLabels[i], x, barLabelStartY, mLabelPaint);
            }
        }
        // Draw final tick. We draw the final tick outside the loop to avoid any
        // rounding discrepancies.
        if (mBarTickPaint != null){
            canvas.drawLine(mBarRightX, barTickStartY, mBarRightX, barTickEndY, mBarTickPaint);
        }
        if (mLabels != null && mLabels[totalTickCount-1] != null){
            canvas.drawText(mLabels[totalTickCount-1], mBarRightX, barLabelStartY, mLabelPaint);
        }
    }
    
    protected void drawLine(Canvas canvas) {
        canvas.drawLine(mLeftThumb.getX(), mBarY, mRightThumb.getX(), mBarY, mLinePaint);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        if (mBarPaint == null){
            createPaints();
        }
        
        drawBar(canvas);
        drawLine(canvas);

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
        if (indexOutOfRange(leftThumbIndex, rightThumbIndex)){
            throw new IllegalArgumentException("A thumb index is out of bounds. Check that it is between 0 and mTickCount - 1");
        } else {
            mLeftIndex = leftThumbIndex;
            mRightIndex = rightThumbIndex;
            
            if (mLeftThumb != null && mRightThumb != null){
                // Initialize thumbs to the desired indices
                float mTickDistance = (mBarRightX-mBarLeftX) / (mTickCount - 1);
                mLeftThumb.setX(mBarLeftX + (mLeftIndex * mTickDistance));
                mRightThumb.setX(mBarLeftX + (mRightIndex * mTickDistance));
    
                // Set the thumb indices.
                final int newLeftIndex = getNearestTickIndex(mLeftThumb);
                final int newRightIndex = getNearestTickIndex(mRightThumb);

                // Call the listener.
                if (newLeftIndex != mLeftIndex || newRightIndex != mRightIndex) {
    
                    mLeftIndex = newLeftIndex;
                    mRightIndex = newRightIndex;
    
                    if (mListener != null) {
                        mListener.onIndexChangeListener(this, mLeftIndex, mRightIndex);
                    }
                }
            }
        }

        invalidate();
        // requestLayout();
    }

    /**
     * Gets the index of the left-most thumb.
     * 
     * @return the 0-based index of the left thumb
     */
    @ExportedProperty
    public int getLeftIndex() {
        return mLeftIndex;
    }

    public void setLeftIndex(int leftIndex)
    {
        if (leftIndex == mLeftIndex){
            return ;
        }
        setThumbIndices(leftIndex, mRightIndex);
    }
    /**
     * Gets the index of the right-most thumb.
     * 
     * @return the 0-based index of the right thumb
     */
    @ExportedProperty
    public int getRightIndex() {
        return mRightIndex;
    }

    public void setRightIndex(int rightIndex)
    {
        if (rightIndex == mRightIndex){
            return ;
        }
        setThumbIndices(mLeftIndex, rightIndex);
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
            final int tickCount = ta.getInteger(R.styleable.RangeBar_tickCount, DEFAULT_TICK_COUNT);
            if (isValidTickCount(tickCount)) {
                // Similar functions performed above in setTickCount; make sure
                // you know how they interact
                mTickCount = tickCount;
            } else {
                Log.e(LOG_TAG, "tickCount less than 2; invalid tickCount. XML input ignored.");
                mTickCount = 2;
            }
            final int secondaryTickCount = ta.getInteger(R.styleable.RangeBar_secondaryTickCount, 0);

            if (isValidSecondaryTickCount(secondaryTickCount)) {
                mSecondaryTickCount = secondaryTickCount;
            } else {
                Log.e(LOG_TAG, "secondaryTickCount less than 0; invalid tickCount. XML input ignored.");
                mSecondaryTickCount = 0;
            }

            mTickHeight = ta.getDimension(R.styleable.RangeBar_tickHeight, 
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_TICK_HEIGHT_DP, dm));
            mTickWidth = ta.getDimension(R.styleable.RangeBar_tickWidth, 
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_TICK_WIDTH_DP, dm));
            mSecondaryTickHeight = ta.getDimension(R.styleable.RangeBar_secondaryTickHeight, 
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_TICK_HEIGHT_DP / 2f, dm));
            mSecondaryTickWidth = ta.getDimension(R.styleable.RangeBar_secondaryTickWidth, 
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
            mLabelsTopSpace = ta.getDimension(R.styleable.RangeBar_labelsTopSpace,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_LABEL_TOP_SPACE_DP, dm));

            mLeftIndex = ta.getResourceId(R.styleable.RangeBar_leftIndex, 0);
            mRightIndex = ta.getResourceId(R.styleable.RangeBar_rightIndex, mTickCount-1);
            if (indexOutOfRange(mLeftIndex, 0)){
                mLeftIndex = 0;
            }
            if (indexOutOfRange(0, mRightIndex)){
                mRightIndex = mTickCount-1;
            }
            
            Drawable mThumbDrawable = context.getResources().getDrawable(mThumbDrawableResId);
            mDrawableWidth = mThumbDrawable.getIntrinsicHeight(); 
            mDrawableHeight = mThumbDrawable.getIntrinsicWidth();
            
            int labelsResId = ta.getResourceId(R.styleable.RangeBar_labels, -1);
            if (labelsResId > 0){
                mLabels = context.getResources().getStringArray(labelsResId);
            }
            
            TypedArray appearance = null;
            int ap = ta.getResourceId(R.styleable.RangeBar_textAppearance, -1);
            if (ap != -1) {
                appearance = context.getTheme().obtainStyledAttributes(ap, TextAppearanceConsts.styleable_TextAppearance);
                int n = appearance.getIndexCount();
                for (int i = 0; i < n; i++) {
                    int attr = appearance.getIndex(i);

                    if (attr == TextAppearanceConsts.styleable_TextAppearance_textColor){
                        mTextColor = appearance.getColorStateList(attr);
                    } else if (attr == TextAppearanceConsts.styleable_TextAppearance_textSize){
                        mTextSize = appearance.getDimensionPixelSize(attr, mTextSize);
                    } else if (attr == TextAppearanceConsts.styleable_TextAppearance_typeface){
                        mTypefaceIndex = appearance.getInt(attr, -1);
                    } else if (attr == TextAppearanceConsts.styleable_TextAppearance_fontFamily){
                        mFontFamily = appearance.getString(attr);
                    } else if (attr == TextAppearanceConsts.styleable_TextAppearance_textStyle){
                        mFontStyleIndex = appearance.getInt(attr, -1);
                    }
                }
                appearance.recycle();
            }
        } finally {
            ta.recycle();
        }
    }

    int calcHeight2()
    {
        if (mLabels != null && mLabelPaint == null){
            createPaints();
        }
        if (mLabels != null && mLabelsHeight == -1){
            Rect bounds = new Rect();
            mLabelsHeight = 0;
            for (String text : mLabels){
                if (text == null){
                    continue;
                }
                mLabelPaint.getTextBounds(text, 0, text.length(), bounds);
                int height = bounds.height();
                if (height > mLabelsHeight){
                    mLabelsHeight = height;
                }
            }
        }
        
        
        float mDefaultHeight = Math.max(mConnectingLineWeight, 
            Math.max(mDrawableHeight, Math.max(mTickHeight, mSecondaryTickHeight) + mBarWeight));
        mDefaultHeight += getPaddingTop();
        mDefaultHeight += getPaddingBottom();
        mDefaultHeight += mLabelsHeight;
        mDefaultHeight += mLabelsTopSpace;
        return (int)mDefaultHeight;
    }

    public void setTickCount(int tickCount) {
        if (mTickCount == tickCount){
            return ;
        }
        if (!isValidTickCount(tickCount)){
            throw new IllegalArgumentException("Invalid tickCount=" + tickCount);
        }
        mTickCount = tickCount;
        invalidate();
    }
    
    @ExportedProperty
    public int getTickCount()
    {
        return mTickCount;
    }

    public void setSecondaryTickCount(int tickCount) {
        if (mSecondaryTickCount == tickCount){
            return ;
        }
        if (!isValidSecondaryTickCount(tickCount)){
            throw new IllegalArgumentException("Invalid secondaryTickCount=" + tickCount);
        }
        mSecondaryTickCount = tickCount;
        invalidate();
    }
    
    @ExportedProperty
    public int getSecondaryTickCount()
    {
        return mSecondaryTickCount;
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
     * If is invalid tickCount, rejects. TickCount must be greater than 1
     * 
     * @param tickCount Integer
     * @return boolean: whether tickCount >= 0
     */
    private boolean isValidSecondaryTickCount(int tickCount) {
        return (tickCount >= 0);
    }

    protected final int getTotalTickCount()
    {
        return mTickCount * (mSecondaryTickCount + 1) - mSecondaryTickCount; 
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
        final int newLeftIndex = getNearestTickIndex(mLeftThumb);
        final int newRightIndex = getNearestTickIndex(mRightThumb);

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

        final float nearestTickX = getNearestTickCoordinate(thumb);
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
        if (x < mBarLeftX || x > mBarRightX) {
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
}
