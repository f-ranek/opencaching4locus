package org.bogus.domowygpx.services;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.http.ConnectionClosedException;
import org.apache.http.client.HttpClient;
import org.bogus.domowygpx.activities.DownloadListActivity;
import org.bogus.domowygpx.services.downloader.DownloadProgressMonitor;
import org.bogus.domowygpx.services.downloader.FileData;
import org.bogus.domowygpx.services.downloader.FilesDownloader;
import org.bogus.domowygpx.utils.HttpClientFactory;
import org.bogus.domowygpx.utils.HttpException;
import org.bogus.geocaching.egpx.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;

public class FilesDownloaderService extends Service implements FilesDownloaderApi
{
    private final static String LOG_TAG = "FilesDownloaderSvc";  

    private static final int NOTIFICATION_ID_ONGOING = 0x20;
    private static final int NOTIFICATION_ID_FINISHED = NOTIFICATION_ID_ONGOING+1;

    /**
     * Private action to start downloads job
     */
    public static final String INTENT_ACTION_START_DOWNLOAD = "org.bogus.domowygpx.FilesDownloaderService.START_DOWNLOAD_FILES";
    
    private static final String INTENT_EXTRA_TAKS_ID = "org.bogus.domowygpx.FilesDownloaderService.taskId";
    private static final String INTENT_EXTRA_RESTART_FAILED = "org.bogus.domowygpx.FilesDownloaderService.restartFailed";
    private static final String INTENT_EXTRA_INCLUDE_ALL = "org.bogus.domowygpx.FilesDownloaderService.includeAll";
    
    private ConcurrentMap<File, Boolean> filesOnHold = new ConcurrentHashMap<File, Boolean>(8);
    private FilesDownloader freeFilesDownloader;
    private final List<FilesDownloadTask> downloadTasks = new ArrayList<FilesDownloadTask>();
    
    public static class FilesDownloadTask implements Cloneable {
        public int taskId;
        
        public int state;
        public final static int STATE_RUNNING = 0;
        public final static int STATE_FINISHED = 1;
        public final static int STATE_CANCELLING = 2;
        public final static int STATE_CANCELLED = 3;
        public final static int STATE_PAUSING = 4;
        public final static int STATE_PAUSED = 5;
        
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

        public final static int FLAG_INTERNAL_PAUSE = 1; // TODO: internal-pause (due to the network outage)
        public final static int FLAG_NOTIFICATION_DONE = 2;
        public final static int FLAG_FIRST_FILE_STARTED = 4;
        
        
        public final long createdDate;
        
        public int totalFiles;
        public int totalFilesSizeKB;
        public int finishedFiles; 
        public int skippedFiles;
        public int permanentErrorFiles;
        public int transientErrorFiles;
        
        /** Flag indicating, that before task has paused, reasume operation was requested */
        boolean continueFromPausing;
        
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
        public void notifyFileStarted(FileData fileData)
        {
            FilesDownloaderService.this.onFileStarted(this, fileData);
        }
        
        @Override
        public void notifyFileSkipped(FileData fileData)
        {
            FilesDownloaderService.this.onFileSkipped(this, fileData);
        }
        
