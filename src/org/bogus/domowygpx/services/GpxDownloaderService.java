package org.bogus.domowygpx.services;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import locus.api.android.ActionFiles;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.bogus.ToStringBuilder;
import org.bogus.domowygpx.activities.DownloadListActivity;
import org.bogus.domowygpx.activities.TaskConfiguration;
import org.bogus.domowygpx.apache.http.client.utils.ResponseUtils;
import org.bogus.domowygpx.application.Application;
import org.bogus.domowygpx.gpx.GpxProcessMonitor;
import org.bogus.domowygpx.gpx.GpxProcessor;
import org.bogus.domowygpx.html.HTMLProcessor;
import org.bogus.domowygpx.html.ImageUrlProcessor;
import org.bogus.domowygpx.oauth.OKAPI;
import org.bogus.domowygpx.services.downloader.FileData;
import org.bogus.domowygpx.utils.DumpDatabase;
import org.bogus.domowygpx.utils.HttpClientFactory;
import org.bogus.domowygpx.utils.HttpClientFactory.CreateHttpClientConfig;
import org.bogus.domowygpx.utils.HttpException;
import org.bogus.domowygpx.utils.InputStreamHolder;
import org.bogus.geocaching.egpx.BuildConfig;
import org.bogus.geocaching.egpx.R;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;

public class GpxDownloaderService extends Service implements GpxDownloaderApi
{
    private final static String LOG_TAG = "GpxDownloaderSvc";

    private static final int NOTIFICATION_ID_ONGOING = 0x10;
    private static final int NOTIFICATION_ID_FINISHED = NOTIFICATION_ID_ONGOING+1;
    
    private HttpClient httpClient;
    
    synchronized HttpClient getHttpClient(){
        if (httpClient == null){
            final CreateHttpClientConfig ccc = new CreateHttpClientConfig(this);
            ccc.authorizeRequests = true;
            ccc.shared = true;
            ccc.preventCaching = true;
            final HttpClient aHttpClient = HttpClientFactory.createHttpClient(ccc);
            // aHttpClient.getParams().setIntParameter(HttpClientFactory.RAW_SOCKET_RECEIVE_BUFFER_SIZE, 32*1024);
            httpClient = aHttpClient;
        }
        return httpClient;        
    }
    
    private synchronized void closeHttpClient()
    {
        HttpClientFactory.closeHttpClient(httpClient);
        httpClient = null;
    }
    
    final static AtomicInteger threadIndexCount = new AtomicInteger();
    
    private DatabaseHelper databaseHelper;
    SQLiteDatabase database;
    
    volatile FilesDownloaderApi filesDownloader;
    volatile boolean filesDownloaderBound;
    final Semaphore filesDownloaderSemaphore = new Semaphore(0);
    
    protected FilesDownloaderApi getDownloaderService()
    {
        synchronized(filesDownloaderServiceConnection){
            if (filesDownloader == null){
                bindService(new Intent(this, FilesDownloaderService.class), filesDownloaderServiceConnection, Context.BIND_AUTO_CREATE);
                filesDownloaderSemaphore.acquireUninterruptibly();
            }
            return filesDownloader;
        }
    }
    
