package org.bogus.domowygpx.application;

import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.app.Service;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;

public class Application extends android.app.Application
{
    private final static String LOG_TAG = "Application";
    private OKAPI okApi;
    private int notificationIconResid;
    
    public static Application getInstance(Service context)
    {
        return (Application)(context.getApplication());
    }

    public static Application getInstance(Activity context)
    {
        return (Application)(context.getApplication());
    }
    
    public OKAPI getOkApi()
    {
        return okApi;
    }
    
    @Override
    public void onCreate()
    {
        super.onCreate();
        
        okApi = new OKAPI(this);
        int sblv = getStatusBarBackgroundLightValue();
        Log.d(LOG_TAG, "StatusBarBackgroundLightValue: " + sblv);
        notificationIconResid = sblv < 128 ? R.drawable.ic_logo_czyste_biale : R.drawable.ic_logo_czyste_czarne;
        /* SENSELESS :/
        SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
        if (config.getBoolean("Application_restartDownloadOnApplicationStart", true)){
            Looper mainLooper = Looper.myLooper();
            Handler handler = new Handler(mainLooper);
            handler.postAtTime(new Runnable(){
                @Override
                public void run(){
                    // restart downloads (i.e. after system reboot)
                    // TODO: in case of an error, this may cause application crach
                    // that would lead to infinite loop of 
                    //  * user starts application -> application crach -> application stopped
                    // then user will uninstall our application :(((
                    final Intent intent = new Intent(FilesDownloaderService.INTENT_ACTION_START_DOWNLOAD, null, 
                        Application.this, FilesDownloaderService.class);
                    startService(intent);
                }
            }, SystemClock.uptimeMillis() + 15L*1000L);
        }*/
        /*
        locus.api.utils.Logger.registerLogger(new locus.api.utils.Logger.ILogger(){

            @Override
            public void logI(String tag, String msg)
            {
                android.util.Log.i(tag, msg);
            }

            @Override
            public void logD(String tag, String msg)
            {
                android.util.Log.d(tag, msg);
            }

            @Override
            public void logW(String tag, String msg)
            {
                android.util.Log.w(tag, msg);
            }

            @Override
            public void logE(String tag, String msg, Exception e)
            {
                android.util.Log.e(tag, msg, e);
            }});
        */    
    }
    
    @Override
    public void onTerminate()
    {
        okApi = null;
        super.onTerminate();
    }
    
    /**
     * Returns estimated value of the lightness of the status bar
     * @return
     */
    private int getStatusBarBackgroundLightValue()
    {
        // better this than nothing
        Drawable bg = getResources().getDrawable(android.R.drawable.status_bar_item_background);
        int height = Math.max(1, bg.getIntrinsicHeight());
        int width = Math.max(1, bg.getIntrinsicWidth());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        bg.setBounds(0, 0, width, height);
        bg.draw(canvas);
        
        long sum = 0;
        for (int x=0; x<width; x++){
            for (int y=0; y<height; y++){
                int color = bitmap.getPixel(x, y);
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = (color) & 0xFF;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int l = (min + max)/2;
                sum = sum + l;
            }
        }
        bitmap.recycle();
        bitmap = null;
        canvas = null;
        bg = null;
        sum = sum / (width * height);
        // should be [0..255]
        return (int)Math.min(255, Math.max(sum, 0));
    }

    public int getNotificationIconResid()
    {
        return notificationIconResid;
    }
}
