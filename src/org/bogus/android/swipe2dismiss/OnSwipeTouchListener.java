package org.bogus.android.swipe2dismiss;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

/**
 * http://stackoverflow.com/a/16563337
 * @author Boguś
 *
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class OnSwipeTouchListener implements OnTouchListener
{
    private final GestureDetector gestureDetector;
    protected View view;

    public OnSwipeTouchListener(Context ctx)
    {
        gestureDetector = new GestureDetector(ctx, new GestureListener());
    }
    
    @Override
    public boolean onTouch(final View view, final MotionEvent motionEvent)
    {
        this.view = view;
        // super.onTouch(view, motionEvent);
        return gestureDetector.onTouchEvent(motionEvent);

    }

    final class GestureListener extends SimpleOnGestureListener
    {

        private static final int SWIPE_THRESHOLD = 100;

        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {

            boolean result = false;
            float diffY = e2.getY() - e1.getY();
            float diffX = e2.getX() - e1.getX();
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        onSwipeRight();
                    } else {
                        onSwipeLeft();
                    }
                }
            } else {
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        onSwipeBottom();
                    } else {
                        onSwipeTop();
                    }
                }
            }
            return result;
        }
    }

    public void onSwipeRight()
    {
    }

    public void onSwipeLeft()
    {
    }

    public void onSwipeTop()
    {
    }

    public void onSwipeBottom()
    {
    }
}