    private final ServiceConnection filesDownloaderServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(LOG_TAG, "Established connection to " + className.getShortClassName());
            filesDownloaderBound = true;
            filesDownloader = ((FilesDownloaderService.LocalBinder)service).getService();
            filesDownloaderSemaphore.release();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            filesDownloader = null;
            Log.w(LOG_TAG, "Lost connection to " + className.getShortClassName());
        }
    };
    
    
    public static class GpxTask implements Cloneable {
        public static final int STATE_RUNNING = 0;
        public static final int STATE_DONE = GpxTaskEvent.EVENT_TYPE_FINISHED_OK;
        public static final int STATE_CANCELED = GpxTaskEvent.EVENT_TYPE_FINISHED_CANCEL;
        public static final int STATE_ERROR = GpxTaskEvent.EVENT_TYPE_FINISHED_ERROR;
        public static final int STATE_UNKNOWN = 0x80;
        
        public final int taskId;
        public final long createdDate;

        public GpxTask(int taskId, long createdDate)
        {
            this.taskId = taskId;
            this.createdDate = createdDate;
            //this.stateDescription = "Uruchamiam";
        }

        public int stateCode;
        public String stateDescription;
        public String currentCacheCode;
        public String currentCacheName;
        public int totalKB;
        public int expectedTotalKB;
        public int totalCacheCount;
        
        /** Events attached only if attachEvents was true in call to {@link GpxDownloaderApi#getTasks(int, boolean)} */
        public List<GpxTaskEvent> events;
        
        public int downloaderTaskId;
        public String imageFileStats;
        
        Exception exception;
        public String exceptionString;
        
        public String taskDescription;
        
        volatile GpxTaskEvent lastTaskEvent;
        
        @Override
        public GpxTask clone()
        {
            try{
                GpxTask task = (GpxTask)super.clone();
                task.exception = null;
                task.lastTaskEvent = null;
                return task;
            }catch(CloneNotSupportedException cnse){
                throw new IllegalStateException(cnse);
            }
        }
        
        GpxTaskEvent createTaskEvent()
        {
            return new GpxTaskEvent(taskId, System.currentTimeMillis());
        }

        @Override
        public String toString()
        {
            if (BuildConfig.DEBUG){
                ToStringBuilder builder = new ToStringBuilder(this);
                builder.add("taskId", taskId);
                builder.add("createdDate", new Date(createdDate));
                switch(stateCode){
                    case STATE_RUNNING: builder.add("stateCode", "STATE_RUNNING"); break; 
                    case STATE_DONE: builder.add("stateCode", "STATE_DONE"); break;
                    case STATE_CANCELED: builder.add("stateCode", "STATE_CANCELED"); break;
                    case STATE_ERROR: builder.add("stateCode", "STATE_ERROR"); break;
                    case STATE_UNKNOWN: builder.add("stateCode", "STATE_UNKNOWN"); break;
                    default: builder.add("stateCode", stateCode); break;
                }
    
                builder.add("stateDescription", stateDescription);
                builder.add("currentCacheCode", currentCacheCode);
                builder.add("totalKB", totalKB);
                builder.add("expectedTotalKB", expectedTotalKB);
                builder.add("totalCacheCount", totalCacheCount);
                builder.add("downloaderTaskId", downloaderTaskId, 0);
                builder.add("imageFileStats", imageFileStats); 
                builder.add("exception", exception);
                return builder.toString();
            } else {
                return super.toString();
            }
        }
    }
    
    public static class GpxTaskEvent implements Cloneable {
        public static final int EVENT_TYPE_LOG = 1;
        public static final int EVENT_TYPE_WARN = 2;
        public static final int EVENT_TYPE_ERROR = 3;
        public static final int EVENT_TYPE_FINISHED_OK = 4;
        public static final int EVENT_TYPE_FINISHED_ERROR = 5;
        public static final int EVENT_TYPE_FINISHED_CANCEL = 6;
        public static final int EVENT_TYPE_CACHE_CODE = 7;

        GpxTaskEvent(int taskId, long createdDate)
        {
            this.taskId = taskId;
            this.createdDate = createdDate;
        }
        
        public int eventId;
        public final long createdDate;
        public final int taskId;
        
        public int eventType;
        public String description;
        public String currentCacheCode;
        public String currentCacheName;
        public int totalKB;

        @Override
        public String toString()
        {
            if (BuildConfig.DEBUG){
                ToStringBuilder builder = new ToStringBuilder(this);
                builder.add("eventId", eventId, 0);
                builder.add("taskId", taskId);
                switch(eventType){
                    case EVENT_TYPE_LOG : builder.add("eventType", "LOG"); break;
                    case EVENT_TYPE_WARN : builder.add("eventType", "WARN"); break;
                    case EVENT_TYPE_ERROR : builder.add("eventType", "ERROR"); break;
                    case EVENT_TYPE_FINISHED_OK : builder.add("eventType", "FINISHED_OK"); break;
                    case EVENT_TYPE_FINISHED_ERROR : builder.add("eventType", "FINISHED_ERROR"); break;
                    case EVENT_TYPE_FINISHED_CANCEL : builder.add("eventType", "FINISHED_CANCEL"); break;
                    case EVENT_TYPE_CACHE_CODE : builder.add("eventType", "CACHE_CODE"); break;
                    default: builder.add("eventType", eventType); break;
                }            
                builder.add("description", description);
                builder.add("currentCacheCode", currentCacheCode);
                builder.add("totalKB", totalKB);
                return builder.toString();
            } else {
                return super.toString();
            }
        }
        
        @Override
        public GpxTaskEvent clone()
        {
            try{
                return (GpxTaskEvent)super.clone();
            }catch(CloneNotSupportedException cnse){
                throw new IllegalStateException(cnse);
            }
        }
    }
    

    OKAPI okApi;
    
    class WorkerThread extends Thread implements GpxProcessMonitor 
    {
        final TaskConfiguration taskConfig;
        final GpxTask taskState;
        private boolean hasErrorDescription;
        private List<File> touchedFiles;
        volatile HttpUriRequest currentRequest;
        volatile boolean interruptionFlag;
        volatile HttpResponse mainResponse;
        HttpClient httpClient;
        
        public WorkerThread(TaskConfiguration taskConfiguration, GpxTask taskState)
        {
            super("gpx-downloader-thread-" + threadIndexCount.getAndIncrement());
            this.taskConfig = taskConfiguration;
            this.taskState = taskState;
        }
        
        
        
        protected void appendSearchParameters(StringBuilder query, String userUuid) 
        throws JSONException
        {
            query.append("search_method=services/caches/search/nearest");
            query.append("&search_params=");

            JSONObject searchParams = new JSONObject();
            NumberFormat nf = new DecimalFormat("##0.########", new DecimalFormatSymbols(Locale.US));
            searchParams.put("center", nf.format(taskConfig.getOutLatitude()) + "|" + nf.format(taskConfig.getOutLongitude()));
            if (taskConfig.getOutMaxNumOfCaches() > 0){
                searchParams.put("limit", taskConfig.getOutMaxNumOfCaches());
            } else {
                searchParams.put("limit", 500); // maximum allowed
            }
            if (taskConfig.getOutMaxCacheDistance() > 0){
                searchParams.put("radius", taskConfig.getOutMaxCacheDistance());
            }
            if (taskConfig.getCacheTypes() != null){
                searchParams.put("type", taskConfig.getCacheTypes());
            }
            if (taskConfig.getCacheTaskDifficulty() != null){
                searchParams.put("difficulty", taskConfig.getCacheTaskDifficulty());
            }
            if (taskConfig.getCacheTerrainDifficulty() != null){
                searchParams.put("terrain", taskConfig.getCacheTerrainDifficulty());
            }
            if (taskConfig.getCacheRatings() != null){
                searchParams.put("rating", taskConfig.getCacheRatings());
            }
            if (taskConfig.getCacheRecommendations() != null){
                searchParams.put("min_rcmds", taskConfig.getCacheRecommendations());
            }
            SharedPreferences config = getSharedPreferences("egpx", Context.MODE_PRIVATE);
            if (userUuid != null){
                if (TaskConfiguration.FOUND_STRATEGY_SKIP.equals(config.getString("foundStrategy", TaskConfiguration.FOUND_STRATEGY_MARK))){
                    searchParams.put("not_found_by", userUuid);
                }
                if (config.getBoolean("excludeMyOwn", true)){
                    searchParams.put("owner_uuid", "-" + userUuid);
                }
            }
            final boolean hasOAuth3 = okApi.getOAuth().hasOAuth3();
            if (hasOAuth3 && config.getBoolean("excludeIgnored", true)){
                searchParams.put("exclude_ignored", "true");
            }
            if (hasOAuth3 && config.getBoolean("userCoordsAsDefault", true)){
                searchParams.put("location_source", "alt_wpt:user-coords");
            }
            
            searchParams.put("status", "Available");
            query.append(urlEncode(searchParams));
        }
        
        protected void appendReturnParameters(StringBuilder query, String userUUID) 
        throws JSONException
        {
            final SharedPreferences config = getSharedPreferences("egpx", Context.MODE_PRIVATE);
            final boolean searchAndRetrieve = true;
            JSONObject retrParams = new JSONObject();
            
            retrParams.put("langpref","pl|en"); // XXX configurable!
            retrParams.put("ns_ground", "true");
            retrParams.put("ns_gsak", "true");
            retrParams.put("ns_ox", "true");
            retrParams.put("ns_gsak", "true");
            retrParams.put("images", "descrefs:all");
            if (config.getBoolean("listTrackables", true)){
                retrParams.put("trackables", "desc:list");
            }
            retrParams.put("recommendations", "desc:count");
            retrParams.put("attrs", "gc:attrs|desc:text|gc_ocde:attrs"); // do I need ox:tags ?
            retrParams.put("alt_wpts", "true");
            
            final boolean hasOAuth3 = okApi.getOAuth().hasOAuth3();
            if (hasOAuth3){
                retrParams.put("my_notes", config.getString("myNotes", "desc:text|gc:personal_note"));
            }
            if (hasOAuth3 && config.getBoolean("userCoordsAsDefault", true)){
                retrParams.put("location_source", "alt_wpt:user-coords");
                retrParams.put("location_change_prefix", "[F] ");
            }
            
            int maxCacheLogs = config.getInt("limitCacheLogs", 20);
            
            if (maxCacheLogs != 0){
                retrParams.put("latest_logs", "true");
                if (maxCacheLogs > 0){
                    retrParams.put("lpc", String.valueOf(maxCacheLogs));
                } else {
                    retrParams.put("lpc", "all");
                } 
            }
                
            if (userUUID != null && TaskConfiguration.FOUND_STRATEGY_MARK.equals(config.getString("foundStrategy", TaskConfiguration.FOUND_STRATEGY_MARK))){
                if (!okApi.getOAuth().hasOAuth3()){
                    retrParams.put("user_uuid", userUUID);
                }
                retrParams.put("mark_found", "true");
            }

            if (searchAndRetrieve){
                query.append("&retr_method=services/caches/formatters/gpx");
                query.append("&retr_params=");
                query.append(urlEncode(retrParams));
            } else {
                @SuppressWarnings("unchecked")
                Iterator<String> keyIt = retrParams.keys();
                while (keyIt.hasNext()){
                    String key = keyIt.next();
                    Object val = retrParams.opt(key);
                    query.append(key).append('=');
                    query.append(urlEncode(val)).append('&');
                }
            }
            query.append("&wrap=false");
        }
        
        @Override
        public void run()
        {
            try{
                this.httpClient = getHttpClient();
                Log.i(LOG_TAG, "START");
                run0();
            }finally{
                httpClient = null;
                touchedFiles = null;;
                currentRequest = null;
                mainResponse = null;
                GpxDownloaderService.this.workFinished(WorkerThread.this);
                Log.i(LOG_TAG, "END");
            }
        }
        
        private String getUserUuid() 
        throws Exception
        {
            final Resources res = getResources();
            final SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
            String userUuid = config.getString("userUuid", null);
            if (userUuid != null){
                return userUuid;
            }
            
            String userName = config.getString("userName", null);
            final boolean hasOAuth3 = okApi.getOAuth().hasOAuth3();
            
            HttpResponse resp = null;
            try{
                if (hasOAuth3){
                    String url = okApi.getAPIUrl() + 
                            "services/users/user?fields=uuid%7Cusername";
                    Log.v(LOG_TAG, url);
                    final HttpGet get = new HttpGet(url);
                    currentRequest = get;
                    resp = httpClient.execute(get);
                    final JSONObject obj = (JSONObject)ResponseUtils.getJSONObjectFromResponse(LOG_TAG, get, resp);
                    userUuid = (String)obj.opt("uuid");
                    userName = (String)obj.opt("username"); 
                    if (userUuid == null || userUuid.length() == 0){
                        throw new IllegalStateException(res.getString(R.string.gpx_downloader_failed_to_get_user));
                    } 
                    Editor editor = config.edit();
                    editor.putString("userName", userName);
                    editor.putString("userUuid", userUuid);
                    editor.putString("userUUID:" + userName, userUuid);
                    editor.commit();
                   
                    return userUuid;
                } else {
                    if (userName == null){
                        return null;
                    }
                    final String key = "userUUID:" + userName;
                    userUuid = config.getString(key, null);
                    if (userUuid == null){
                        String url = okApi.getAPIUrl() + 
                                "services/users/by_username?username="
                                + urlEncode(userName) + 
                                "&fields=uuid";
                        Log.v(LOG_TAG, url);
                        final HttpGet get = new HttpGet(url);
                        currentRequest = get;
                        resp = httpClient.execute(get);
                        final JSONObject obj = (JSONObject)ResponseUtils.getJSONObjectFromResponse(LOG_TAG, get, resp);
                        userUuid = (String)obj.opt("uuid");
                        if (userUuid == null){
                            sendWarnErrorInfo(res.getString(R.string.gpx_downloader_no_such_user, userName), GpxTaskEvent.EVENT_TYPE_WARN); 
                        } else {
                            config.edit().putString(key, userUuid).commit();
                        }
                    }
                    return userUuid;
                }
            }catch(Exception e){
                Log.e(LOG_TAG, "Failed to get user UUID for userName=" + userName);
                // setErrorDescription("Nie udało się pobrać danych użytkownika", e);
                throw e;
            }finally{
                ResponseUtils.closeResponse(resp);
                currentRequest = null;
            }
        }
        
        private void checkInterrupted()
        throws InterruptedException
        {
            if (interruptionFlag || Thread.interrupted()){
                throw new InterruptedException();
            }
        }
        
        private boolean processDefaultException(Exception e)
        {
            final Resources res = getResources();
            if (e instanceof org.apache.http.conn.ConnectionPoolTimeoutException){
                setErrorDescription(res.getString(R.string.gpx_downloader_too_many_connections), e); 
                return true;
            }
            if (e instanceof java.net.SocketTimeoutException ||
                    e instanceof org.apache.http.conn.ConnectTimeoutException
            ){
                setErrorDescription(res.getString(R.string.gpx_downloader_data_timeout), e); 
                return true;
            }
            if (e instanceof java.net.SocketException || 
                    e instanceof java.net.UnknownHostException ||
                    e instanceof java.net.SocketTimeoutException ||
                    e instanceof org.apache.http.client.ClientProtocolException ||
                    e instanceof org.apache.http.ConnectionClosedException)
            {
                setErrorDescription(res.getString(R.string.gpx_downloader_network_error), e); 
                return true;
            }
            if (e instanceof javax.net.ssl.SSLException){
                setErrorDescription(res.getString(R.string.gpx_downloader_ssl_error), e); 
                return true;
            }
            if (e instanceof HttpException){
                HttpException he = (HttpException)e;
                if (he.isCustomErrorCode()){
                    setErrorDescription(he.httpMessage, e);
                    return true;
                }
                if (he.httpCode == 400){
                    setErrorDescription(res.getString(R.string.gpx_downloader_server_request_error), e); 
                    return true;
                }
                if (he.httpCode == 401 && okApi.getOAuth().hasOAuth3()){
                    setErrorDescription(res.getString(R.string.gpx_downloader_login_error), e); 
                    return true;
                }
            }   
            if (e instanceof HttpException || 
                    e instanceof FileNotFoundException)
            {
                setErrorDescription(res.getString(R.string.gpx_downloader_server_error), e); 
                return true;
            }
            return false;
        }
        
        private void run0()
        {
            Log.i(LOG_TAG, taskConfig.toString());
            
            updateNotification();
            
            OutputStream os = null;
            InputStream is = null;
            GpxProcessor gpxProcessor = null;

            final Resources res = getResources();
            
            final InputStreamHolder mis = new InputStreamHolder();
            // cos = new CountingInputStream(mis);
            touchedFiles = new ArrayList<File>();
            try{
                final SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
                sendProgressInfo(res.getString(R.string.gpx_downloader_progress_init)); 
                final String userUuid = getUserUuid();
                
                touchedFiles.add(taskConfig.getOutTargetFileName());
                
                // prepare directories and files
                gpxProcessor = new GpxProcessor();
                gpxProcessor.setTempDir(GpxDownloaderService.this.getCacheDir());
                gpxProcessor.setDestFileBaseName(taskConfig.getOutTargetFileName());
                gpxProcessor.setSourceStream(mis);
                gpxProcessor.setGpxProcessMonitor(this);
                
                try{
                    gpxProcessor.preCreateOutputStream();
                }catch(IOException e){
                    setErrorDescription(res.getString(R.string.gpx_downloader_error_file_creation), e); 
                    throw e;
                }
                
                final File targetDirName = taskConfig.getOutTargetDirName();
                final String virtualLocation = targetDirName.toURI().toASCIIString();
                final ImageUrlProcessor imageUrlProcessor = new ImageUrlProcessor(gpxProcessor, targetDirName, virtualLocation);
                imageUrlProcessor.setDownloadImages(taskConfig.isOutDownloadImages());
                imageUrlProcessor.setUseExistingImages(config.getBoolean("useExistingImages", true));
                final HTMLProcessor htmlProcessor = new HTMLProcessor(imageUrlProcessor);
                gpxProcessor.setHtmlProcessor(htmlProcessor);

                checkInterrupted();
                
                HttpResponse resp = null;
                try{
                    StringBuilder requestURL = new StringBuilder(256);
                    requestURL.append(okApi.getAPIUrl());
                    
                    requestURL.append("services/caches/shortcuts/search_and_retrieve?");
                    appendSearchParameters(requestURL, userUuid);
                    appendReturnParameters(requestURL, userUuid);
                    
                    // execute requestURL and process response
                    sendProgressInfo(res.getString(R.string.gpx_downloader_progress_search)); 
                    final String url = requestURL.toString();
                    Log.i(LOG_TAG, url);
                    final HttpGet get = new HttpGet(url);
                    currentRequest = get;
                    mainResponse = httpClient.execute(get);
                    taskState.expectedTotalKB = (int)(ResponseUtils.getContentLength(mainResponse) / 1024L);
                    final StatusLine statusLine = mainResponse.getStatusLine();
                    final int statusCode = statusLine.getStatusCode();
                    if (statusCode == 404 || statusCode == 204){
                        ResponseUtils.logResponseContent(LOG_TAG, mainResponse);
                        throw new FileNotFoundException("Got " + statusCode + " for " + url);
                    } else if (statusCode != 200){
                        ResponseUtils.logResponseContent(LOG_TAG, mainResponse);
                        throw HttpException.fromHttpResponse(mainResponse, get);
                    }
                    
                }catch(Exception e){
                    ResponseUtils.abortResponse(resp);
                    if (!processDefaultException(e)){
                        setErrorDescription(res.getString(R.string.gpx_downloader_error_search), e); 
                    }
                    throw e;
                }finally{
                    ResponseUtils.closeResponse(resp);
                }
                
                checkInterrupted();
                
                // process main response
                is = mainResponse.getEntity().getContent();
                try{
                    // branch source file for debug purposes
                    String fileName = "gpx_" + taskState.taskId + "_" + System.currentTimeMillis() + ".xml.gz";
                    os = new FileOutputStream(new File(GpxDownloaderService.this.getCacheDir(), fileName));
                    os = new BufferedOutputStream(os, 4096);
                    os = new GZIPOutputStream(os, 4096);
                    final TeeInputStream tee = new TeeInputStream(is, os);
                    mis.setInputStream(tee);
                }catch(Exception e){   
                    setErrorDescription(res.getString(R.string.gpx_downloader_error_fs), e); 
                    throw e;
                }
                
                checkInterrupted();
                
                try{
                    setPriority(Thread.NORM_PRIORITY-1);
                    sendProgressInfo(res.getString(R.string.gpx_downloader_progress_downloading));
                    gpxProcessor.processGpx();
                    
                    onStartedCacheCode(null, null);
                    
                }catch(Exception e){   
                    if (!processDefaultException(e)) {
                        setErrorDescription(res.getString(R.string.gpx_downloader_error_invalid_gpx), e); 
                    }
                    throw e;
                }finally{
                    setPriority(Thread.NORM_PRIORITY);
                }
                
                IOUtils.closeQuietly(is);
                is = null;
                IOUtils.closeQuietly(mis);
                os.flush();
                IOUtils.closeQuietly(os);
                os = null;
                
                invokeLocus();
                
                invokeImagesDownloader(imageUrlProcessor);                    
                
                finishTaskOk();
            }catch(final Exception exception){
                ResponseUtils.abortResponse(mainResponse);
                Log.e(LOG_TAG, "Failed to download, location=" 
                    + taskConfig.getOutLatitude() + ":" + taskConfig.getOutLongitude() 
                    + ", file=" + taskConfig.getOutTargetFileName() 
                    + ", dir=" + taskConfig.getOutTargetDirName()
                    , exception);
                
                if (taskState.exception == null){
                    taskState.exception = exception;
                }
                finishTaskWithError(exception, interruptionFlag);
                try{
                    if (taskConfig.getOutTargetFileName().exists() && taskConfig.getOutTargetFileName().length() == 0){
                        taskConfig.getOutTargetFileName().delete();
                    }
                }catch(Exception e){
                    // ignore
                }
            }finally{
                currentRequest = null;
                IOUtils.closeQuietly(gpxProcessor);
                IOUtils.closeQuietly(is);
                IOUtils.closeQuietly(mis);
                IOUtils.closeQuietly(os);
                ResponseUtils.closeResponse(mainResponse);
                // IOUtils.closeQuietly(cos);
                touchFiles();
            }
        }

        protected void invokeImagesDownloader(final ImageUrlProcessor imageUrlProcessor) throws Exception
        {
            try{
                final List<FileData> foundImages = imageUrlProcessor.getDataFiles();
                final int size0 = foundImages.size();
                //Log.i(LOG_TAG, "Found unique images count=" + size0);

                /*
                final File tempImagesFile = new File(getCacheDir(), "images_list_" + taskState.taskId + "_" + System.currentTimeMillis() + ".bin.gz");
                OutputStream osImagesList = null;
                try{
                    osImagesList = new FileOutputStream(tempImagesFile);
                    osImagesList = new GZIPOutputStream(osImagesList);
                    ObjectOutputStream oos = new ObjectOutputStream(osImagesList);
                    oos.writeObject(foundImages);
                    osImagesList.flush();
                    osImagesList.close();
                }catch(Exception e){
                    Log.e(LOG_TAG, "Failed to dump images list to " + tempImagesFile, e);
                }finally{
                    IOUtils.closeQuietly(osImagesList);
                }
                */
                
                final Iterator<FileData> foundImagesIt = foundImages.iterator();
                while(foundImagesIt.hasNext()){
                    FileData fileData = foundImagesIt.next();
                    if (fileData.target.exists()){
                        foundImagesIt.remove();
                    }
                }
                final int size1 = foundImages.size();
                //Log.i(LOG_TAG, "Removed (existing) images count=" + (size0-size1) + ", images to download count=" + size1);
                if (size1 > 0){
                    final FilesDownloaderApi da = getDownloaderService();
                    final int taskId = da.createTaskForFiles(foundImages);
                    taskState.downloaderTaskId = taskId;
                    Log.i(LOG_TAG, "GPX taskId=" + taskState.taskId + " --> files taskId=" + taskId);
                }     
                
                // total images in gpx, total images to be processed, unique images, images to be downloaded
                final String imageStats = imageUrlProcessor.getTotalImages() + ", " + 
                        imageUrlProcessor.getRawSize() + ", " + 
                        size0 + ", " + size1;
                Log.i(LOG_TAG, "Image stats: " + imageStats);
                taskState.imageFileStats = imageStats;
                
            }catch(Exception e){
                final Resources res = getResources();
                setErrorDescription(res.getString(R.string.gpx_downloader_error_images_downloading), e); 
                throw e;
            }
        }

        protected void invokeLocus()
        {
            if (taskConfig.isDoLocusImport() && taskState.totalCacheCount > 0){
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try{
                            ActionFiles.importFileLocus(GpxDownloaderService.this, 
                                taskConfig.getOutTargetFileName(), true,
                                Intent.FLAG_ACTIVITY_NEW_TASK);
                        }catch(Exception e){
                            Log.e(LOG_TAG, "Failed to start Locus", e);
                            final Resources res = getResources();
                            sendProgressInfo(res.getString(R.string.gpx_downloader_error_locus_import));
                        }
                    }
                }, 500);
            }
        }
        
        private void touchFiles()
        {
            try{
                for (File touched : touchedFiles){
                    // so MTP sees our file
                    // sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(touched)));
                    // which code is better?
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(touched));
                    intent.setType("application/gpx");
                    sendBroadcast(intent);
                }
            }catch(Exception e){
                Log.e(LOG_TAG, "Failed to send ACTION_MEDIA_MOUNTED event", e);
            }
            touchedFiles = null;
        }
        
        @Override
        public void onStartedCacheCode(String cacheCode, String cacheName)
        {
            final GpxTaskEvent event = taskState.createTaskEvent();
            event.currentCacheCode = cacheCode;
            event.currentCacheName = cacheName;
            event.eventType = GpxTaskEvent.EVENT_TYPE_CACHE_CODE;
            event.totalKB = (int)(ResponseUtils.getBytesRead(mainResponse) / 1024L);
            taskState.currentCacheCode = cacheCode;
            taskState.currentCacheName = cacheName;
            taskState.totalKB = event.totalKB;

            if (cacheCode != null){
                Log.d(LOG_TAG, "Started cache code: " + cacheCode);
                taskState.totalCacheCount++;
            }
            
            taskState.lastTaskEvent = event;
            broadcastEvent(event, taskState);
            try{
                // not so good practice, but UI must run smooth, 
                // but GPX processing is really CPU and memory-intensive
                Thread.sleep(20);
            }catch(InterruptedException ie){
                interrupt();
            }
        }

        @Override
        public void onEndedCacheCode(String cacheCode)
        {
            taskState.currentCacheCode = null;
        }

        @Override
        public void onNewFile(int index, File fileName)
        {
            Log.i(LOG_TAG, "Starting " + index + " part");
            touchedFiles.add(fileName);
        }

        protected final void finishTaskOk()
        {
            final GpxTaskEvent event = taskState.createTaskEvent();
            event.eventType = GpxTaskEvent.EVENT_TYPE_FINISHED_OK;
            event.totalKB = (int)(ResponseUtils.getBytesRead(mainResponse) / 1024L);
            final Resources res = getResources();
            if (taskState.totalCacheCount == 0){
                event.description = res.getString(R.string.gpx_downloader_progress_no_caches);
            } else {
                event.description = res.getString(R.string.gpx_downloader_progress_finish);
            }
            
            taskState.stateCode = event.eventType;
            taskState.currentCacheCode = null;
            taskState.totalKB = event.totalKB;
            taskState.stateDescription = event.description;

            updateTaskInDatabase(taskState, event);
            taskState.lastTaskEvent = null;            
            // send notification
            broadcastEvent(event, taskState);
        }
        
        protected final void finishTaskWithError(Exception exception, boolean cancelledByUser)
        {
            final boolean canceled = (exception instanceof InterruptedException); 

            if (canceled){
                taskState.exception = null;
            }
            
            final GpxTaskEvent event = taskState.createTaskEvent();
            event.totalKB = (int)(ResponseUtils.getBytesRead(mainResponse) / 1024L);
            if (canceled || cancelledByUser){
                taskState.stateCode = event.eventType = GpxTaskEvent.EVENT_TYPE_FINISHED_CANCEL;
                taskState.stateDescription = event.description = "Przerwano"; 
            } else {
                taskState.stateCode = event.eventType = GpxTaskEvent.EVENT_TYPE_FINISHED_ERROR;
                if (hasErrorDescription){
                    event.description = taskState.stateDescription;
                } else {
                    final Resources res = getResources();
                    taskState.stateDescription = event.description = res.getString(R.string.gpx_downloader_unknown_error); 
                }
            }
            taskState.totalKB = event.totalKB = (int)(ResponseUtils.getBytesRead(mainResponse) / 1024L);
            taskState.currentCacheCode = null;

            updateTaskInDatabase(taskState, event);
            taskState.lastTaskEvent = null;
            // send notification
            broadcastEvent(event, taskState);
        }
        
        /**
         * Updates current task state, prepares and sends event info  
         * @param description
         */
        protected final void sendProgressInfo(String description)
        {
            final GpxTaskEvent event = taskState.createTaskEvent();
            event.eventType = GpxTaskEvent.EVENT_TYPE_LOG;
            taskState.stateDescription = event.description = description;

            // update db
            updateTaskInDatabase(taskState, event);
            
            // send notification
            broadcastEvent(event, taskState);
        }
        
        /**
         * Prepares and sends event info, but unlike {@link #sendProgressInfo(String)}
         * does not update current task state. Aim of this method is to log and show
         * this event to the user 
         * @param description
         * @param eventType
         */
        protected final void sendWarnErrorInfo(String description, int eventType)
        {
            final GpxTaskEvent event = taskState.createTaskEvent();
            event.eventType = eventType;
            event.description = description;
            saveEventToDatabase(event);
            taskState.lastTaskEvent = event;
            broadcastEvent(event, null);
        }
        
        /**
         * Sets detailed error description. Invocation of this method must be followed 
         * by processing abort
         * @param description
         * @param exception Exception source, or null
         */
        protected final void setErrorDescription(String description, Exception exception)
        {
            if (!hasErrorDescription){
                hasErrorDescription = true; 
                taskState.stateDescription = description;
                taskState.exception = exception;
                taskState.stateCode = GpxTask.STATE_ERROR;
            }
        }
        
        protected void dump(PrintWriter writer)
        {
            writer.println("source=" + taskConfig);
            writer.println("state=" + taskState); 
            writer.println("---------------------------------");
        }
    }
    
    private final List<WorkerThread> threads = new ArrayList<WorkerThread>();
    //private final List<GpxTask> finishedTasks = new ArrayList<GpxTask>();
    
    private final List<Integer> startIds = new ArrayList<Integer>();
    
    /** Keeps track of all current registered clients. */
    private final List<Pair<Handler, GpxDownloaderListener>> listeners = new CopyOnWriteArrayList<Pair<Handler,GpxDownloaderListener>>(); 
    
    /** Binder exposed to clients */
    private final IBinder mBinder = new LocalBinder();
    
    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder implements LocalBinderIntf<GpxDownloaderApi>{
        private final GpxDownloaderApi proxy = new GpxDownloaderApiProxy(GpxDownloaderService.this); 
        @Override
        public GpxDownloaderApi getService() 
        {
            return proxy;
        }
    }    
    
    static class GpxDownloaderApiProxy implements GpxDownloaderApi {

        private final WeakReference<GpxDownloaderApi> target;
        
        public GpxDownloaderApiProxy(GpxDownloaderApi target)
        {
            this.target = new WeakReference<GpxDownloaderApi>(target);
        }
        
        private GpxDownloaderApi getTarget()
        {
            GpxDownloaderApi result = target.get();
            if (result == null){
                throw new IllegalStateException("You are storing a reference to already stopped service");
            }
            return result;
        }
        
        @Override
        public boolean cancelTask(int taskId)
        {
            return getTarget().cancelTask(taskId);
        }

        @Override
        public List<GpxTask> getTasks(int filterTaskId, boolean attachEvents)
        {
            return getTarget().getTasks(filterTaskId, attachEvents);
        }

        @Override
        public boolean removeTask(int taskId)
        {
            return getTarget().removeTask(taskId);
        }

        @Override
        public boolean updateCurrentCacheStatus(int taskId, GpxDownloaderListener listener)
        {
            return getTarget().updateCurrentCacheStatus(taskId, listener);
        }

        @Override
        public void registerEventListener(GpxDownloaderListener listener)
        {
            getTarget().registerEventListener(listener);
        }

        @Override
        public boolean unregisterEventListener(GpxDownloaderListener listener)
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
    
    @SuppressWarnings("deprecation")
    static String urlEncode(Object object)
    {
        if (object == null){
            return null;
        }
        return URLEncoder.encode(object.toString());
    }
    
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
            /* 
            We are actually not a sticky service, but due to a strange (bug?) android's bevavoiur, see
                - https://groups.google.com/forum/?fromgroups=#!topic/android-developers/x4pYZcXeKsw
                - http://stackoverflow.com/questions/9491258/service-restarted-with-start-not-sticky
                - http://stackoverflow.com/questions/12648447/why-does-my-android-service-get-restarted-when-the-process-is-killed-even-thoug
            So we are starting in STICKY mode, and when system restarts us, we simply stop ourself 
             */
            if (intent == null){
                return START_STICKY;
            }
            
            final String action = intent.getAction();
            Log.i(LOG_TAG, "onStartCommand, startId=" + startId + ", action=" + action);
            if (INTENT_ACTION_START_DOWNLOAD.equals(action)){
                TaskConfiguration taskConfiguration = intent.getParcelableExtra(INTENT_EXTRA_TASK_CONFIGURATION);
                if (taskConfiguration == null){
                    throw new NullPointerException("Missing " + INTENT_EXTRA_TASK_CONFIGURATION);
                }
                final GpxTask gpxTask = createTask(taskConfiguration);
                final WorkerThread thread = new WorkerThread(taskConfiguration, gpxTask);
                threads.add(thread);
                
                final Messenger messenger = intent.getExtras().getParcelable(INTENT_EXTRA_MESSENGER);
                if (messenger != null){
                    try{
                        messenger.send(Message.obtain(null, 0, gpxTask.taskId, 0));
                    }catch(RemoteException re){
                        
                    }
                }
                
                final MessageQueue queue = Looper.myQueue();
                queue.addIdleHandler(new MessageQueue.IdleHandler(){

                    @Override
                    public boolean queueIdle()
                    {
                        thread.start();
                        return false;
                    }});
                
            } else
            {
                Log.w(LOG_TAG, "Got unknown action=" + action);
            }
        }finally{
            shutdownSelf();
        }
        return Service.START_STICKY;
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
        final int runningCount = threads.size();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setOngoing(runningCount != 0);
        
        final Resources res = getResources();
        if (runningCount == 0){
            builder.setContentTitle(res.getText(R.string.gpx_downloader_notif_finished));   
        } else {
            builder.setContentTitle(res.getQuantityText(R.plurals.gpx_downloader_notif_state, runningCount));
        }
        int[] runningStats = getStateStats();
        final int dbCountError = runningStats[1];
        final int dbCountDone = runningStats[0] + dbCountError;
        final int dbCountRunning = runningStats[2];

        if(runningCount != dbCountRunning){
            Log.e(LOG_TAG, "Running threads: " + runningCount + ", databaseStats=" + dbCountRunning);
        }
        final StringBuilder sb = new StringBuilder();
        if (runningCount > 0){
            sb.append(res.getString(R.string.gpx_downloader_notif_in_progress, runningCount));
        }
        if (dbCountDone > 0){
            if (sb.length() > 0){
                sb.append(", "); 
            }
            sb.append(res.getQuantityString(R.plurals.downloader_finished, dbCountDone, dbCountDone));
        }
        if (dbCountError > 0){
            if (sb.length() > 0){
                sb.append(", "); 
            }
            sb.append(res.getString(R.string.gpx_downloader_notif_with_error, dbCountError));
        }
        
        if (sb.length() > 0){
            builder.setContentText(sb);
        }
        showNotification(builder, runningCount != 0);
    }
    
    /**
     * returns count of tasks, [ done ok, errored, in progress ]
     * @return
     */
    protected int[] getStateStats()
    {
        Cursor cursor = database.query("tasks", new String[]{"state", "count(1)"}, 
            "state!=" + GpxTask.STATE_UNKNOWN, 
            null, "state", null, null);
        
        int countDone = 0;
        int countError = 0;
        int countRunning = 0;
        
        if (cursor.moveToFirst()){
            do{
                int state = cursor.getInt(0);
                int count = cursor.getInt(1);
                switch(state){
                    case 0: countRunning += count; break;
                    case GpxTask.STATE_DONE: countDone += count; break;
                    case GpxTask.STATE_ERROR: countError += count; break;
                }
            }while(cursor.moveToNext());
        }
        
        cursor.close();
        
        return new int[]{countDone, countError, countRunning};
    }
    
    @Override
    protected synchronized void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        
        final WorkerThread[] threads;
        synchronized(this){
            writer.println("DownloaderService, startIds: " + startIds);
            threads = this.threads.toArray(new WorkerThread[this.threads.size()]);
        }
        writer.println("DownloaderService, threads:");
        for (WorkerThread t : threads){
            t.dump(writer);
        }
    }
    
    protected synchronized void workFinished(WorkerThread t)
    {
        threads.remove(t);
        updateNotification();
        shutdownSelf();
    }

    private void shutdownSelf()
    {
        if (threads.isEmpty()){
            super.stopForeground(false);
            
            closeHttpClient();

            boolean willBeStopped = false;
            for (Integer startId : startIds){
                willBeStopped = super.stopSelfResult(startId);
            }
            Log.i(LOG_TAG, "Called stopSelfResult(" + startIds + "), willStop=" + willBeStopped);
            startIds.clear();
        }
    }
    
    private static class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(Context context, String name,
                CursorFactory factory, int version) {
            super(context, name, factory, version);
        }
     
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE tasks( " +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "created_date LONG NOT NULL," +
                    "state INTEGER NOT NULL DEFAULT 0," +
                    "description TEXT," +
                    "total_kb INTEGER," +
                    "total_cache_count INTEGER," +
                    "exception TEXT," +
                    "task_description TEXT," +
                    "downloader_task_id INTEGER," +
                    "image_file_stats TEXT);"); 
            db.execSQL("CREATE TABLE events( " +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "task_id INTEGER NOT NULL, " +
                    "created_date LONG NOT NULL, " +
                    "event_type INTEGER NOT NULL, " +
                    "description TEXT);");
            db.execSQL("CREATE INDEX events_idx1 ON events(task_id);");
        }
     
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            for (int version = oldVersion+1; version<=newVersion; version++){
                if (version == 2){
                    db.execSQL("ALTER TABLE tasks ADD COLUMN downloader_task_id INTEGER;");
                } else
                if (version == 3){
                    db.execSQL("ALTER TABLE tasks ADD COLUMN image_file_stats TEXT;");
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
    
    @Override
    public synchronized void onCreate()
    {
        Log.i(LOG_TAG, "Called onCreate");
        super.onCreate();
        
        okApi = OKAPI.getInstance(this);
        
        try{
            databaseHelper = new DatabaseHelper(this, "GpxDownloaderDatabase.db", null, 3);
            database = databaseHelper.getWritableDatabase();
        }catch(SQLiteException sqle){
            Log.e(LOG_TAG, "Failed to create database", sqle);
            throw sqle;
        }

        cleanupDatabase();
        //loadTasksData();
        cleanupFiles();
        
        stopForeground(true);
    }        

    private void cleanupFiles()
    {
        // remove any stall entries
        final File cacheDir = getCacheDir();
        final String[] tempFiles = cacheDir.list();
        if (tempFiles != null){
            long timeStamp = System.currentTimeMillis() - 6L*60L*60L*1000L;
            for (String f : tempFiles){
                // TODO: create a framework for such temp files keeping
                boolean isMyCacheFile = f.startsWith("gpx_") && f.endsWith(".xml.gz")
                        || f.startsWith("images_list_") && f.endsWith(".bin.gz"); 
                if (isMyCacheFile){
                    File f2 = new File(cacheDir, f);
                    if (f2.lastModified() < timeStamp){
                        f2.delete();
                    }
                }
            }
        }
    }

    private void cleanupDatabase()
    {
        database.beginTransaction();
        try{
            // set all tasks beeing processes as unknown
            ContentValues updates = new ContentValues(1);
            updates.put("state", GpxTask.STATE_UNKNOWN);
            int rows = database.update("tasks", updates, "state=0", null);
            if (rows > 0){
                Log.e(LOG_TAG, "Found #" + rows + " abandoned tasks");
            }
            database.setTransactionSuccessful();
        }finally{
            database.endTransaction();
        }
        
        
        final SharedPreferences config = getSharedPreferences("egpx", MODE_PRIVATE);
        final int removeGpxTasksDays = config.getInt("Application_removeGpxTasksDays", 14);
        if (removeGpxTasksDays > 0){
            database.beginTransaction();
            try{
                long ts = System.currentTimeMillis() - 24L*60L*60L*1000L*removeGpxTasksDays;
                database.delete("events", "task_id in (select _id from tasks where created_date < " + ts + ")", null);
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
    
    void saveEventToDatabase(GpxTaskEvent event)
    {
        if (event.taskId <= 0){
            throw new IllegalArgumentException("taskId not assigned, " + event);
        }
        if (event.eventId != 0){
            throw new IllegalArgumentException("eventId already assigned, " + event);
        }
        ContentValues insertValues = new ContentValues(6);
        insertValues.put("task_id", event.taskId);
        insertValues.put("created_date", event.createdDate);
        insertValues.put("event_type", event.eventType);
        insertValues.put("description", event.description); 
        long eventId = database.insert("events", null, insertValues);
        if (eventId == -1){
            Log.e(LOG_TAG, "Failed to insert event to DB"); 
        }
        event.eventId = (int)eventId;
    }
    
    private GpxTask createTask(TaskConfiguration taskConfiguration)
    {
        final long now = System.currentTimeMillis();
        final ContentValues insertValues = new ContentValues(2);
        insertValues.put("created_date", now);
        String taskDescription = taskConfiguration.toString();
        insertValues.put("task_description", taskDescription);
        final int taskId = (int)database.insert("tasks", null, insertValues);
        if (taskId == -1){
            throw new SQLiteException("Failed to insert task to DB"); //$NON-NLS-1$
        } else {
            for (Pair<Handler, GpxDownloaderListener> client : listeners){
                final GpxDownloaderListener listener = client.second;
                client.first.post(new Runnable(){
                    @Override
                    public void run()
                    {
                        listener.onTaskCreated(taskId);
                    }});
            }
        }
        Log.i(LOG_TAG, "createTask, taskId=>" + taskId);
        final GpxTask result = new GpxTask(taskId, now);
        result.taskDescription = taskDescription;
        return result;
    }

    /**
     * Updates current task status, if event is not null, saves event to database 
     * @param task
     * @param event
     */
    void updateTaskInDatabase(GpxTask task, GpxTaskEvent event)
    {
        database.beginTransaction();
        try{
            final ContentValues updateValues = new ContentValues();
            updateValues.put("state", task.stateCode);
            updateValues.put("description", task.stateDescription);
            if (task.downloaderTaskId > 0){
                updateValues.put("downloader_task_id", task.downloaderTaskId);
            }
            if (task.totalKB > 0){
                updateValues.put("total_kb", task.totalKB);
            }
            if (task.imageFileStats != null){
                updateValues.put("image_file_stats", task.imageFileStats);
            }
            updateValues.put("total_cache_count", task.totalCacheCount);
            if (task.exception != null){
                StringWriter sw = new StringWriter(2048);
                task.exception.printStackTrace(new PrintWriter(sw, false));
                updateValues.put("exception", sw.toString());
            }
            database.update("tasks", updateValues, "_id=" + task.taskId, null);
            
            saveEventToDatabase(event);
            
            database.setTransactionSuccessful();
        }finally{
            database.endTransaction();
        }
    }

    /**
     * Broadcast event info to listeners, optinally attaching task data
     * @param event
     * @param task
     */
    void broadcastEvent(final GpxTaskEvent event, final GpxTask task)
    {
        // clone to save current task state, and prevent malicious clients 
        // from modifying it
        final GpxTask task2 = task == null ? null : task.clone();
        for (Pair<Handler, GpxDownloaderListener> client : listeners){
            final GpxDownloaderListener listener = client.second;
            client.first.post(new Runnable(){
                @Override
                public void run()
                {
                    listener.onTaskEvent(event, task2);
                }});
        }
    }
    
    @Override
    public List<GpxTask> getTasks(int filterTaskId, boolean attachEvents)
    {
        List<GpxTask> result = null;
        database.beginTransaction();
        try{
            final Cursor cursor1 = database.query("tasks", 
                new String[]{"_id", "state", "created_date", "description", "total_kb", "total_cache_count", 
                    "downloader_task_id", "exception", "task_description"}, 
                    "state != " + GpxTask.STATE_UNKNOWN + 
                    (filterTaskId == -1 ? "" : " and _id=" + filterTaskId), 
                null, null, null, null);
            
            result = new ArrayList<GpxTask>(cursor1.getCount());
            if (cursor1.moveToFirst()){
                do{
                    GpxTask task = new GpxTask(cursor1.getInt(0), cursor1.getLong(2));
                    task.stateCode = cursor1.getInt(1);
                    task.stateDescription = cursor1.getString(3);
                    task.totalKB = cursor1.getInt(4);
                    task.totalCacheCount = cursor1.getInt(5);
                    task.downloaderTaskId = cursor1.getInt(6);
                    task.exceptionString = cursor1.getString(7);
                    task.taskDescription = cursor1.getString(8);
                    result.add(task);
                }while(cursor1.moveToNext());
            }
            cursor1.close();
    
            if (attachEvents){
                final Cursor cursor2 = database.query("events", 
                    new String[]{"_id", "task_id", "created_date", "event_type", "description"}, 
                    (filterTaskId == -1 ? null : "task_id=" + filterTaskId), null, null, null, "created_date");
                
                if (cursor2.moveToFirst()){
                    do{
                        final int taskId = cursor2.getInt(1);
                        for (GpxTask task : result){
                            if (task.taskId == taskId){
                                GpxTaskEvent event = new GpxTaskEvent(taskId, cursor2.getLong(2));
                                event.eventId = cursor2.getInt(0);
                                event.eventType = cursor2.getInt(3);
                                event.description = cursor2.getString(4);
                                if (task.events == null){
                                    task.events = new ArrayList<GpxTaskEvent>();
                                }
                                task.events.add(event);
                                break;
                            }
                            
                        }
                    }while(cursor2.moveToNext());
                }
                cursor2.close();
            }
            database.setTransactionSuccessful();
        }finally{
            database.endTransaction();
        }
        
        // attach latest, in-memory events and data
        synchronized(this){
            for (WorkerThread thread : threads){
                // find task in our result list
                GpxTask threadTask = thread.taskState;
                for (GpxTask task : result){
                    if (task.taskId == threadTask.taskId){
                        task.totalKB = threadTask.totalKB;
                        final HttpResponse mainResponse = thread.mainResponse;
                        if (mainResponse != null){
                            task.totalKB = (int)(ResponseUtils.getBytesRead(mainResponse) / 1024L);
                        }
                        
                        task.expectedTotalKB = threadTask.expectedTotalKB; // this may actually return stale data
                        task.currentCacheCode = threadTask.currentCacheCode;
                        task.currentCacheName = threadTask.currentCacheName;
                        task.totalCacheCount = threadTask.totalCacheCount;
                        GpxTaskEvent event = thread.taskState.lastTaskEvent;
                        if (event != null && event.eventId == 0){
                            if (task.events == null){
                                task.events = new ArrayList<GpxTaskEvent>(1);
                                
                            } 
                            task.events.add(event.clone());
                        }
                        break; // inner loop
                    }
                }
            }
        }
        
        return result;
    }
    
    @Override
    public boolean updateCurrentCacheStatus(int taskId, GpxDownloaderListener listener)
    {
        GpxTask task = null;
        GpxTaskEvent event = null;
        HttpResponse mainResponse = null;
        try{
            synchronized(this){
                for (WorkerThread t : threads){
                    if (t.taskState.taskId == taskId){
                        task = t.taskState.clone();
                        mainResponse = t.mainResponse;
                        break;
                    }
                }
                
            }
            if (task == null || mainResponse == null){
                return false;
            }
            
            event = task.createTaskEvent();
            event.currentCacheCode = task.currentCacheCode;
            event.currentCacheName = task.currentCacheName;
            event.eventType = GpxTaskEvent.EVENT_TYPE_CACHE_CODE;
            event.totalKB = (int)(ResponseUtils.getBytesRead(mainResponse) / 1024L);
            task.totalKB = event.totalKB;
            
        }catch(Exception e){
            Log.e(LOG_TAG, "updateCurrentCacheStatus, taskId=" + taskId, e);
            return false;
        }
        listener.onTaskEvent(event, task);
        return true;
    }
    
    @Override
    public boolean removeTask(final int taskId)
    {
        boolean dbResult = false;
        database.beginTransaction();
        try{
            // remove task if it's finished
            if (database.delete("tasks", 
                    "_id = " + taskId + 
                    " and state != 0 ",
                    null) > 0)
            { 
                database.delete("events", "task_id = " + taskId, null);
                dbResult = true;
                database.setTransactionSuccessful();
            }            
        }finally{
            database.endTransaction();
        }

        if (dbResult){
            for (Pair<Handler, GpxDownloaderListener> client : listeners){
                final GpxDownloaderListener listener = client.second;
                if (client.first.getLooper() == Looper.getMainLooper()){
                    listener.onTaskRemoved(taskId);
                } else {
                    client.first.post(new Runnable(){
                        @Override
                        public void run()
                        {
                            listener.onTaskRemoved(taskId);
                        }});
                }
            }
        }
        
        return dbResult;
    }
    
    @Override
    public synchronized boolean cancelTask(int taskId)
    {
        for (WorkerThread t : threads){
            if (t.taskState.taskId == taskId){
                t.interruptionFlag = true;
                t.interrupt();
                final HttpUriRequest request = t.currentRequest;
                if (request != null){
                    try{
                        request.abort();
                    }catch(Exception e){
                        // ignore
                    }
                }
                return true;
            }
        }
        return false;
    }
    
    @Override
    public List<File> dumpDatabase(File rootDir)
    throws IOException
    {
        final File target = new File(rootDir, "GpxDownloader.xml");
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
        final File db = ctx.getDatabasePath("GpxDownloaderDatabase.db");
        final DumpDatabase dd = new DumpDatabase();
        return dd.getOfflineDatabaseFiles(db);
    }
    
    @Override
    public void registerEventListener(GpxDownloaderListener listener)
    {
        if (listener == null){
            throw new NullPointerException();
        }
        listeners.add(Pair.create(new Handler(), listener));
    }

    @Override
    public boolean unregisterEventListener(GpxDownloaderListener listener)
    {
        if (listener == null){
            return false;
        }
        
        boolean result = false;
        for (Pair<Handler, GpxDownloaderListener> client : listeners){
            if (listener.equals(client.second)){
                listeners.remove(client);
                result = true;
            }
        }
        return result;
    }
    
    @Override
    public void onDestroy()
    {
        Log.i(LOG_TAG, "Called onDestroy, threadsCount=" + threads.size() + ", listeners=" + listeners);
        synchronized(this){
            for (WorkerThread t : threads){
                t.interrupt();
            }
        }
        synchronized(this){
            boolean interrupted = Thread.interrupted();
            for (WorkerThread t : threads){
                try{
                    t.join(60000L);
                }catch(InterruptedException ie){
                    interrupted = true;
                }
            }
            if (interrupted){
                Thread.currentThread().interrupt();
            }
        }
        
        threads.clear();
        listeners.clear();
        
        closeHttpClient();
        
        databaseHelper.close();
        databaseHelper = null;
        database = null;

        if (filesDownloaderBound){
            unbindService(filesDownloaderServiceConnection);
        }
    }

    
}
