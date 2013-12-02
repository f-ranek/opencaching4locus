package org.bogus.domowygpx.activities;

import org.bogus.android.AndroidUtils;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.View;
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
    public void onCreate(final View owner)
    {
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
            
            textViewDownloadImages.setText(lblResId);
            checkBoxDownloadImages.setChecked(state);
        }finally{
            preventEvents = false;
        }
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