        @Override
        public void notifyFileProgress(FileData fileData, int doneKB, int totalKB)
        {
            FilesDownloaderService.this.onFileProgress(this, fileData, doneKB, totalKB);
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
            synchronized(task){
                task.filesDownloader.removeObserver(caller);
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
                    case FilesDownloadTask.STATE_CANCELLED:
                    case FilesDownloadTask.STATE_FINISHED:
                    case FilesDownloadTask.STATE_PAUSED:
                        // something is terribly wrong
                        newTaskState = -1;
                        break;
                    case FilesDownloadTask.STATE_PAUSING:
                        if (task.continueFromPausing){
                            newTaskState = FilesDownloadTask.STATE_RUNNING;
                            break;
                        } else {
                            // fall-through
                        }
                    case FilesDownloadTask.STATE_CANCELLING:
                    case FilesDownloadTask.STATE_RUNNING:
                        newTaskState = currentState+1;
                        break;
                    default:
                        newTaskState = -2;
                }
                if (currentState != FilesDownloadTask.STATE_PAUSING && task.continueFromPausing){
                    task.continueFromPausing = false;
                }
        
                if (newTaskState <= 0){
                    Log.e(LOG_TAG, "Fuck, some state is wrong, taskId=" + taskId + ", currentState=" + currentState);
                    return ;
                }
                if (!updateTask(taskId, task, newTaskState, currentState)){
                    Log.e(LOG_TAG, "Fuck, failed to update task, taskId=" + taskId + ", currentState=" + currentState + ", newTaskState=" + newTaskState);
                    return ;
                }
                
                if (newTaskState != FilesDownloadTask.STATE_RUNNING){
                    final FilesDownloadTask task2 = task.clone();
                    task2.filesDownloader = null;
                    for (final Pair<Handler, FilesDownloaderListener> listener : listeners){
                        final FilesDownloaderListener fdl = listener.second;
                        listener.first.post(new Runnable(){
                            @Override
                            public void run(){
                                switch(newTaskState){
                                    case FilesDownloadTask.STATE_PAUSED:
                                        fdl.onTaskPaused(task2);
                                        break;
                                    case FilesDownloadTask.STATE_CANCELLED:
                                        fdl.onTaskCancelled(task2);
                                        break;
                                    case FilesDownloadTask.STATE_FINISHED:
                                        fdl.onTaskFinished(task2);
                                        break;
                                }
                                
                            }
                        });
                    }
                }                
                //task.state = newTaskState;
                if (task.continueFromPausing){
                    task.continueFromPausing = false;
                    
                    task.filesDownloader = createFilesDownloader();
                    startTaskFromDatabase(task, false, false);
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

    void onFileProgress(DownloadProgressMonitorImpl caller, FileData fileData, final int doneKB, final int totalKB)
    {
        final FilesDownloadTask task = caller.downloadTask; 
        final FilesDownloadTask task2;
        synchronized(task){
            task.totalFilesSizeKB += doneKB;
            task2 = caller.downloadTask.clone();
        }
        task2.filesDownloader = null;
        final FileData fileData2 = fileData.clone();
        for (final Pair<Handler, FilesDownloaderListener> listener : listeners){
            final FilesDownloaderListener fdl = listener.second;
            listener.first.post(new Runnable(){
                @Override
                public void run(){
                    fdl.onFileProgress(task2, fileData2, doneKB, totalKB);
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
        final boolean isCancelled = taskState == FilesDownloadTask.STATE_CANCELLED || 
                taskState == FilesDownloadTask.STATE_CANCELLING;
        final boolean isOk = exception == null;
        final boolean isTransientError = !isCancelled && !isOk && isTransientError(exception);
        if (isOk){
            fileData.state = FileData.FILE_STATE_FINISHED;
        } else
        if (isCancelled){
            fileData.state = FileData.FILE_STATE_ABORTED;
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
        
        Log.i(LOG_TAG, "onFileFinished, file=" + fileData);
        
        updateFileInDatabase(fileData, true);
        // XXX update err/ok stats!!!!
        
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
            if(includeHeaders){
                Cursor cursor = database.query("files", new String[]{"headers"}, "_id=" + fileData.fileDataId, null, null, null, null);
                if (cursor.moveToFirst()){
                    if (!cursor.isNull(0)){
                        includeHeaders = false;
                    }
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
                cv.put("status_line", fileData.statusLine);
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
        if (freeFilesDownloader == null){
            fd = freeFilesDownloader;
            freeFilesDownloader = null;
            if (fd.isClosed()){
                fd = null;
            }
        }
        if (fd == null){
            fd = new FilesDownloader(httpClient, 1, filesOnHold);
        }
        return fd;
    }
    
    /**
     * Reads files to be downloaded for a given task, and schedules them for download
     * @param restartFailed reser retryCount
     * @param includePermanentlyFailed includes files with state=FileData.FILE_STATE_PERMANENT_ERROR
     * @param task
     */
    protected void startTaskFromDatabase(FilesDownloadTask task, 
        boolean restartFailed, 
        boolean includeAll) 
    {
        Cursor cursor = database.query("files", 
            new String[]{"_id", "state", "cache_code", "source", "target", "virtual_target", "priority", "retry_count", "headers"}, 
            "task_id = " + task.taskId + 
            (includeAll ? "" : " and state in (" + FileData.FILE_STATE_SCHEDULED + ", " + FileData.FILE_STATE_TRANSIENT_ERROR + ")"),  
            (String[])null, null, null, "_id");
        if (cursor.moveToFirst()){
            do{
                final FileData file = new FileData();
                file.fileDataId = cursor.getInt(0);
                try {
                    file.state = cursor.getInt(1);
                    file.cacheCode = cursor.getString(2);
                    file.source = new URI(cursor.getString(3));
                    file.target = new File(cursor.getString(4));
                    file.virtualTarget = cursor.getString(5);
                    if (cursor.isNull(6)){
                        file.priority = Integer.MAX_VALUE;
                    } else {
                        file.priority = cursor.getInt(6);
                    }
                    if (!restartFailed){
                        file.retryCount = cursor.getInt(7);
                    }
                    String headers = cursor.getString(8);
                    if (headers != null){
                        String[] headers2 = headers.split("[\n\r]+");
                        file.headers = new String[headers2.length][];
                        for (int i=0; i<headers2.length; i++){
                            String header = headers2[i];
                            int idx = header.indexOf(':');
                            String headerName = header.substring(0, idx);
                            String headerValue = header.substring(idx+1);
                            file.headers[i] = new String[]{headerName, headerValue};
                        }
                    }
                } catch (URISyntaxException e) {
                    Log.e(LOG_TAG, "Failed to read file, _id=" + file.fileDataId, e);
                }
                task.filesDownloader.submit(file);
            }while(cursor.moveToNext());
            
        }
        cursor.close();
    }
    
    /**
     * Iterates through the list of active tasks, and adjusts their downloader's thread pools
     */
    protected synchronized void adjustDownloaderThreads()
    {
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
        
        final int threadPool = Math.max(config.getInt("FilesDownloaderService_threadPoolSize", 4), 1);
        final boolean stopIfThreadPoolExhausted = config.getBoolean("FilesDownloaderService_stopIfThreadPoolExhausted", false);
        int threadsPerTask = threadPool / runningTasks;
        if (threadsPerTask == 0 && !stopIfThreadPoolExhausted){
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
        builder.setSmallIcon(R.drawable.logo_straszne);
        
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
        int runningCount = 0;
        int finishedCount = 0;
        int pausedCount = 0;
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
                    case FilesDownloadTask.STATE_PAUSED:
                        pausedCount++;
                        break;
                }
            }
        }
        
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setOngoing(runningCount != 0);
        if (runningCount > 0){
            builder.setContentTitle("Pobieram pliki"); 
        } else
        if (pausedCount > 0){
            builder.setContentTitle("Pobieranie wstrzymane"); 
        } else
        if (failedCount == finishedCount) {
            builder.setContentTitle("Błąd pobierania");
        } else {
            builder.setContentTitle("Pobieranie zakończone"); 
        }
        final StringBuilder sb = new StringBuilder();
        if (runningCount > 0){
            sb.append(runningCount);
            sb.append(" w trakcie"); 
        }
        if (finishedCount > 0){
            if (sb.length() > 0){
                sb.append(", "); 
            }
            sb.append(getResources().getQuantityString(R.plurals.finished, finishedCount, finishedCount));
        }
        if (failedCount > 0){
            if (sb.length() > 0){
                sb.append(", "); 
            }
            sb.append(failedCount).append(" błędnie"); 
        }
        if (withFailuresCount > 0){
            if (sb.length() > 0){
                sb.append(", "); 
            }
            sb.append(withFailuresCount).append(" z błędami"); 
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
    
    protected boolean updateTask(int taskId, FilesDownloadTask newTask, int newState, int expectedState)
    {
        ContentValues cv = new ContentValues(3);
        cv.put("state", newState);
        cv.put("total_files_size_kb", newTask.totalFilesSizeKB);
        cv.put("flags", newTask.flags);
        int rows = database.update("tasks", cv, "_id=" + taskId + " and state=" + expectedState, null);
        if (rows > 0){
            newTask.state = newState;
            Log.i(LOG_TAG, "Update taskId=" + taskId + ", newState=" + newState + ", oldState=" + expectedState);
        }
        return rows > 0;
    }
    
    private HttpClient httpClient;
    
    final List<Integer> startIds = new ArrayList<Integer>();
    
    /** Keeps track of all current registered clients. */
    private final List<Pair<Handler, FilesDownloaderListener>> listeners = new CopyOnWriteArrayList<Pair<Handler,FilesDownloaderListener>>(); 

    /** Binder exposed to clients */
    private final IBinder mBinder = new LocalBinder();
    //private int boundClientsCount = 0;
    
    public class LocalBinder extends Binder {
        public FilesDownloaderApi getService() 
        {
            return FilesDownloaderService.this;
        }
    } 
    
    @Override
    public /*synchronized*/ IBinder onBind(Intent intent)
    {
        //boundClientsCount++;
        //Log.i(LOG_TAG, "Client has bound, boundClientsCount=" + boundClientsCount);
        return mBinder;
    }

    /*@Override
    public synchronized void onRebind(Intent intent)
    {
        boundClientsCount++;
        Log.i(LOG_TAG, "Client has rebound, boundClientsCount=" + boundClientsCount);
    }
    
    @Override
    public synchronized boolean onUnbind(Intent intent)
    {
        boundClientsCount--;
        Log.i(LOG_TAG, "Client has unbound, boundClientsCount=" + boundClientsCount);
        shutdownSelf();
        return true;
    }*/

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
                    "total_files_size_kb INTEGER " +
                    ");"); 
            db.execSQL("CREATE TABLE files(" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "task_id INTEGER NOT NULL, " +
                    "state INTEGER NOT NULL DEFAULT 0, " +
                    "cache_code TEXT, " +
                    "source TEXT NOT NULL, " +
                    "target TEXT NOT NULL, " +
                    "virtual_target TEXT, " +
                    "priority INTEGER, " +
                    "retry_count INTEGER NOT NULL DEFAULT 0, " +
                    "status_line TEXT, " + 
                    "headers TEXT, " +
                    "exception TEXT " +
                    ");"); 
            db.execSQL("CREATE INDEX files_idx1 ON files(task_id);");
            /*db.execSQL("CREATE TABLE headers( " +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "file_id INTEGER NOT NULL, " +
                    "header TEXT NOT NULL, " +
                    "value TEXT NOT NULL);");
            db.execSQL("CREATE INDEX headers_idx1 ON headers(file_id);");*/
        }
     
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    protected DatabaseHelper databaseHelper;
    private SQLiteDatabase database;
    
    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) 
    {
        startIds.add(startId);
        Log.i(LOG_TAG, "called onStartCommand for startId=" + startId);

        try{
            if (intent == null || INTENT_ACTION_START_DOWNLOAD.equals(intent.getAction())){
                // null intent = redelivered intent
                int taskId = -1;
                if (intent != null){
                    taskId = intent.getIntExtra(INTENT_EXTRA_TAKS_ID, -1);
                }
                
                for (FilesDownloadTask task : downloadTasks){
                    synchronized(task){
                        if (task.state == FilesDownloadTask.STATE_RUNNING && (taskId == -1 || taskId == task.taskId)){
                            final FilesDownloader filesDownloader = createFilesDownloader();
                            task.filesDownloader = filesDownloader;
                            final boolean restartFailed = intent == null ? false : 
                                intent.getBooleanExtra(INTENT_EXTRA_RESTART_FAILED, false); 
                            final boolean includeAll = intent == null ? false : 
                                intent.getBooleanExtra(INTENT_EXTRA_INCLUDE_ALL, false);
                            startTaskFromDatabase(task, restartFailed, includeAll);
                        }
                    }
                }
                adjustDownloaderThreads();
            } else
            if (INTENT_ACTION_SCHEDULE_FILES.equals(intent.getAction())){
                final FileData[] files = (FileData[])intent.getParcelableArrayExtra(INTENT_EXTRA_FILES);
                if (files == null){
                    Log.e(LOG_TAG, "Missing " + INTENT_EXTRA_FILES);
                    return Service.START_STICKY;
                }                
                if (files.length == 0){
                    Log.e(LOG_TAG, "Empty " + INTENT_EXTRA_FILES);
                    return Service.START_STICKY;
                }
                int taskId = scheduleFiles(Arrays.asList(files));
                if (taskId >= 0){
                    final Messenger messenger = intent.getExtras().getParcelable(INTENT_EXTRA_MESSENGER);
                    if (messenger != null){
                        try{
                            messenger.send(Message.obtain(null, 0, taskId, 0));
                        }catch(RemoteException re){
                            
                        }
                    }
                }
            }
        }finally{
            shutdownSelf();
        }

        return Service.START_STICKY;
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
        insertValues.put("virtual_target", fileData.virtualTarget);
        insertValues.put("priority", fileData.priority);
        int fileDataId =  (int)database.insert("files", null, insertValues);
        if (fileDataId == -1){
            throw new SQLiteException("Failed to insert file to DB");
        }
        fileData.fileDataId = fileDataId;
    }
    
    @Override
    public synchronized void onCreate()
    {
        super.onCreate();
        httpClient = HttpClientFactory.createHttpClient(true, this);
        
        try{
            databaseHelper = new DatabaseHelper(this, "FilesDownloaderDatabase.db", null, 1);
            database = databaseHelper.getWritableDatabase();
        }catch(SQLiteException sqle){
            Log.e(LOG_TAG, "Failed to create database", sqle);
            throw sqle;
        }   
        
        cleanupDatabase();
        loadTasks();
    }

    private void cleanupDatabase()
    {
        database.beginTransaction();
        try{
            database.execSQL("update files set retry_count=retry_count+1, state=0 where state=" + FileData.FILE_STATE_RUNNING);
            Cursor cursor = database.rawQuery("select changes()", null);
            if (cursor.moveToFirst()){
                int changes = cursor.getInt(0);
                if (changes > 0){
                    Log.w(LOG_TAG, "Updated #" + changes + " files with state=FILE_STATE_RUNNING");
                }
            }
            cursor.close();

            database.execSQL("update tasks set state=case when " + 
                    FilesDownloadTask.STATE_CANCELLING + " then " + FilesDownloadTask.STATE_CANCELLED + 
                    " when " + FilesDownloadTask.STATE_PAUSING + " then " + FilesDownloadTask.STATE_PAUSED +
                    " end where state in (" + FilesDownloadTask.STATE_CANCELLING + ", " + FilesDownloadTask.STATE_PAUSING+ ")");
            cursor = database.rawQuery("select changes()", null);
            if (cursor.moveToFirst()){
                int changes = cursor.getInt(0);
                if (changes > 0){
                    Log.w(LOG_TAG, "Updated #" + changes + " tasks with transient state");
                }
            }
            cursor.close();
            
            database.execSQL("update tasks set state=" + 
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
            cursor.close();
            
            // TODO: remove old, finished or cancelled tasks
            
            database.setTransactionSuccessful();
        }finally{
            database.endTransaction();
        }
    }
    
    private void loadTasks()
    {
        database.beginTransaction();
        try{
            Cursor tasks = database.query("tasks", 
                new String[]{"_id", "created_date", "state", "total_files_size_kb", "flags"}, 
                null, null, null, null, "created_date");
            if (tasks.moveToFirst()){
                do{
                    FilesDownloadTask task = new FilesDownloadTask(tasks.getLong(1));
                    task.taskId = tasks.getInt(0);
                    task.state = tasks.getInt(2);
                    task.totalFilesSizeKB = tasks.getInt(3);
                    task.flags = tasks.getInt(4);
                    
                    Cursor stats = database.rawQuery("select state, count(1) from files where task_id=" + task.taskId + " group by state", null);
                    if (stats.moveToFirst()){
                        do{
                            int status = stats.getInt(0);
                            int count = stats.getInt(1);
                            task.totalFiles += count;
                            switch(status){
                                case FileData.FILE_STATE_FINISHED:
                                    task.finishedFiles++;
                                    break;
                                case FileData.FILE_STATE_SKIPPED:
                                    task.skippedFiles++;
                                    break;
                                case FileData.FILE_STATE_PERMANENT_ERROR:
                                    task.permanentErrorFiles++;
                                    break;
                                case FileData.FILE_STATE_TRANSIENT_ERROR:
                                    task.transientErrorFiles++;
                                    break;
                            }
                        }while(stats.moveToNext());
                    }
                    stats.close();
                    downloadTasks.add(task);
                }while(tasks.moveToNext());
            }
            tasks.close();
            database.setTransactionSuccessful();
        }finally{
            database.endTransaction();
        }
    }
    
    protected synchronized FilesDownloadTask getTaskById(int taskId)
    {
        for (FilesDownloadTask task : downloadTasks){
            if (task.taskId == taskId){
                return task;
            }
        }
        return null;
    }

    @Override
    public int scheduleFiles(final List<FileData> filesToDownload)
    throws IllegalArgumentException
    {
        if (filesToDownload.isEmpty()){
            throw new IllegalArgumentException("filesToDownload is empty");
        }
        
        final FilesDownloadTask task;
        database.beginTransaction();
        try{
            task = createTask();
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
        intent.putExtra("taskId", task.taskId);
        startService(intent);
        
        Log.i(LOG_TAG, "Starting taskId=" + task.taskId);
        
        return task.taskId;
    }
    
    @Override
    public boolean pauseTask(int taskId)
    {
        final FilesDownloadTask task = getTaskById(taskId);
        if (task == null){
            return false;
        }
        synchronized(task){
            if (task.state == FilesDownloadTask.STATE_RUNNING){
                boolean result = updateTask(task.taskId, task,  
                    FilesDownloadTask.STATE_PAUSING, FilesDownloadTask.STATE_RUNNING);
                if (result){
                    task.filesDownloader.stopDownload();
                }
                return result;
            }
        }
        return false;
    }

    @Override
    public boolean resumeTask(int taskId)
    {
        final FilesDownloadTask task = getTaskById(taskId);
        if (task == null){
            return false;
        }
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
            boolean result = updateTask(task.taskId, task,  
                FilesDownloadTask.STATE_RUNNING, task.state);
            if (result){
                final Intent intent = new Intent(INTENT_ACTION_START_DOWNLOAD, null, this, FilesDownloaderService.class);
                intent.putExtra(INTENT_EXTRA_TAKS_ID, task.taskId);
                startService(intent);
            }
            return result;
        }
    }

    @Override
    public boolean restartTask(int taskId)
    {
        final FilesDownloadTask task = getTaskById(taskId);
        if (task == null){
            return false;
        }
        synchronized(task){
            if (task.state != FilesDownloadTask.STATE_FINISHED){
                return false;
            }
            if (task.skippedFiles + task.finishedFiles >= task.totalFiles){
                return false;
            }
            int totalFilesSizeKB = task.totalFilesSizeKB;
            task.totalFilesSizeKB = 0;
            boolean result = updateTask(task.taskId, task, 
                FilesDownloadTask.STATE_RUNNING, task.state);
            if (result){
                task.state = FilesDownloadTask.STATE_RUNNING;
                final Intent intent = new Intent(INTENT_ACTION_START_DOWNLOAD, null, this, FilesDownloaderService.class);
                intent.putExtra(INTENT_EXTRA_TAKS_ID, task.taskId);
                intent.putExtra(INTENT_EXTRA_RESTART_FAILED, true);
                intent.putExtra(INTENT_EXTRA_INCLUDE_ALL, true);
                startService(intent);
            } else {
                task.totalFilesSizeKB = totalFilesSizeKB;
            }
            return result;
        }
    }
    
    
    @Override
    public boolean cancelTask(int taskId)
    {
        final FilesDownloadTask task = getTaskById(taskId);
        if (task == null){
            return false;
        }
        synchronized(task){
            if (task.state == FilesDownloadTask.STATE_RUNNING || 
                task.state == FilesDownloadTask.STATE_PAUSING || 
                task.state == FilesDownloadTask.STATE_PAUSED)
            {
                boolean result = updateTask(task.taskId, task, 
                    FilesDownloadTask.STATE_CANCELLING, task.state);
                if (result){
                    task.filesDownloader.abortDownload();
                }
                return result;
            }
            return false;
        }
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
                task.state == FilesDownloadTask.STATE_CANCELLED || 
                task.state == FilesDownloadTask.STATE_PAUSED)
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
                    if (listener.first.getLooper() == Looper.getMainLooper()){
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
        List<FilesDownloadTask> result = new ArrayList<FilesDownloadTask>(downloadTasks.size());
        for (FilesDownloadTask task : downloadTasks){
            FilesDownloadTask task2 = task.clone();
            task2.filesDownloader = null;
            result.add(task2);
        }
        return result;
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
        for (FilesDownloadTask task : downloadTasks){
            synchronized(task){
                if (task.filesDownloader != null){
                    allTasksFinished = false;
                    break;
                }
            }
        }
        if (allTasksFinished){
            super.stopForeground(false);
        }
        if (allTasksFinished /*&& boundClientsCount == 0*/){
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

            HttpClientFactory.closeHttpClient(httpClient);
            httpClient = null;
            
            if (freeFilesDownloader != null){
                freeFilesDownloader.abortDownload();
            }
    
            downloadTasks.clear();
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
