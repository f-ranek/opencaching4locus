package org.bogus.android;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

/**
 * A {@link ScrollView} that can block invocations of 
 * {@link ScrollView#requestChildRectangleOnScreen(View, Rect, boolean) requestChildRectangleOnScreen}  
 * @author Boguś
 *
 */
public class LockableScrollView extends ScrollView
{
    private boolean childRequestsLocked;
    
    public LockableScrollView(Context context)
    {
        super(context);
    }

    public LockableScrollView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public LockableScrollView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate)
    {
        if (childRequestsLocked){
            return false;
        } else {
            return super.requestChildRectangleOnScreen(child, rectangle, immediate);
        }
    }

    public boolean isChildRequestsLocked()
    {
        return childRequestsLocked;
    }

    public void setChildRequestsLocked(boolean childRequestsLocked)
    {
        this.childRequestsLocked = childRequestsLocked;
    }

}
