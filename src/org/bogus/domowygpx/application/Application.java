package org.bogus.domowygpx.application;

public class Application extends android.app.Application
{
    private OKAPI okApi;
    
    public OKAPI getOkApi()
    {
        return okApi;
    }
    
    @Override
    public void onCreate()
    {
        super.onCreate();
        okApi = new OKAPI(this);
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
}
