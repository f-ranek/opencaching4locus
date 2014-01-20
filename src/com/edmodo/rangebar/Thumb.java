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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
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

    // The normal and pressed images to display for the thumbs.
    private final Bitmap mImageNormal;
    private final Bitmap mImagePressed;

    // Variables to store half the width/height for easier calculation.
    private final float mHalfWidthNormal;
    private final float mHalfHeightNormal;

    private final float mHalfWidthPressed;
    private final float mHalfHeightPressed;

    // Indicates whether this thumb is currently pressed and active.
    private boolean mIsPressed = false;

    // The y-position of the thumb in the parent view. This should not change.
    private final float mY;

    // The current x-position of the thumb in the parent view.
    private float mX;

    // Constructors ////////////////////////////////////////////////////////////

    Thumb(Context ctx,
          float y,
          int thumbImageNormal,
          int thumbImagePressed) {

        final Resources res = ctx.getResources();

        mImageNormal = BitmapFactory.decodeResource(res, thumbImageNormal);
        mImagePressed = BitmapFactory.decodeResource(res, thumbImagePressed);

        mHalfWidthNormal = mImageNormal.getWidth() / 2f;
        mHalfHeightNormal = mImageNormal.getHeight() / 2f;

        mHalfWidthPressed = mImagePressed.getWidth() / 2f;
        mHalfHeightPressed = mImagePressed.getHeight() / 2f;

        // Sets the minimum touchable area, but allows it to expand based on
        // image size

        int normalDrawableSize = Math.max(mImageNormal.getWidth(), mImageNormal.getHeight());
        mTargetRadiusPx = Math.max(normalDrawableSize, 
                TypedValue.complexToDimensionPixelSize(MINIMUM_TARGET_RADIUS_DP,
                    res.getDisplayMetrics()));

        mX = mHalfWidthNormal;
        mY = y;
    }

    // Package-Private Methods /////////////////////////////////////////////////

    float getHalfWidth() {
        return mHalfWidthNormal;
    }

    float getHalfHeight() {
        return mHalfHeightNormal;
    }

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

        // should use drawable, and drawable states!
        final Bitmap bitmap = (mIsPressed) ? mImagePressed : mImageNormal;

        if (mIsPressed) {
            final float topPressed = mY - mHalfHeightPressed;
            final float leftPressed = mX - mHalfWidthPressed;
            canvas.drawBitmap(bitmap, leftPressed, topPressed, null);
        } else {
            final float topNormal = mY - mHalfHeightNormal;
            final float leftNormal = mX - mHalfWidthNormal;
            canvas.drawBitmap(bitmap, leftNormal, topNormal, null);
        }
    }
}
