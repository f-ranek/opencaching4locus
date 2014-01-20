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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

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
          float y,
          int thumbDrawableResId) {

        final Resources res = ctx.getResources();

        mDrawable = res.getDrawable(thumbDrawableResId);

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
