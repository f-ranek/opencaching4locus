package org.bogus.domowygpx.services;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.http.ConnectionClosedException;
import org.apache.http.client.HttpClient;
import org.bogus.ToStringBuilder;
import org.bogus.domowygpx.activities.DownloadListActivity;
import org.bogus.domowygpx.application.Application;
import org.bogus.domowygpx.services.downloader.DownloadProgressMonitor;
import org.bogus.domowygpx.services.downloader.FileData;
import org.bogus.domowygpx.services.downloader.FilesDownloader;
import org.bogus.domowygpx.utils.DumpDatabase;
import org.bogus.domowygpx.utils.HttpClientFactory;
import org.bogus.domowygpx.utils.HttpClientFactory.CreateHttpClientConfig;
import org.bogus.domowygpx.utils.HttpException;
import org.bogus.geocaching.egpx.BuildConfig;
import org.bogus.geocaching.egpx.R;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;

public class FilesDownloaderService extends Service implements FilesDownloaderApi
{
    private final static String LOG_TAG = "FilesDownloaderSvc";  
    //private static final List<FilesDownloadTask> EMPTY_LIST = Collections.emptyList();
    
    private static final int NOTIFICATION_ID_ONGOING = 0x20;
    private static final int NOTIFICATION_ID_FINISHED = NOTIFICATION_ID_ONGOING+1;

    /**
     * Private action to start downloads job
     */
    public static final String INTENT_ACTION_START_DOWNLOAD = "org.bogus.domowygpx.FilesDownloaderService.START_DOWNLOAD_FILES";
    
    private static final String INTENT_EXTRA_TAKS_ID = "org.bogus.domowygpx.FilesDownloaderService.taskId";
    private static final String INTENT_EXTRA_RESTART_FROM_SCRATCH = "org.bogus.domowygpx.FilesDownloaderService.restartFromScratch";
    
    private ConcurrentMap<File, Boolean> filesOnHold = new ConcurrentHashMap<File, Boolean>(8);
    private FilesDownloader freeFilesDownloader;
    private volatile List<FilesDownloadTask> downloadTasks;
    
    public static class FilesDownloadTask implements Cloneable {
        public int taskId;
        
        public int state;
        public final static int STATE_RUNNING = 0;
        public final static int STATE_FINISHED = 1;
        public final static int STATE_CANCELLING = 2;
        public final static int STATE_PAUSING = 4;
        public final static int STATE_STOPPED = 7;
        
        /**
         * Returns true, if no files were successfully downloaded. If all the files 
         * were skipped, returns false
         * @return
         */
        public boolean isFailed()
        {
            return (finishedFiles == 0) && (skippedFiles < totalFiles);
        }
        
        /**
         * Returns true, if there are files that failed to download
         * @return
         */
        public boolean hasFailures()
        {
            return (permanentErrorFiles > 0) || (transientErrorFiles > 0);
        }

        int flags;

        // final static int FLAG_INTERNAL_PAUSE = 1; // TODO: internal-pause (due to the network outage)
        // final static int FLAG_NOTIFICATION_DONE = 2;
        /** Used to optimise system notifications */
        final static int FLAG_FIRST_FILE_STARTED = 4;
        
        
        public final long createdDate;
        
        public int totalFiles;
        /** Number of bytes downloaded for this task. This is never reset */
        public long totalDownloadSize;
        public int finishedFiles; 
        public int skippedFiles;
        public int permanentErrorFiles;
        public int transientErrorFiles;
        
        FilesDownloader filesDownloader;
        
        FilesDownloadTask(long createdDate)
        {
            this.createdDate = createdDate;
        }
        
        @Override
        public synchronized FilesDownloadTask clone()
        {
            try{
                return (FilesDownloadTask)super.clone();
            }catch(CloneNotSupportedException cnse){
                throw new IllegalStateException(cnse);
            }
        }

        @Override
        public String toString()
        {
            if (BuildConfig.DEBUG){
                ToStringBuilder builder = new ToStringBuilder(this);
                builder.add("taskId", taskId);
                switch(state){
                    case STATE_RUNNING: builder.add("state", "STATE_RUNNING"); break; 
                    case STATE_FINISHED: builder.add("state", "STATE_FINISHED"); break;
                    case STATE_CANCELLING: builder.add("state", "STATE_CANCELLING"); break;
                    case STATE_PAUSING: builder.add("state", "STATE_PAUSING"); break;
                    case STATE_STOPPED: builder.add("state", "STATE_STOPPED"); break;
                    default: builder.add("state", state); break;
                }
                builder.add("createdDate", new Date(createdDate));
                builder.add("totalFiles", totalFiles);
                builder.add("totalDownloadSize", totalDownloadSize);
                builder.add("finishedFiles", finishedFiles, 0);
                builder.add("skippedFiles", skippedFiles, 0);
                builder.add("transientErrorFiles", transientErrorFiles, 0);
                builder.add("permanentErrorFiles", permanentErrorFiles, 0);
                return builder.toString();
            } else {
                return super.toString();
            }
        }
    }
    
    class DownloadProgressMonitorImpl implements DownloadProgressMonitor
    {
        final FilesDownloadTask downloadTask;
        
        public DownloadProgressMonitorImpl(FilesDownloadTask downloadTask)
        {
            this.downloadTask = downloadTask;
        }

        @Override
        public void notifyTasksFinished(boolean shutdownEvent)
        {
            FilesDownloaderService.this.onTaskFinished(this, shutdownEvent);
        }
        
        @Override
        public void notifyFileStarting(FileData fileData)
        {
            FilesDownloaderService.this.onFileStarting(this, fileData);
        }

        @Override
        public void notifyFileStarted(FileData fileData, long done, long expectedFileSize)
        {
            FilesDownloaderService.this.onFileStarted(this, fileData);
        }
        
        @Override
        public void notifyFileSkipped(FileData fileData)
        {
            FilesDownloaderService.this.onFileSkipped(this, fileData);
        }
        
        @Override
        public void notifyFileProgress(FileData fileData, int loopAmount, long sessionDone)
        {
            FilesDownloaderService.this.onFileProgress(this, fileData, loopAmount, sessionDone);
        }
        
        @Override
        public void notifyFileFinished(FileData fileData, Exception exception)
        {
            FilesDownloaderService.this.onFileFinished(this, fileData, exception);
        }
    }
    
