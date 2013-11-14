package org.bogus.domowygpx.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.HttpClient;
import org.bogus.domowygpx.downloader.DownloadProgressMonitor;
import org.bogus.domowygpx.downloader.DownloadedFileData;
import org.bogus.domowygpx.downloader.FileData;
import org.bogus.domowygpx.downloader.FilesDownloader;
import org.bogus.domowygpx.downloader.HttpClientFactory;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class FilesDownloaderService extends Service
{
    private final static String LOG_TAG = "FilesDownloaderSvc";  
    public static final String INTENT_EXTRA_FILES = "org.bogus.domowygpx.FilesDownloaderService.TASK_CONFIGURATION";
    public static final String INTENT_ACTION_START_DOWNLOAD = "org.bogus.domowygpx.FilesDownloaderService..START_DOWNLOAD_FILES";
    
    private FilesDownloader filesDownloader;
    private HttpClient httpClient;
    
    final List<Integer> startIds = new ArrayList<Integer>();
    
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) 
    {
        synchronized(startIds){
            startIds.add(startId);
        }
        Log.i(LOG_TAG, "called onStartCommand for startId=" + startId);

        if (intent != null){
            if (INTENT_ACTION_START_DOWNLOAD.equals(intent.getAction())){
                FileData[] files = (FileData[])intent.getParcelableArrayExtra(INTENT_EXTRA_FILES);
                if (files == null){
                    throw new NullPointerException("Missing " + INTENT_EXTRA_FILES);
                }
                // TODO: save files in database, remove/update them as we process
                filesDownloader.submit(Arrays.asList(files));
                /*
                Notification notif = null; 
                notif = new Notification();
                PendingIntent pi = new PendingIntent();
                notif.setLatestEventInfo(this, "AAA", "Bbb", contentIntent)
                super.startForeground(int id, notif);
                */
                // XXX: notification
            }
            
        } else {
            // TODO: restart activity - if return form this method would be Service.START_STICKY 
        }
        return Service.START_NOT_STICKY;
    }
    
    @Override
    public void onCreate()
    {
        super.onCreate();
        httpClient = HttpClientFactory.createHttpClient(true);
        filesDownloader = new FilesDownloader(httpClient, 4);
        filesDownloader.addObserver(new DownloadProgressMonitor()
        {
            
            @Override
            public void notifyTasksFinished(boolean shutdownEvent)
            {
                FilesDownloaderService.this.notifyTasksFinished();
            }
            
            @Override
            public void notifyFileStarted(FileData fileData)
            {
            }
            
            @Override
            public void notifyFileSkipped(FileData fileData)
            {
            }
            
            @Override
            public void notifyFileProgress(FileData fileData, int doneKB, int totalKB)
            {
            }
            
            @Override
            public void notifyFileFinished(DownloadedFileData fileData, Exception exception)
            {
            }
        });
    }

    protected synchronized void notifyTasksFinished()
    {
        if (filesDownloader.areAllTasksFinished()){
            synchronized(startIds){
                boolean willBeStopped = false;
                for (Integer startId : startIds){
                    willBeStopped |= super.stopSelfResult(startId);
                }
                Log.i(LOG_TAG, "Called stopSelfResult(" + startIds + "), willStop=" + willBeStopped);
                startIds.clear();
            }
        }
    }
    
    @Override
    public synchronized void onDestroy()
    {
        Log.i(LOG_TAG, "called onDestroy, allTasksFinished=" + filesDownloader.areAllTasksFinished());
        super.onDestroy();
        filesDownloader.abortDownload();
        try{
            filesDownloader.awaitTermination(60, TimeUnit.SECONDS);
        }catch(InterruptedException ie){
            
        }
        HttpClientFactory.closeHttpClient(httpClient);
        Log.i(LOG_TAG, "returning from onDestroy, allTasksFinished=" + filesDownloader.areAllTasksFinished());
        httpClient = null;
        filesDownloader = null;
    }
}
