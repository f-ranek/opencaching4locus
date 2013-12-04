package org.bogus.domowygpx.activities;

import java.util.Queue;

import org.bogus.android.AndroidUtils;
import org.bogus.android.LockableScrollView;
import org.bogus.android.SimpleQueue;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;
import android.os.MessageQueue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

public class DownloadImagesFragment
{
    private ConnectivityManager conectivityManager;

    String currentDownloadImagesStrategy;
    CheckBox checkBoxDownloadImages;
    private TextView textViewDownloadImages;
    
    boolean preventEvents;

    Window window;
    ViewGroup view;
    
    private LockableScrollView lockableScrollViewCache;
    private boolean lockableScrollViewCached;
    
    public void onCreate(final ViewGroup owner)
    {
        this.view = owner;
        conectivityManager = (ConnectivityManager)owner.getContext().getSystemService(
            Activity.CONNECTIVITY_SERVICE);

        checkBoxDownloadImages = (CheckBox) owner.findViewById(R.id.checkBoxDownloadImages);
        checkBoxDownloadImages.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (preventEvents){
                    return ;
                }
                if (isChecked){
                    currentDownloadImagesStrategy = TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ALWAYS;
                } else {
                    currentDownloadImagesStrategy = TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_NEVER;
                }
                updateDownloadImagesState();
                AndroidUtils.hideSoftKeyboard(window);
            }
        });
        textViewDownloadImages = (TextView) owner.findViewById(R.id.textViewDownloadImages);
        textViewDownloadImages.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (preventEvents){
                    return ;
                }
                if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI.equals(currentDownloadImagesStrategy)){
                    currentDownloadImagesStrategy = TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ALWAYS;
                } else
                if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_NEVER.equals(currentDownloadImagesStrategy)){
                    currentDownloadImagesStrategy = TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI;
                } else 
                if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ALWAYS.equals(currentDownloadImagesStrategy)){
                    currentDownloadImagesStrategy = TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_NEVER;
                } else {
                    throw new IllegalStateException();
                }
                updateDownloadImagesState();
                AndroidUtils.hideSoftKeyboard(window);
            }
        });
    }

    void updateDownloadImagesState()
    {
        preventEvents = true;
        try{
            int lblResId;
            boolean state;
            if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ON_WIFI.equals(currentDownloadImagesStrategy)){
                NetworkInfo wifiInfo = conectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                state = wifiInfo.isConnected();        
                lblResId = R.string.downloadImages_wifi;
            } else
            if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_NEVER.equals(currentDownloadImagesStrategy)){
                state = false;
                lblResId = R.string.downloadImages_never;
            } else 
            if (TaskConfiguration.DOWNLOAD_IMAGES_STRATEGY_ALWAYS.equals(currentDownloadImagesStrategy)){
                state = true;
                lblResId = R.string.downloadImages_always;
            } else {
                throw new IllegalStateException();
            }
            
            final LockableScrollView sv = getLockableScrollView();
            if (sv != null && !sv.isChildRequestsLocked()){
                sv.setChildRequestsLocked(true);
                Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler()
                {
                    // unlock scroll view when all the layout events are done
                    @Override
                    public boolean queueIdle()
                    {
                        sv.setChildRequestsLocked(false);
                        return false;
                    }
                });
            }

            // The text change will trigger layout request, which in turn will trigger
            // scroll request to the view currently owning focus. We prevent this
            // by installing our ScrollView implementation, that can suppress scrolling
            // children to be on-screen
            textViewDownloadImages.setText(lblResId);
            checkBoxDownloadImages.setChecked(state);
        }finally{
            preventEvents = false;
        }
    }

    
    private LockableScrollView getLockableScrollView()
    {
        // assume we have no severe tree rebuild 
        if (lockableScrollViewCached){
            return lockableScrollViewCache;
        } else {
            lockableScrollViewCached = true;
            return lockableScrollViewCache = findLockableScrollView();
        }
    }
    
    /**
     * Performs breadth-first search over the view tree,
     * and returns first {@link LockableScrollView} found
     * @return
     */
    private LockableScrollView findLockableScrollView()
    {
        ViewGroup view = this.view;
        if (view instanceof LockableScrollView){
            return (LockableScrollView)view;
        }
        
        LockableScrollView result = null;
        final Queue<ViewGroup> queue = SimpleQueue.getInstance();
        do{
            if (view instanceof LockableScrollView){
                result = (LockableScrollView)view;
                break;
            }
            final int childCount = view.getChildCount();
            for (int i=0; i<childCount; i++){
                final View childView = view.getChildAt(i);
                if (childView instanceof ViewGroup){
                    queue.add((ViewGroup)childView);
                }
            }
        }while((view = queue.poll()) != null);
        return result;
    }

    public String getCurrentDownloadImagesStrategy()
    {
        return currentDownloadImagesStrategy;
    }

    public void setCurrentDownloadImagesStrategy(String currentDownloadImagesStrategy)
    {
        if (!currentDownloadImagesStrategy.equals(this.currentDownloadImagesStrategy)){
            this.currentDownloadImagesStrategy = currentDownloadImagesStrategy;
            updateDownloadImagesState();
        }
    }

    public Window getWindow()
    {
        return window;
    }

    public void setWindow(Window window)
    {
        this.window = window;
    }

}