    void onTaskFinished(DownloadProgressMonitorImpl caller,
        boolean shutdownEvent)
    {
        try{
            final FilesDownloadTask task = caller.downloadTask;
            Log.i(LOG_TAG, "Task finished, shutdownEvent=" + shutdownEvent + 
                ", id=" + task.taskId + ", state=" + task.state + 
                ", files=" + task.totalFiles + ", " + task.finishedFiles +
                ", " + task.skippedFiles + ", " + task.permanentErrorFiles + 
                ", " + task.transientErrorFiles + ", size=" + task.totalDownloadSize);
            synchronized(task){
                task.filesDownloader.setDownloadProgressMonitor(null);
                if (!shutdownEvent && !task.filesDownloader.isClosed()){
                    if (freeFilesDownloader == null){
                        // recycle downloader
                        freeFilesDownloader = task.filesDownloader;
                    } else {
                        // release downloader
                        task.filesDownloader.abortDownload();
                    }
                }
                task.filesDownloader = null;
                task.flags &= ~FilesDownloadTask.FLAG_FIRST_FILE_STARTED;
                
                final int taskId = task.taskId;
                final int currentState = task.state;
                final int newTaskState;
                switch(currentState){
                    case FilesDownloadTask.STATE_FINISHED:
                    case FilesDownloadTask.STATE_STOPPED:
                        // something is terribly wrong
                        newTaskState = -1;
                        break;
                    case FilesDownloadTask.STATE_PAUSING:
                    case FilesDownloadTask.STATE_CANCELLING:
                        newTaskState = FilesDownloadTask.STATE_STOPPED;
                        break;
                    case FilesDownloadTask.STATE_RUNNING:
                        newTaskState = FilesDownloadTask.STATE_FINISHED;
                        break;
                    default:
                        newTaskState = -2;
                }
        
                if (newTaskState < 0){
                    Log.e(LOG_TAG, "Fuck, some state is wrong, taskId=" + taskId + ", currentState=" + currentState);
                    return ;
                }
                if (!updateTask(task, newTaskState, currentState)){
                    return ;
                }
                
                final FilesDownloadTask task2 = task.clone();
                task2.filesDownloader = null;
                for (final Pair<Handler, FilesDownloaderListener> listener : listeners){
                    final FilesDownloaderListener fdl = listener.second;
                    listener.first.post(new Runnable(){
                        @Override
                        public void run(){
                            fdl.onTaskFinished(task2);
                        }
                    });
                }
            }
        }finally{
            adjustDownloaderThreads();
            updateNotification();
            shutdownSelf();
        }
    }
    
    void onFileStarting(DownloadProgressMonitorImpl caller, final FileData fileData)
    {
        // TODO: check network availability, if not available sleep in a loop for a while,
        // if still unavailable then 
        // - internaly pause the task, 
        // - throw some exception, 
        //   - then catch it in onFileFinished, but do not notify client of an error 
        // - add callback to notify client of task state changes 
        //   (ie. paused due to the network outage, other's client actions and so on)
        final FilesDownloadTask task = caller.downloadTask;
        final FilesDownloadTask task2;
        boolean updateNotification;
        synchronized(task){
            updateNotification = (task.flags & FilesDownloadTask.FLAG_FIRST_FILE_STARTED) == 0;
            if (updateNotification){
                task.flags |= FilesDownloadTask.FLAG_FIRST_FILE_STARTED;
            }
            task2 = task.clone();
        }
        if (updateNotification){
            updateNotification();
        }
        
        fileData.state = FileData.FILE_STATE_RUNNING;
        
        task2.filesDownloader = null;
        final FileData fileData2 = fileData.clone();
        for (final Pair<Handler, FilesDownloaderListener> listener : listeners){
            final FilesDownloaderListener fdl = listener.second;
            listener.first.post(new Runnable(){
                @Override
                public void run(){
                    fdl.onFileStarting(task2, fileData2);
                }
            });
        }
    }
    
    void onFileStarted(DownloadProgressMonitorImpl caller, FileData fileData)
    {
        updateFileInDatabase(fileData, true);
        
        final FilesDownloadTask task2 = caller.downloadTask.clone();
        task2.filesDownloader = null;
        final FileData fileData2 = fileData.clone();
        for (final Pair<Handler, FilesDownloaderListener> listener : listeners){
            final FilesDownloaderListener fdl = listener.second;
            listener.first.post(new Runnable(){
                @Override
                public void run(){
                    fdl.onFileStarted(task2, fileData2);
                }
            });
        }
    }

    void onFileSkipped(DownloadProgressMonitorImpl caller, FileData fileData)
    {
        fileData.state = FileData.FILE_STATE_SKIPPED;
        synchronized(caller.downloadTask){
            caller.downloadTask.skippedFiles++;
        }
        updateFileInDatabase(fileData, false);
        
        final FilesDownloadTask task2 = caller.downloadTask.clone();
        task2.filesDownloader = null;
        final FileData fileData2 = fileData.clone();
        for (final Pair<Handler, FilesDownloaderListener> listener : listeners){
            final FilesDownloaderListener fdl = listener.second;
            listener.first.post(new Runnable(){
                @Override
                public void run(){
                    fdl.onFileSkipped(task2, fileData2);
                }
            });
        }
    }

    void onFileProgress(DownloadProgressMonitorImpl caller, FileData fileData, int loopAmount, long sessionDone)
    {
        final FilesDownloadTask task = caller.downloadTask; 
        final FilesDownloadTask task2;
        synchronized(task){
            task.totalDownloadSize += loopAmount; 
            task2 = caller.downloadTask.clone();
        }
        task2.filesDownloader = null;
        final FileData fileData2 = fileData.clone();
        for (final Pair<Handler, FilesDownloaderListener> listener : listeners){
            final FilesDownloaderListener fdl = listener.second;
            listener.first.post(new Runnable(){
                @Override
                public void run(){
                    fdl.onFileProgress(task2, fileData2);
                }
            });
        }
    }
    
    protected boolean isTransientError(Exception exception)
    {
        if (exception == null){
            return false;
        }
        if (exception instanceof FileNotFoundException){
            return false;
        }
        if (exception instanceof HttpException){
            final HttpException httpException = (HttpException)exception;
            final int code = httpException.httpCode;
            if (code == 408 // Request Timeout
             || code == 503 // Service Unavailable
             || code == 504 // Gateway Timeout
             || code == 522 // Connection timed out       
            ){
                return true;
            }
        }
        if (exception instanceof IOException){
            if (exception instanceof UnknownHostException){
                return true;
            }
            if (exception instanceof SocketException){
                return true;
            }
            if (exception instanceof InterruptedIOException){
                return true;
            }
            if (exception instanceof ConnectionClosedException){
                return true;
            }
        }
        return false;
    }
    
