package org.bogus.domowygpx.application;

import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

import org.bogus.android.AndroidUtils;
import org.bogus.domowygpx.oauth.OKAPI;
import org.bogus.domowygpx.utils.TargetDirLocator;
import org.bogus.geocaching.egpx.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.app.backup.BackupManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class Application extends android.app.Application implements OnSharedPreferenceChangeListener
{
    private final static String LOG_TAG = "Opencaching:Application";
    private volatile OKAPI okApi;
    private int notificationIconResid;
    
    File offlineDump;
    long offlineDumpPostpone;
    
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
    
    protected void cleanupOfflineDumpState()
    {
        offlineDump = null;
        offlineDumpPostpone = 0;
        Editor editor = getSharedPreferences("egpx", MODE_PRIVATE).edit();
        editor.remove("Dump.offlineDumpFile");
        editor.remove("Dump.offlineDumpPostpone");
        AndroidUtils.applySharedPrefsEditor(editor);
    }
    
    protected void postponeOfflineDumpState()
    {
        if (offlineDump != null){
            offlineDumpPostpone = System.currentTimeMillis() + 15L*60L*1000L;
            Editor editor = getSharedPreferences("egpx", MODE_PRIVATE).edit();
            editor.putLong("Dump.offlineDumpPostpone", offlineDumpPostpone);
            AndroidUtils.applySharedPrefsEditor(editor);
        }
    }
    
    public boolean showErrorDumpInfo(final Activity activity)
    {
        if (offlineDump != null && System.currentTimeMillis() > offlineDumpPostpone){
            if (!offlineDump.exists()){
                cleanupOfflineDumpState();
                return false;
            }
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    final StateCollector stateCollector = new StateCollector(activity);
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle(R.string.infoDeveloperDialogTitle);
                    builder.setMessage(getResources().getString(R.string.infoDeveloperDialogText,
                        stateCollector.extractFileName(offlineDump)));
                    builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            cleanupOfflineDumpState();
                        }
                    });
                    builder.setNeutralButton(R.string.infoDeveloperInfoPostpone, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            postponeOfflineDumpState();
                        }
                    });
                    builder.setPositiveButton(R.string.infoDeveloperInfoSend, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            stateCollector.sendEmail(offlineDump);
                            cleanupOfflineDumpState();
                        }
                    });
                    AlertDialog dialog = builder.show();
                    dialog.setOnCancelListener(new DialogInterface.OnCancelListener()
                    {
                        
                        @Override
                        public void onCancel(DialogInterface dialog)
                        {
                            postponeOfflineDumpState();
                        }
                    });
                }
            }, 1500);
            return true;
        }
        return false;
    }
    
    @Override
    public void onCreate()
    {
        Log.i(LOG_TAG, "Called onCreate");
        super.onCreate();
        
        final SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
        
        final StateCollector stateCollector = new StateCollector(this);
        final long offlineDumpTimestamp = stateCollector.hasOfflineDump();
        if (offlineDumpTimestamp > 0){
            try{
                final Semaphore offlineDumpReady = new Semaphore(0); 
                // this could take a while, but what to do else?
                AsyncTask<Void, Void, File> dumpTask = new AsyncTask<Void, Void, File>(){

                    @Override
                    protected File doInBackground(Void... params)
                    {
                        try{
                            File file = stateCollector.dumpDataOffline(offlineDumpTimestamp, offlineDumpReady);
                            if (file != null){
                                Log.i(LOG_TAG, "Offline dump saved to file=" + file);
                            }
                            return file;
                        }catch(Exception e){
                            Log.e(LOG_TAG, "Failed to create offline dump", e);
                            return null;
                        }
                    }
                    
                    @Override
                    protected void onPostExecute(File dumpFile)
                    {
                        try{
                            offlineDump = dumpFile;
                            if (dumpFile != null){
                                Editor editor = config.edit();
                                editor.putString("Dump.offlineDumpFile", dumpFile.toString());
                                editor.remove("Dump.offlineDumpPostpone");
                                AndroidUtils.applySharedPrefsEditor(editor);
                                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(dumpFile));
                                intent.setType("application/octet-stream");
                                sendBroadcast(intent);
                            }
                        }catch(Exception e){
                            Log.e(LOG_TAG, "Failed to create offline dump", e);
                        }
                    }
                };
                // don't bother with previous dump, we are preparing the next one :-]
                cleanupOfflineDumpState();
                dumpTask.execute();    
                offlineDumpReady.acquireUninterruptibly();
            }catch(Exception e){
                Log.e(LOG_TAG, "Failed to create offline dump", e);
            }
        } else {
            String odf = config.getString("Dump.offlineDumpFile", null);
            if (odf != null){
                offlineDump = new File(odf);
                offlineDumpPostpone = config.getLong("Dump.offlineDumpPostpone", 0);
            }
        }
        
        final Thread main = Thread.currentThread();
        final UncaughtExceptionHandler mueh = main.getUncaughtExceptionHandler();
        final UncaughtExceptionHandler sueh = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler(){

            @Override
            public void uncaughtException(Thread thread, Throwable ex)
            {
                Thread.setDefaultUncaughtExceptionHandler(sueh);
                if (thread == main){
                    thread.setUncaughtExceptionHandler(mueh);
                }
                Log.e(LOG_TAG, "Error in threadId=" + thread.getId() + " [" + thread.getName() + "]", ex);
                try{
                    stateCollector.createEmergencyDump();
                }catch(Exception e){
                    Log.e(LOG_TAG, "Failed to create emergency dump", e);
                }
                if (thread == main && mueh != null){
                    Log.i(LOG_TAG, "Invoking main.uncaughtException");
                    mueh.uncaughtException(thread, ex);
                } else 
                if (sueh != null){
                    Log.i(LOG_TAG, "Invoking default.uncaughtException");
                    sueh.uncaughtException(thread, ex);
                } else {
                    Log.i(LOG_TAG, "Invoking System.exit()");
                    System.exit(1);
                }
            }});
        
        setupPreferences();
        cleanupDevDumps();
        cleanupTempGpx(config);
        
        config.registerOnSharedPreferenceChangeListener(this);
        
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
    
    private void cleanupDevDumps()
    {
        try{
            final File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            final String[] files = dir.list();
            if (files == null){
                return ;
            }
            final long timeStamp = System.currentTimeMillis() - 7L*24L*60L*60L*1000L;
            int count = 0;
            for (String name : files){
                if ((name.startsWith("awaryjniejszy-gpx-state-") || name.startsWith("awaryjniejszy-gpx-x-state-")) 
                        && name.endsWith(".tgz")){
                    File f2 = new File(dir, name);
                    if (f2.lastModified() < timeStamp){
                        if (f2.delete()){
                            count++;
                        }
                    }                    
                }
            }
            if (count > 0){
                Log.i(LOG_TAG, "Removed " + count + " developer dumps");
            }
        }catch(Exception e){
            Log.e(LOG_TAG, "Failed to cleanup developer dumps", e);
        }
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
                    AndroidUtils.applySharedPrefsEditor(editor);
                    
                    createSaveDirectories(config);
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

    private void cleanupTempGpx(SharedPreferences config)
    {
        String dir = config.getString("gpxTargetDirNameTemp", null);
        if (dir == null){
            return ;
        }
        File[] files = new File(dir).listFiles();
        if (files == null || files.length == 0){
            return ;
        }
        final Pattern pattern = Pattern.compile("20[0-9][0-9]-[0-1][0-9]-[0-3][0-9]_[0-2][0-9]\\.[0-6][0-9]\\.gpx");
        final long tresholdTime = System.currentTimeMillis() - 3L*24L*60L*60L*1000L;
        int count = 0;
        for (File f : files){
            final String name = f.getName();
            if (pattern.matcher(name).matches()){
                long date = f.lastModified();
                if (date < tresholdTime){
                    if (f.delete()){
                        count++;
                    }
                }
            }
        }
        if (count > 0){
            Log.i(LOG_TAG, "Removed " + count + " temp GPX files");
        }
    }
    
    private boolean createSaveDirectories(SharedPreferences config)
    {
        boolean result = true;
        result = createDirectories(config, "gpxTargetDirName") & result;
        result = createDirectories(config, "gpxTargetDirNameTemp") & result;
        result = createDirectories(config, "imagesTargetDirName") & result;
        return result;
    }
    
    private void initNewConfig()
    {
        SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
        Editor editor = config.edit();
        editor.clear();
        
        initSaveDirectories(config, editor);
        
        editor.putInt("configVersion", 2);
        AndroidUtils.applySharedPrefsEditor(editor);    
    }
    
    private void initSaveDirectories(SharedPreferences config, Editor editor)
    {
        final TargetDirLocator tdl = new TargetDirLocator(this);
        final List<File> locus = tdl.locateLocus();
        File gpxTargetDirName = null;
        File gpxTargetDirNameTemp = null;
        File imagesTargetDirName = null;
        Resources res = getResources();
        if (locus != null && !locus.isEmpty()){
            File loc = locus.get(0); 
            gpxTargetDirName = new File(loc, "mapItems");
            gpxTargetDirNameTemp = new File (loc, res.getString(R.string.dir_locus_temp));
            imagesTargetDirName = new File(loc, res.getString(R.string.dir_locus_images));
        } else {
            final List<File> data = tdl.locateSaveDirectories();
            final File dir = data.get(0);
            gpxTargetDirName = new File(dir, res.getString(R.string.dir_default));
            gpxTargetDirNameTemp = new File (dir, res.getString(R.string.dir_default_temp));
            imagesTargetDirName = new File(dir, res.getString(R.string.dir_default_images));
        }
        
        editor.putString("gpxTargetDirName", gpxTargetDirName.toString());
        editor.putString("gpxTargetDirNameTemp", gpxTargetDirNameTemp.toString()); 
        editor.putString("imagesTargetDirName", imagesTargetDirName.toString());
        
    }
    
    private boolean createDirectories(SharedPreferences config, String key)
    {
        String val = config.getString(key, null);
        if (val != null){
            File f = new File(val);
            f.mkdirs();
            return f.exists();
        } else {
            return true;
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
    
    public boolean initSaveDirectories()
    {
        SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
        Editor editor = config.edit();
        initSaveDirectories(config, editor);
        AndroidUtils.applySharedPrefsEditor(editor);
        boolean result = createSaveDirectories(config);
        return result;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        BackupManager.dataChanged(getPackageName());
    }
}
