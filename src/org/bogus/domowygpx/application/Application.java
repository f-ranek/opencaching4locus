package org.bogus.domowygpx.application;

import java.io.File;
import java.util.List;

import org.bogus.domowygpx.utils.TargetDirLocator;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.app.Service;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.Log;

public class Application extends android.app.Application
{
    private final static String LOG_TAG = "Application";
    private volatile OKAPI okApi;
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
        if (okApi == null){
            synchronized(this){
                if (okApi == null){
                    okApi = new OKAPI(this);
                }
            }
        }
        return okApi;
    }
    
    @Override
    public void onCreate()
    {
        Log.i(LOG_TAG, "Called onCreate");
        super.onCreate();
        
        setupPreferences();
        
        // TODO: cleanup gpxTargetDirNameTemp
        
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
        if (notificationIconResid == 0){
            int sblv = getStatusBarBackgroundLightValue();
            notificationIconResid = sblv < 128 ? R.drawable.ic_logo_czyste_biale : R.drawable.ic_logo_czyste_czarne;
        }
        return notificationIconResid;
    }
    
    private void setupPreferences()
    {
        SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
        try{
            int version = config.getInt("configVersion", -1);
            if (version <= 0){
                initNewConfig();
                return ;
            }
            Editor editor = config.edit();
            switch(version){
                case 1: 
                    updatePreferences(config, editor, 1);
                    editor.putInt("configVersion", 2);
                    editor.commit();
                    
                    createDirectories(config, "gpxTargetDirName");
                    createDirectories(config, "gpxTargetDirNameTemp");
                    createDirectories(config, "imagesTargetDirName");
                    
                    break;
                case 2: 
                    break;
                default:
                    Log.w(LOG_TAG, "Newer preferences found: " + version);
                    initNewConfig();
            }
            
            // TODO: sprawdzić, czy docelowy katalog istnieje, i jak nie
            // to albo spróbować utworzyć, albo wybrać inny katalog
            
        }catch(Exception e){
            Log.e(LOG_TAG, "Failed to read config", e);
            initNewConfig();
        }
    }
    
    private void initNewConfig()
    {
        SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
        Editor editor = config.edit();
        editor.clear();
        
        initSaveDirectories(config, editor);
        
        editor.putInt("configVersion", 2);
        editor.commit();    
    }
    
    private void initSaveDirectories(SharedPreferences config, Editor editor)
    {
        final TargetDirLocator tdl = new TargetDirLocator(this);
        final List<File> locus = tdl.locateLocus();
        File gpxTargetDirName = null;
        File gpxTargetDirNameTemp = null;
        File imagesTargetDirName = null;
        if (locus != null && !locus.isEmpty()){
            File loc = locus.get(0); 
            gpxTargetDirName = new File(loc, "mapItems");
            gpxTargetDirNameTemp = new File (loc, "mapItems-temp");
            imagesTargetDirName = new File(loc, ".cacheImages");
        } else {
            final List<File> data = tdl.locateSaveDirectories();
            File dir;
            if (data == null || data.isEmpty()){
                dir = data.get(0);
            } else {
                dir = Environment.getDataDirectory();
            }
            gpxTargetDirName = new File(dir, "kesze"); // XXX localization!!!
            gpxTargetDirNameTemp = new File (dir, "kesze-temp");
            imagesTargetDirName = new File(dir, ".cacheImages");
        }
        
        editor.putString("gpxTargetDirName", gpxTargetDirName.toString());
        editor.putString("gpxTargetDirNameTemp", gpxTargetDirNameTemp.toString()); 
        editor.putString("imagesTargetDirName", imagesTargetDirName.toString());
        
    }
    
    private void createDirectories(SharedPreferences config, String key)
    {
        String val = config.getString(key, null);
        if (val != null){
            File f = new File(val);
            f.mkdirs();
        }
    }
    
    private void updatePreferences(SharedPreferences config, Editor editor, int fromVersion)
    {
        if (fromVersion == 1){
            String targetFileName = config.getString("targetFileName", null);
            initSaveDirectories(config, editor);
            if (targetFileName != null && targetFileName.length() > 0){
                File tfn = new File(targetFileName);
                editor.putString("lastFileName", tfn.getName());
                File tdn = tfn.getParentFile();
                if (tdn != null){
                    editor.putString("gpxTargetDirName", tdn.toString());
                    File tdnp = tdn.getParentFile();
                    if (tdnp != null){
                        editor.putString("gpxTargetDirNameTemp", new File(tdnp, tdn.getName() + "-temp").toString());
                        editor.putString("imagesTargetDirName", new File(tdnp, ".cacheImages").toString()); 
                    }
                }
            }
            editor.remove("targetFileName");
        }
    }
}