    void onFileFinished(DownloadProgressMonitorImpl caller, FileData fileData, final Exception exception)
    {
        final FilesDownloadTask downloadTask = caller.downloadTask;
        final int taskState = downloadTask.state;
        final boolean isCancelled = (taskState == FilesDownloadTask.STATE_CANCELLING) || 
                (exception instanceof InterruptedException);
        final boolean isOk = exception == null;
        final boolean isTransientError = !isCancelled && !isOk && isTransientError(exception);
        if (isOk){
            fileData.state = FileData.FILE_STATE_FINISHED;
        } else
        if (isCancelled){
            fileData.state = FileData.FILE_STATE_ABORTED;
            fileData.exception = null;
        } else
        if (isTransientError){
            if (fileData.retryCount++ >= 3){
                fileData.state = FileData.FILE_STATE_PERMANENT_ERROR;
            } else {
                fileData.state = FileData.FILE_STATE_TRANSIENT_ERROR;
            }
        } else {
            fileData.state = FileData.FILE_STATE_PERMANENT_ERROR;  
        }
        
        if (BuildConfig.DEBUG){
            Log.d(LOG_TAG, "onFileFinished, file=" + fileData.fileDataId + ", " + fileData.source);
        }
        
        updateFileInDatabase(fileData, false);
        
        if (fileData.state == FileData.FILE_STATE_TRANSIENT_ERROR){
            //ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            //if (cm.getActiveNetworkInfo())
        }
        
        synchronized(downloadTask){
            if (isOk){
                downloadTask.finishedFiles++;
            } else
            if(!isCancelled){
                if (isTransientError){
                    downloadTask.transientErrorFiles++;
                } else {
                    downloadTask.permanentErrorFiles++;
                }
            }
        }
        
        
        final FilesDownloadTask task2 = downloadTask.clone();
        task2.filesDownloader = null;
        final FileData fileData2 = fileData.clone();
        for (final Pair<Handler, FilesDownloaderListener> listener : listeners){
            final FilesDownloaderListener fdl = listener.second;
            listener.first.post(new Runnable(){
                @Override
                public void run(){
                    fdl.onFileFinished(task2, fileData2, exception);
                }
            });
        }
    }
    
    protected void updateFileInDatabase(FileData fileData, boolean includeHeaders)
    {
        database.beginTransaction();
        try{
            if(!includeHeaders){
                Cursor cursor = database.rawQuery("select 1 from files where headers is null and _id=" + fileData.fileDataId, null);
                if (cursor.moveToFirst()){
                    includeHeaders = true;
                }
                cursor.close();
            }
            ContentValues cv = new ContentValues(5);
            if (includeHeaders){
                if (fileData.headers != null){
                    StringBuilder sb = new StringBuilder(256);
                    for (String[] header : fileData.headers){
                        if (sb.length() > 0){
                            sb.append('\n');
                        }
                        sb.append(header[0]).append(": ").append(header[1]);
                    }
                    cv.put("headers", sb.toString());
                }
                if (fileData.statusLine != null){
                    cv.put("status_line", fileData.statusLine);
                }
            }
            if (fileData.exception != null){
                StringWriter sw = new StringWriter(2048);
                fileData.exception.printStackTrace(new PrintWriter(sw, false));
                cv.put("exception", sw.toString());
            }
            cv.put("retry_count", fileData.retryCount);
            cv.put("state", fileData.state);
            database.update("files", cv, "_id=" + fileData.fileDataId, null);
            database.setTransactionSuccessful();
        }finally{
            database.endTransaction();
        }
    }
    
    protected synchronized FilesDownloader createFilesDownloader()
    {
        FilesDownloader fd = null;
        if (freeFilesDownloader != null){
            fd = freeFilesDownloader;
            freeFilesDownloader = null;
        }
        if (fd == null || fd.isClosed()){
            if (httpClient == null){
                final CreateHttpClientConfig ccc = new CreateHttpClientConfig(this);
                ccc.shared = true;
                httpClient = HttpClientFactory.createHttpClient(ccc);
            }
            fd = new FilesDownloader(httpClient, 1, filesOnHold);
        }
        return fd;
    }
    
    private List<FileData> loadTaskFiles(int taskId, String whereClause, int expectedCount)
    {
        boolean loop = false;
        ArrayList<FileData> result = new ArrayList<FileData>(expectedCount);
        do{
            // in case of malicious input (long URLs), we have to chunk data, otherwise strange exception happens
            // see the reason: http://code.google.com/p/opencaching-api/issues/detail?id=283#c13
            if (BuildConfig.DEBUG){
                Log.d(LOG_TAG, "Loading files from offset=" + result.size());
                if (!result.isEmpty()){
                    Log.d(LOG_TAG, "Last _id=" + result.get(result.size()-1).fileDataId);
                }
            }
            Cursor cursor = database.rawQuery(
                "select _id, state, source, target, retry_count, headers " +
                " from files where " + whereClause + 
        		" order by _id limit 25 offset " + result.size(), 
                null);
            loop = cursor.moveToFirst(); 
            if (loop){
                do{
                    final FileData file = new FileData();
                    file.fileDataId = cursor.getInt(0);
                    try {
                        file.taskId = taskId; 
                        file.state = cursor.getInt(1);
                        file.source = new URI(cursor.getString(2));
                        file.target = new File(cursor.getString(3));
                        file.retryCount = cursor.getInt(4);
                        String headers = cursor.getString(5);
                        if (headers != null){
                            String[] headers2 = headers.split("[\n\r]+");
                            file.headers = new String[headers2.length][];
                            for (int i=0; i<headers2.length; i++){
                                String header = headers2[i];
                                int idx = header.indexOf(':');
                                String headerName = header.substring(0, idx);
                                String headerValue = header.substring(idx+2);
                                file.headers[i] = new String[]{headerName, headerValue};
                            }
                        }
                        result.add(file);
                    } catch (URISyntaxException e) {
                        Log.e(LOG_TAG, "Failed to read file, _id=" + file.fileDataId, e);
                    }
                }while(cursor.moveToNext());
            }
            cursor.close();
        }while(loop);
        return result;
    }
    
    /**
     * Reads files to be downloaded for a given task, and schedules them for download
     * @param task
     * @param restartFromScratch
     */
    protected void startTaskFromDatabase(
        final FilesDownloadTask task, 
        final DownloadProgressMonitorImpl dpm,
        final boolean restartFromScratch) 
    {
        AsyncTask<Void, Void, Void> task2 = new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... params)
            {
                startTaskFromDatabaseInThread(task, dpm, restartFromScratch);
                return null;
            }};
        task2.execute();
    }

    protected void startTaskFromDatabaseInThread(
        final FilesDownloadTask task, 
        final DownloadProgressMonitorImpl dpm,
        final boolean restartFromScratch) 
    {
        final List<FileData> files; 
        database.beginTransaction();
        try{
            synchronized(task){
                if (restartFromScratch){
                    ContentValues cv = new ContentValues(2);
                    cv.put("state", FileData.FILE_STATE_SCHEDULED);
                    cv.put("retry_count", 0);
                    int rows = database.update("files", cv, "(state <> " + FileData.FILE_STATE_SCHEDULED + " or retry_count <> 0) AND task_id=" + task.taskId, null);
                    Log.i(LOG_TAG, "Restarting task, updated #" + rows + " rows");
                }
                
                loadTaskStats(task);
                Log.i(LOG_TAG, "Starting task, id=" + task.taskId + 
                    ", files=" + task.totalFiles + ", " + task.finishedFiles +
                    ", " + task.skippedFiles + ", " + task.permanentErrorFiles + 
                    ", " + task.transientErrorFiles + ", size=" + task.totalDownloadSize);
                // reset those, since we will process them again
                task.skippedFiles = task.transientErrorFiles = 0;
                
                StringBuilder whereClause = new StringBuilder(32);
                whereClause.append("task_id = ").append(task.taskId);
                whereClause.append(" and state in (");
                whereClause.append(FileData.FILE_STATE_SCHEDULED).append(", ");
                whereClause.append(FileData.FILE_STATE_RUNNING).append(", "); // there should be no such files
                whereClause.append(FileData.FILE_STATE_SKIPPED).append(", ");
                whereClause.append(FileData.FILE_STATE_ABORTED).append(", ");
                whereClause.append(FileData.FILE_STATE_TRANSIENT_ERROR).append(")");
                
                files = loadTaskFiles( task.taskId, whereClause.toString(), task.totalFiles);
                database.setTransactionSuccessful();
            }
        }finally{
            database.endTransaction();
        }
        
        Log.i(LOG_TAG, "Submitting task files, id=" + task.taskId + 
            ", count=" + files.size());
        task.filesDownloader.submit(files);
        
        if (files.size() == 0){
            onTaskFinished(dpm, false);
        }
    }
    
    /**
     * Iterates through the list of active tasks, and adjusts their downloader's thread pools
     */
    protected synchronized void adjustDownloaderThreads()
    {
        if (downloadTasks == null){
            return ;
        }
        int runningTasks = 0;
        for (FilesDownloadTask task : downloadTasks){
            synchronized(task){
                if (task.filesDownloader != null && task.state != FilesDownloadTask.STATE_CANCELLING){
                    runningTasks++;
                }
            }
        }
        if (runningTasks == 0){
            return ;
        }
        
        SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
        
        final int threadPool = Math.max(config.getInt("FilesDownloaderService_threadPoolSize", 2), 1);
        int threadsPerTask = threadPool / runningTasks;
        if (threadsPerTask == 0){
            threadsPerTask = 1;
        }
        int extra = threadPool - runningTasks*threadsPerTask;
        
        for (FilesDownloadTask task : downloadTasks){
            synchronized(task){
                if (task.filesDownloader != null && task.state != FilesDownloadTask.STATE_CANCELLING){
                    int numOfWorkerThreads = threadsPerTask;
                    if (extra > 0){
                        numOfWorkerThreads++;
                        extra--;
                    }
                    task.filesDownloader.setNumOfWorkerThreads(numOfWorkerThreads);
                }
            }
        }
    }
    
    protected void showNotification(NotificationCompat.Builder builder, boolean foreground)
    {
        builder.setSmallIcon(Application.getInstance(this).getNotificationIconResid());
        
        final Context appContext = getApplicationContext();
     
        // Creates an explicit intent for an Activity in your app
        final Intent resultIntent = new Intent(appContext, DownloadListActivity.class);
        final PendingIntent resultPendingIntent = PendingIntent.getActivity(
            appContext, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);

        builder.setAutoCancel(!foreground);
        
        final NotificationManager notificationManager = 
                (NotificationManager)getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        final Notification notif = builder.build();
        if (foreground){
            notificationManager.cancel(NOTIFICATION_ID_FINISHED);
            super.startForeground(NOTIFICATION_ID_ONGOING, notif);
        } else {
            super.stopForeground(true);
            notificationManager.notify(NOTIFICATION_ID_FINISHED, notif);
        }
    }
    
    protected synchronized void updateNotification()
    {
        loadDatabase(false);
        int runningCount = 0;
        int finishedCount = 0;
        int stoppedCount = 0;
        int failedCount = 0;
        int withFailuresCount = 0;
        for (FilesDownloadTask task : downloadTasks){
            synchronized(task){
                switch(task.state){
                    case FilesDownloadTask.STATE_PAUSING:
                    case FilesDownloadTask.STATE_RUNNING:
                    case FilesDownloadTask.STATE_CANCELLING:
                        runningCount++;
                        break;
                    case FilesDownloadTask.STATE_FINISHED:
                        finishedCount++;
                        if (task.isFailed()){
                            failedCount++;
                        } else 
                        if (task.hasFailures()){
                            withFailuresCount++;
                        }
                        break;
                    case FilesDownloadTask.STATE_STOPPED:
                        stoppedCount++;
                        break;
                }
            }
        }
        
        final Resources res = getResources();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setOngoing(runningCount != 0);
        if (runningCount > 0){
            builder.setContentTitle(res.getString(R.string.files_downloader_in_progrss)); 
        } else
        if (stoppedCount > 0){
            builder.setContentTitle(res.getString(R.string.files_downloader_paused)); 
        } else
        if (failedCount == finishedCount) {
            builder.setContentTitle(res.getString(R.string.files_downloader_error));
        } else {
            builder.setContentTitle(res.getString(R.string.files_downloader_finished)); 
        }
        final StringBuilder sb = new StringBuilder();
        boolean wasFirstTaskInfo = false;
        if (runningCount > 0){
            if (wasFirstTaskInfo){
                sb.append(", "); 
            }
            sb.append(getResources().getQuantityString(
                wasFirstTaskInfo ? R.plurals.running : R.plurals.taskRunning, 
                        runningCount, runningCount));
            wasFirstTaskInfo = true;
        }
        if (finishedCount > 0){
            if (wasFirstTaskInfo){
                sb.append(", "); 
            }
            sb.append(getResources().getQuantityString(
                wasFirstTaskInfo ? R.plurals.downloader_finished : R.plurals.taskFinished, 
                finishedCount, finishedCount));
            wasFirstTaskInfo = true;
        }
        if (failedCount > 0){
            if (sb.length() > 0){
                sb.append(", "); 
            }
            sb.append(res.getString(R.string.files_downloader_with_errors1, failedCount));
        }
        if (withFailuresCount > 0){
            if (sb.length() > 0){
                sb.append(", "); 
            }
            sb.append(res.getString(R.string.files_downloader_with_errors2, withFailuresCount)); 
        }
        
        if (sb.length() > 0){
            builder.setContentText(sb);
        }
        showNotification(builder, runningCount != 0);
    }   
    
    protected FilesDownloadTask createTask()
    {
        FilesDownloadTask task = new FilesDownloadTask(System.currentTimeMillis());
        ContentValues values = new ContentValues(1);
        values.put("created_date", task.createdDate);
        int taskId = (int)database.insert("tasks", null, values);
        if (taskId == -1){
            throw new SQLiteException("Failed to insert task to DB");
        }
        task.taskId = taskId;
        return task;
    }
    /**
     * Updates task in database, if the update succeed, in-memory task state is also updated
     * @param newTask New task data
     * @param newState New task state
     * @param expectedState Expected task state
     * @return true, if task has been updated, false otherwise
     */
    private boolean updateTask(FilesDownloadTask newTask, int newState, int expectedState)
    {
        final int taskId = newTask.taskId;
        final ContentValues cv = new ContentValues(3);
        cv.put("state", newState);
        cv.put("total_download_size", newTask.totalDownloadSize);
        cv.put("flags", newTask.flags);
        int rows = database.update("tasks", cv, "_id=" + taskId + " and state=" + expectedState, null);
        if (rows > 0){
            newTask.state = newState;
            Log.i(LOG_TAG, "Update taskId=" + taskId + ", newState=" + newState + ", oldState=" + expectedState);
        } else {
            Log.w(LOG_TAG, "Failed to update taskId=" + taskId + ", newState=" + newState + ", expectedState=" + expectedState);
        }
        return rows > 0;
    }
    
    private HttpClient httpClient;
    
    final List<Integer> startIds = new ArrayList<Integer>();
    
    /** Keeps track of all current registered clients. */
    private final List<Pair<Handler, FilesDownloaderListener>> listeners = new CopyOnWriteArrayList<Pair<Handler,FilesDownloaderListener>>(); 

    /** Binder exposed to clients */
    private final IBinder mBinder = new LocalBinder();
    
    public class LocalBinder extends Binder implements LocalBinderIntf<FilesDownloaderApi> {
        private final FilesDownloaderApi proxy = new FilesDownloaderApiProxy(FilesDownloaderService.this);
        @Override
        public FilesDownloaderApi getService() 
        {
            return proxy; 
        }
    } 
    
    static class FilesDownloaderApiProxy implements FilesDownloaderApi
    {
        private final WeakReference<FilesDownloaderApi> target;
        public FilesDownloaderApiProxy(FilesDownloaderApi target)
        {
            this.target = new WeakReference<FilesDownloaderApi>(target);
        }
        
        private FilesDownloaderApi getTarget()
        {
            FilesDownloaderApi result = target.get();
            if (result == null){
                throw new IllegalStateException("You are storing a reference to already stopped service");
            }
            return result;
        }

        @Override
        public int createTaskForFiles(List<FileData> filesToDownload) throws IllegalArgumentException
        {
            return getTarget().createTaskForFiles(filesToDownload);
        }

        @Override
        public boolean stopTask(int taskId)
        {
            return getTarget().stopTask(taskId);
        }

        @Override
        public boolean restartTask(int taskId, boolean restartFromScratch)
        {
            return getTarget().restartTask(taskId, restartFromScratch);
        }

        @Override
        public boolean cancelTask(int taskId)
        {
            return getTarget().cancelTask(taskId);
        }

        @Override
        public boolean removeTask(int taskId)
        {
            return getTarget().removeTask(taskId);
        }

        @Override
        public List<FilesDownloadTask> getTasks()
        {
            return getTarget().getTasks();
        }

        @Override
        public void registerEventListener(FilesDownloaderListener listener)
        {
            getTarget().registerEventListener(listener);
        }

        @Override
        public boolean unregisterEventListener(FilesDownloaderListener listener)
        {
            return getTarget().unregisterEventListener(listener);
        }
        
        @Override
        public List<File> dumpDatabase(File rootDir)
        throws IOException
        {
            return getTarget().dumpDatabase(rootDir);
        }

        @Override
        public List<File> getDatabaseFileNames(Context ctx)
        {
            return getTarget().getDatabaseFileNames(ctx);
        }
    };
    
    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(Context context, String name,
                CursorFactory factory, int version) {
            super(context, name, factory, version);
        }
     
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE tasks(" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "created_date LONG NOT NULL, " +
                    "state INTEGER NOT NULL DEFAULT 0, " +
                    "flags INTEGER NOT NULL DEFAULT 0, " +
                    "total_download_size INTEGER " +
                    ");"); 
            db.execSQL("CREATE TABLE files(" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "task_id INTEGER NOT NULL, " +
                    "state INTEGER NOT NULL DEFAULT 0, " +
                    "cache_code TEXT, " +
                    "source TEXT NOT NULL, " +
                    "target TEXT NOT NULL, " +
                    "retry_count INTEGER NOT NULL DEFAULT 0, " +
                    "status_line TEXT, " + 
                    "headers TEXT, " +
                    "exception TEXT " +
                    ");"); 
            db.execSQL("CREATE INDEX files_idx1 ON files(task_id);");
        }
     
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            for (int version = oldVersion+1; version<=newVersion; version++){
                if (newVersion == 2){
                    db.execSQL("ALTER TABLE tasks ADD COLUMN total_download_size INTEGER;");
                    db.execSQL("UPDATE tasks SET total_download_size = 1024*total_files_size_kb");
                } else
                if (newVersion == 3){
                    
                }
            }
        }
        
        @Override
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        public void onConfigure(SQLiteDatabase db)
        {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
                db.enableWriteAheadLogging();
            }
        }
    }

    protected DatabaseHelper databaseHelper;
    private SQLiteDatabase database;
    
    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) 
    {
        startIds.add(startId);
        if (intent == null){
            Log.i(LOG_TAG, "called onStartCommand for startId=" + startId + ", system is restarting service");
        } else {
            Log.i(LOG_TAG, "called onStartCommand for startId=" + startId + ", action=" + intent.getAction());
        }

        try{
            loadDatabase(intent == null);
            if (intent == null || INTENT_ACTION_START_DOWNLOAD.equals(intent.getAction())){
                // null intent = redelivered intent
                int taskId = -1;
                if (intent != null){
                    taskId = intent.getIntExtra(INTENT_EXTRA_TAKS_ID, -1);
                }
                
                for (FilesDownloadTask task : downloadTasks){
                    synchronized(task){
                        if (task.state == FilesDownloadTask.STATE_RUNNING && (taskId == -1 || taskId == task.taskId)){
                            task.flags &= ~FilesDownloadTask.FLAG_FIRST_FILE_STARTED;
                            final FilesDownloader filesDownloader = createFilesDownloader();
                            task.filesDownloader = filesDownloader;
                            final DownloadProgressMonitorImpl dpm = new DownloadProgressMonitorImpl(task); 
                            filesDownloader.setDownloadProgressMonitor(dpm);
                            final boolean restartFromScratch = intent == null ? false : 
                                intent.getBooleanExtra(INTENT_EXTRA_RESTART_FROM_SCRATCH, false); 
                            startTaskFromDatabase(task, dpm, restartFromScratch);
                        }
                    }
                }
                adjustDownloaderThreads();
            } 
        }finally{
            shutdownSelf();
        }

        return Service.START_STICKY;
    }
    
    /**
     * Loads tasks database
     * @param systemRestart If out service is restarted by system?
     */
    private void loadDatabase(boolean systemRestart)
    {
        if (downloadTasks == null){
            synchronized(this){
                if (downloadTasks == null){
                    cleanupDatabase(systemRestart);
                    downloadTasks = loadTasks();
                }
            }
        }
    }
    
    protected void pushFileToDatabase(FileData fileData)
    {
        ContentValues insertValues = new ContentValues();
        insertValues.put("task_id", fileData.taskId);
        insertValues.put("cache_code", fileData.cacheCode);
        if (fileData.source != null){
            insertValues.put("source", fileData.source.toASCIIString());
        }
        if (fileData.target != null){
            insertValues.put("target", fileData.target.toString());
        }
        int fileDataId =  (int)database.insert("files", null, insertValues);
        if (fileDataId == -1){
            throw new SQLiteException("Failed to insert file to DB");
        }
        fileData.fileDataId = fileDataId;
    }
    
    @Override
    public synchronized void onCreate()
    {
        Log.i(LOG_TAG, "Called onCreate");
        super.onCreate();
        
        try{
            databaseHelper = new DatabaseHelper(this, "FilesDownloaderDatabase.db", null, 2);
            database = databaseHelper.getWritableDatabase();
        }catch(SQLiteException sqle){
            Log.e(LOG_TAG, "Failed to create database", sqle);
            throw sqle;
        }   
        
        stopForeground(true);
    }

    private void cleanupDatabase(boolean systemRestart)
    {
        database.beginTransaction();
        try{
            database.execSQL("update files set retry_count=retry_count+1, state=" + FileData.FILE_STATE_SCHEDULED + 
                " where state=" + FileData.FILE_STATE_RUNNING);
            Cursor cursor = database.rawQuery("select changes()", null);
            if (cursor.moveToFirst()){
                int changes = cursor.getInt(0);
                if (changes > 0){
                    Log.w(LOG_TAG, "Updated #" + changes + " files with state=FILE_STATE_RUNNING");
                }
            }
            cursor.close();

            database.execSQL("update tasks set state=case state when " + 
                    FilesDownloadTask.STATE_CANCELLING + " then " + FilesDownloadTask.STATE_STOPPED + 
                    " when " + FilesDownloadTask.STATE_PAUSING + " then " + FilesDownloadTask.STATE_STOPPED +
                    (systemRestart ? "" 
                        : " when " + FilesDownloadTask.STATE_RUNNING + " then " + FilesDownloadTask.STATE_STOPPED
                    ) + 
                    " else state " +
                    "end where state in (" + 
                        FilesDownloadTask.STATE_RUNNING + ", " + 
                        FilesDownloadTask.STATE_CANCELLING + ", " + 
                        FilesDownloadTask.STATE_PAUSING+ ")");
            cursor = database.rawQuery("select changes()", null);
            if (cursor.moveToFirst()){
                int changes = cursor.getInt(0);
                if (changes > 0){
                    Log.w(LOG_TAG, "Updated #" + changes + " tasks with transient state");
                }
            }
            cursor.close();
            
            /*database.execSQL("update tasks set state=" + 
                    FilesDownloadTask.STATE_FINISHED + 
                    " where state <> " + FilesDownloadTask.STATE_FINISHED +
                    " and not exists (" +
                    "   select 1 from files f where f.task_id = tasks._id and f.state in " +
                    "       (" + FileData.FILE_STATE_SCHEDULED + ", " + FileData.FILE_STATE_TRANSIENT_ERROR + "))");
            cursor = database.rawQuery("select changes()", null);
            if (cursor.moveToFirst()){
                int changes = cursor.getInt(0);
                if (changes > 0){
                    Log.w(LOG_TAG, "Updated #" + changes + " tasks with running state, but no files to download");
                }
            }
            cursor.close();*/
            
            /*database.execSQL("update tasks set state=" + // XXX WTF? why the hell this code was duplicated?
                    FilesDownloadTask.STATE_FINISHED + 
                    " where state <> " + FilesDownloadTask.STATE_FINISHED +
                    " and not exists (" +
                    "   select 1 from files f where f.task_id = tasks._id and f.state in " +
                    "       (" + FileData.FILE_STATE_SCHEDULED + ", " + FileData.FILE_STATE_TRANSIENT_ERROR + "))");
            cursor = database.rawQuery("select changes()", null);
            if (cursor.moveToFirst()){
                int changes = cursor.getInt(0);
                if (changes > 0){
                    Log.w(LOG_TAG, "Updated #" + changes + " tasks with running state, but no files to download");
                }
            }
            cursor.close();*/
            
            database.setTransactionSuccessful();
        }finally{
            database.endTransaction();
        }
        
        final SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
        final int removeFilesTasksDays = config.getInt("Application_removeFilesTasksDays", 14);
        if (removeFilesTasksDays > 0){
            database.beginTransaction();
            try{
                long ts = System.currentTimeMillis() - 24L*60L*60L*1000L*removeFilesTasksDays;
                database.delete("files", "task_id in (select _id from tasks where created_date < " + ts + ")", null);
                int rows = database.delete("tasks", "created_date < " + ts, null);
                if (rows > 0){
                    Log.i(LOG_TAG, "Deleted #" + rows + " old tasks");
                }
                database.setTransactionSuccessful();
            }finally{
                database.endTransaction();
            }
        }
        
    }
    
    private void loadTaskStats(FilesDownloadTask task)
    {
        task.totalFiles = task.finishedFiles = task.permanentErrorFiles = task.skippedFiles = task.transientErrorFiles = 0;
        Cursor stats = database.rawQuery("select state, count(1) from files where task_id=" + task.taskId + " group by state", null);
        if (stats.moveToFirst()){
            do{
                int state = stats.getInt(0);
                int count = stats.getInt(1);
                task.totalFiles += count;
                switch(state){
                    case FileData.FILE_STATE_FINISHED:
                        task.finishedFiles += count;
                        break;
                    case FileData.FILE_STATE_SKIPPED:
                        task.skippedFiles += count;
                        break;
                    case FileData.FILE_STATE_PERMANENT_ERROR:
                        task.permanentErrorFiles += count;
                        break;
                    case FileData.FILE_STATE_TRANSIENT_ERROR:
                        task.transientErrorFiles += count;
                        break;
                }
            }while(stats.moveToNext());
        }
        stats.close();
    }
    
    private List<FilesDownloadTask> loadTasks()
    {
        List<FilesDownloadTask> result;
        database.beginTransaction();
        try{
            Cursor tasks = database.query("tasks", 
                new String[]{"_id", "created_date", "state", "total_download_size", "flags"}, 
                null, null, null, null, "created_date");
            result = new ArrayList<FilesDownloadTask>();
            if (tasks.moveToFirst()){
                do{
                    FilesDownloadTask task = new FilesDownloadTask(tasks.getLong(1));
                    task.taskId = tasks.getInt(0);
                    task.state = tasks.getInt(2);
                    task.totalDownloadSize = tasks.getLong(3);
                    task.flags = tasks.getInt(4);
                    loadTaskStats(task);
                    result.add(task);
                }while(tasks.moveToNext());
            }
            tasks.close();
            database.setTransactionSuccessful();
        }finally{
            database.endTransaction();
        }
        return result;
    }
    
    protected synchronized FilesDownloadTask getTaskById(int taskId)
    {
        loadDatabase(false);
        for (FilesDownloadTask task : downloadTasks){
            if (task.taskId == taskId){
                return task;
            }
        }
        return null;
    }

    private void sendTaskStateChangedNotification(final FilesDownloadTask task, final int previousState)
    {
        final FilesDownloadTask clonedTask = task.clone();
        for (final Pair<Handler, FilesDownloaderListener> listener : listeners){
            final FilesDownloaderListener fdl = listener.second;
            if (listener.first.getLooper() == Looper.myLooper()){
                fdl.onTaskStateChanged(clonedTask, previousState);
            } else {
                listener.first.post(new Runnable(){
                    @Override
                    public void run(){
                        fdl.onTaskStateChanged(clonedTask, previousState);
                    }
                });
            }
        }           
    }
    
    @Override
    public int createTaskForFiles(final List<FileData> filesToDownload)
    throws IllegalArgumentException
    {
        if (filesToDownload.isEmpty()){
            throw new IllegalArgumentException("filesToDownload is empty");
        }
        
        loadDatabase(false);

        final FilesDownloadTask task;
        database.beginTransaction();
        try{
            task = createTask();
            task.totalFiles = filesToDownload.size();
            for (FileData file : filesToDownload){
                file.taskId = task.taskId;
                pushFileToDatabase(file);
            }
            database.setTransactionSuccessful();
        }finally{
            database.endTransaction();
        }
        synchronized(this){
            downloadTasks.add(task);
        }
        
        final Intent intent = new Intent(INTENT_ACTION_START_DOWNLOAD, null, this, FilesDownloaderService.class);
        intent.putExtra(INTENT_EXTRA_TAKS_ID, task.taskId);
        startService(intent);
        
        Log.i(LOG_TAG, "Starting taskId=" + task.taskId);
        
        return task.taskId;
    }
    
    @Override
    public boolean stopTask(int taskId)
    {
        final FilesDownloadTask task = getTaskById(taskId);
        if (task == null){
            return false;
        }
        boolean result = false; 
        synchronized(task){
            if (task.state == FilesDownloadTask.STATE_RUNNING){
                result = updateTask(task,  
                    FilesDownloadTask.STATE_PAUSING, FilesDownloadTask.STATE_RUNNING);
                if (result){
                    sendTaskStateChangedNotification(task, FilesDownloadTask.STATE_RUNNING);
                    if (task.filesDownloader != null){
                        task.filesDownloader.stopDownload();
                    } else {
                        if (!updateTask(task, FilesDownloadTask.STATE_STOPPED, task.state)){
                            return false;
                        }
                        
                        final FilesDownloadTask task2 = task.clone();
                        task2.filesDownloader = null;
                        for (final Pair<Handler, FilesDownloaderListener> listener : listeners) {
                            final FilesDownloaderListener fdl = listener.second;
                            if (listener.first.getLooper() == Looper.myLooper()){
                                fdl.onTaskFinished(task2);
                            } else {
                                listener.first.post(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        fdl.onTaskFinished(task2);
                                    }
                                });
                            }
                        }
                        
                    }
                }
            }
        }
        return result;
    }

    /*
    @Override
    public boolean resumeTask(int taskId)
    {
        final FilesDownloadTask task = getTaskById(taskId);
        if (task == null){
            return false;
        }
        final boolean result; 
        synchronized(task){
            if (task.state == FilesDownloadTask.STATE_RUNNING || 
                task.state == FilesDownloadTask.STATE_FINISHED || 
                task.state == FilesDownloadTask.STATE_CANCELLED || 
                task.state == FilesDownloadTask.STATE_CANCELLING)
            {
                return false;
            }
            if (task.state == FilesDownloadTask.STATE_PAUSING){
                task.continueFromPausing = true;
                return true;
            }
            final int currState = task.state;
            result = updateTask(task,  
                FilesDownloadTask.STATE_RUNNING, currState);
            if (result){
                sendTaskStateChangedNotification(task, currState);
                final Intent intent = new Intent(INTENT_ACTION_START_DOWNLOAD, null, this, FilesDownloaderService.class);
                intent.putExtra(INTENT_EXTRA_TAKS_ID, task.taskId);
                startService(intent);
            }
        }
        return result;
    }*/

    @Override
    public boolean restartTask(int taskId, boolean restartFromScratch)
    {
        final FilesDownloadTask task = getTaskById(taskId);
        if (task == null){
            return false;
        }
        final boolean result; 
        synchronized(task){
            final int oldTaskState = task.state; 
            if (oldTaskState != FilesDownloadTask.STATE_FINISHED && oldTaskState != FilesDownloadTask.STATE_STOPPED){
                return false;
            }
            
            final FilesDownloadTask taskClone = task.clone(); 
            int currState = task.state;
            // the stats will get updated in #startTaskFromDatabase
            if (restartFromScratch){
                task.finishedFiles = task.permanentErrorFiles = 0;
            }
            task.skippedFiles = task.transientErrorFiles = 0;
            result = updateTask(task, FilesDownloadTask.STATE_RUNNING, currState);
            if (result){
                sendTaskStateChangedNotification(task, currState);
                final Intent intent = new Intent(INTENT_ACTION_START_DOWNLOAD, null, this, FilesDownloaderService.class);
                intent.putExtra(INTENT_EXTRA_TAKS_ID, task.taskId);
                intent.putExtra(INTENT_EXTRA_RESTART_FROM_SCRATCH, restartFromScratch);
                startService(intent);
            } else {
                task.finishedFiles = taskClone.finishedFiles;
                task.skippedFiles = taskClone.skippedFiles;
                task.permanentErrorFiles = taskClone.permanentErrorFiles;
                task.transientErrorFiles = taskClone.transientErrorFiles;
            }
        }
        return result;
    }
    
    
    @Override
    public boolean cancelTask(int taskId)
    {
        final FilesDownloadTask task = getTaskById(taskId);
        if (task == null){
            return false;
        }
        boolean result = false; 
        synchronized(task){
            if (task.state == FilesDownloadTask.STATE_RUNNING || 
                task.state == FilesDownloadTask.STATE_PAUSING)
            {
                int currState = task.state;
                result = updateTask(task, 
                    FilesDownloadTask.STATE_CANCELLING, currState);
                if (result){
                    sendTaskStateChangedNotification(task, currState);
                    if (task.filesDownloader != null){
                        task.filesDownloader.abortDownload();
                    } else {
                        // hmm, cancelling not-running task
                        if (!updateTask(task, FilesDownloadTask.STATE_STOPPED, task.state)){
                            return false;
                        }
                        
                        final FilesDownloadTask task2 = task.clone();
                        task2.filesDownloader = null;
                        for (final Pair<Handler, FilesDownloaderListener> listener : listeners) {
                            final FilesDownloaderListener fdl = listener.second;
                            if (listener.first.getLooper() == Looper.myLooper()){
                                fdl.onTaskFinished(task2);
                            } else {
                                listener.first.post(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        fdl.onTaskFinished(task2);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public synchronized boolean removeTask(int taskId)
    {
        final FilesDownloadTask task = getTaskById(taskId);
        if (task == null){
            return false;
        }
        synchronized(task){
            if (task.filesDownloader != null){
                return false;
            }
            if (task.state == FilesDownloadTask.STATE_FINISHED || 
                task.state == FilesDownloadTask.STATE_STOPPED)
            {
                database.beginTransaction();
                try{
                    database.delete("files", "task_id=" + task.taskId, null);
                    database.delete("tasks", "_id=" + task.taskId, null);
                    downloadTasks.remove(task);
                    database.setTransactionSuccessful();
                }finally{
                    database.endTransaction();
                }
                for (final Pair<Handler, FilesDownloaderListener> listener : listeners){
                    final FilesDownloaderListener fdl = listener.second;
                    if (listener.first.getLooper() == Looper.myLooper()){
                        fdl.onTaskRemoved(task);
                    } else {
                        listener.first.post(new Runnable(){
                            @Override
                            public void run(){
                                fdl.onTaskRemoved(task);
                            }
                        });
                    }
                }                
                return true;
            }
            return false;
        }
    }
    

    @Override
    public List<FilesDownloadTask> getTasks()
    {
        loadDatabase(false);
        List<FilesDownloadTask> result = new ArrayList<FilesDownloadTask>(downloadTasks.size());
        for (FilesDownloadTask task : downloadTasks){
            FilesDownloadTask task2 = task.clone();
            task2.filesDownloader = null;
            result.add(task2);
        }
        return result;
    }
    
    @Override
    public List<File> dumpDatabase(File rootDir)
    throws IOException
    {
        final File target = new File(rootDir, "FilesDownloader.xml");
        final DumpDatabase dd = new DumpDatabase();
        if (dd.dumpDatabase(database, target)){
            return Collections.singletonList(target);
        } else {
            return null;
        }
    }

    @Override
    public List<File> getDatabaseFileNames(Context ctx)
    {
        final File db = ctx.getDatabasePath("FilesDownloaderDatabase.db");
        final DumpDatabase dd = new DumpDatabase();
        return dd.getOfflineDatabaseFiles(db);
    }
    
    @Override
    public void registerEventListener(FilesDownloaderListener listener)
    {
        if (listener == null){
            throw new NullPointerException();
        }
        listeners.add(Pair.create(new Handler(), listener));
    }

    @Override
    public boolean unregisterEventListener(FilesDownloaderListener listener)
    {
        if (listener == null){
            return false;
        }
        
        boolean result = false;
        for (Pair<Handler, FilesDownloaderListener> client : listeners){
            if (listener.equals(client.second)){
                listeners.remove(client);
                result = true;
            }
        }
        return result;
    }
    
    synchronized void shutdownSelf()
    {
        boolean allTasksFinished = true;
        if (downloadTasks != null){
            for (FilesDownloadTask task : downloadTasks){
                synchronized(task){
                    if (task.filesDownloader != null){
                        allTasksFinished = false;
                        break;
                    }
                }
            }
        }
        if (allTasksFinished){
            super.stopForeground(false);

            // early recycle
            HttpClientFactory.closeHttpClient(httpClient);
            httpClient = null;
            
            boolean willBeStopped = false;
            for (Integer startId : startIds){
                willBeStopped = super.stopSelfResult(startId);
            }
            Log.i(LOG_TAG, "Called stopSelfResult(" + startIds + "), willStop=" + willBeStopped);
            startIds.clear();
        }
    }

    @Override
    public void onDestroy()
    {
        try{
            Log.i(LOG_TAG, "called onDestroy");
            if (downloadTasks != null){
                boolean anyTask = false;
                synchronized(this){
                    for (FilesDownloadTask task : downloadTasks){
                        synchronized(task){
                            if (task.filesDownloader != null){
                                anyTask = true;
                                Log.e(LOG_TAG, "Task id=" + task.taskId + " is still in progress!");
                                task.filesDownloader.abortDownload();
                            }
                        }
                    }
                }
                synchronized(this){
                    for (FilesDownloadTask task : downloadTasks){
                        final FilesDownloader fd;
                        synchronized(task){
                            fd = task.filesDownloader;
                        }
                        if (fd != null){
                            try{
                                fd.awaitTermination(60, TimeUnit.SECONDS);
                            }catch(InterruptedException ie){
                                
                            }
                        }
                    }
                }
                if (anyTask){
                    try{
                        Thread.sleep(50);
                    }catch(InterruptedException ie){
                        
                    }
                }
                synchronized(this){
                    for (FilesDownloadTask task : downloadTasks){
                        synchronized(task){
                            if (task.filesDownloader != null){
                                Log.e(LOG_TAG, "Task id=" + task.taskId + " did not respond to cancel signal!");
                            }
                        }
                    }
                }
            }
            HttpClientFactory.closeHttpClient(httpClient);
            httpClient = null;
            
            if (freeFilesDownloader != null){
                freeFilesDownloader.abortDownload();
            }
    
            if (downloadTasks != null){
                downloadTasks.clear();
                downloadTasks = null;
            }
            listeners.clear();
            filesOnHold.clear();
            
            databaseHelper.close();
            databaseHelper = null;
            database = null;
    
            //boundClientsCount = 0;
        }finally{
            super.onDestroy();
        }
    }
}
