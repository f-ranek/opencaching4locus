package org.bogus.domowygpx.services;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.bogus.ToStringBuilder;
import org.bogus.domowygpx.activities.DownloadListActivity;
import org.bogus.domowygpx.activities.TaskConfiguration;
import org.bogus.domowygpx.application.OKAPI;
import org.bogus.domowygpx.downloader.FileData;
import org.bogus.domowygpx.downloader.HttpClientFactory;
import org.bogus.domowygpx.gpx.GpxProcessMonitor;
import org.bogus.domowygpx.gpx.GpxProcessor;
import org.bogus.domowygpx.html.HTMLProcessor;
import org.bogus.domowygpx.html.ImageUrlProcessor;
import org.bogus.domowygpx.utils.MarkingInputStream;
import org.bogus.geocaching.egpx.BuildConfig;
import org.bogus.geocaching.egpx.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;

public class GpxDownloaderService extends Service implements GpxDownloaderApi
{
    private final static String LOG_TAG = "GpxDownloaderSvc";

    public static final String INTENT_EXTRA_TASK_CONFIGURATION = "org.bogus.domowygpx.services.GpxDownloaderService.TASK_CONFIGURATION";
    
    /**
     * Submit GPX download task, requires {@link TaskConfiguration} as {@link #INTENT_EXTRA_TASK_CONFIGURATION} extra parameter.
     */
    public static final String INTENT_ACTION_START_DOWNLOAD = "org.bogus.domowygpx.services.GpxDownloaderService.START_DOWNLOAD_GPX";
    
    private static final int NOTIFICATION_ID_ONGOING = 0x10;
    private static final int NOTIFICATION_ID_FINISHED = NOTIFICATION_ID_ONGOING+1;
    
    protected HttpClient httpClient;
    
    final static AtomicInteger threadIndexCount = new AtomicInteger();
    
    protected DatabaseHelper databaseHelper;
    private SQLiteDatabase database;
    
    public static class GpxTask implements Cloneable {
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
        public int totalKB;
        public int totalCacheCount;
        
        /** Events attached only if attachEvents was true in call to {@link GpxDownloaderApi#getTasks(int, boolean)} */
        public List<GpxTaskEvent> events;
        
        Exception exception;
        
        @Override
        public GpxTask clone()
        {
            try{
                return (GpxTask)super.clone();
            }catch(CloneNotSupportedException cnse){
                throw new IllegalStateException(cnse);
            }
        }
        
        GpxTaskEvent createTaskEvent()
        {
            return new GpxTaskEvent(taskId, System.currentTimeMillis());
        }
    }
    
    public static class GpxTaskEvent {
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
        public int totalKB;

        @Override
        public String toString()
        {
            ToStringBuilder builder = new ToStringBuilder(this);
            builder.add("eventId", eventId);
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
        }
    }
    
    public static class HTTPException extends IOException {

        private static final long serialVersionUID = -870690719865945304L;

        public HTTPException()
        {
            super();
        }

        public HTTPException(String detailMessage)
        {
            super(detailMessage);
        }
        
    }
    
    class WorkerThread extends Thread implements GpxProcessMonitor 
    // TODO: we should use ExecutorService
    {
        final TaskConfiguration taskConfig;
        final GpxTask taskState;
        private boolean hasErrorDescription;
        private CountingInputStream cos;
        private List<File> touchedFiles;
        
        public WorkerThread(TaskConfiguration taskConfiguration, GpxTask taskState)
        {
            super("gpx-downloader-thread-" + threadIndexCount.getAndIncrement());
            this.taskConfig = taskConfiguration;
            this.taskState = taskState;
        }

        protected final String getContentEncoding(final HttpResponse resp, final String defaultValue)
        {
            if (resp.getEntity() == null){
                return null;
            }
            final Header contentType = resp.getEntity().getContentType();
            if (contentType != null){
                final HeaderElement[] elems = contentType.getElements();
                if (elems != null && elems.length > 0){
                    final NameValuePair nvp = elems[0].getParameterByName("charset");
                    if (nvp != null){
                        return nvp.getValue();
                    }
                }
            }
            return defaultValue;
        }
        
        private void logResponseContent(final HttpResponse resp)
        {
            if (Log.isLoggable(LOG_TAG, Log.VERBOSE) || BuildConfig.DEBUG){
                Log.v(LOG_TAG, String.valueOf(resp.getStatusLine()));
                try{
                    if (resp.getEntity() == null){
                        return;
                    }
                    String charset = getContentEncoding(resp, "US-ASCII");
                    InputStream is = resp.getEntity().getContent();
                    char[] buffer = new char[1024];
                    InputStreamReader isr = new InputStreamReader(is, charset);
                    int len = isr.read(buffer);
                    IOUtils.closeQuietly(is);
                    if (len > 0){
                        Log.v(LOG_TAG, new String(buffer, 0, len));
                        if (len == buffer.length){
                            Log.v(LOG_TAG, "...");
                        }
                    }
                }catch(Exception e){
                    Log.v(LOG_TAG, "Failed to dump response", e);
                }
            }
        }
        
        protected final Object getJSONObjectFromResponse(final HttpUriRequest request, final HttpResponse resp)
        throws IOException, JSONException
        {
            InputStream is = null;
            try{
                final int statusCode = resp.getStatusLine().getStatusCode(); 
                if (statusCode == 200){
                    is = resp.getEntity().getContent();
                    final String charset = getContentEncoding(resp, "UTF-8");
                    final String data = IOUtils.toString(is, charset);
                    final JSONTokener jr = new JSONTokener(data);
                    final Object result = jr.nextValue();
                    return result;
                } else 
                if (statusCode == 404 || statusCode == 204){
                    logResponseContent(resp);
                    throw new FileNotFoundException("Got " + statusCode + " for " + request.getURI());
                } else {
                    logResponseContent(resp);
                    throw new HTTPException("Got " + resp.getStatusLine() + " for " + request.getURI());
                }
            }finally{
                IOUtils.closeQuietly(is);
            }
        }
        
        protected final void appendReturnParameters(StringBuilder query, String userUUID, boolean searchAndRetrieve) 
        throws JSONException
        {
            if (searchAndRetrieve){
                query.append("&retr_method=services/caches/formatters/gpx");
                query.append("&retr_params=");
            }
            JSONObject retrParams = new JSONObject();
            
            retrParams.put("langpref","pl|en");
            retrParams.put("ns_ground", "true");
            retrParams.put("ns_gsak", "true");
            retrParams.put("ns_ox", "true");
            retrParams.put("latest_logs", "true");
            retrParams.put("images", "descrefs:all");
            retrParams.put("trackables", "desc:list");
            retrParams.put("recommendations", "desc:count");
            retrParams.put("lpc", "all");
            if (userUUID != null &&  TaskConfiguration.FOUND_STRATEGY_MARK.equals(taskConfig.getFoundStrategy())){
                retrParams.put("user_uuid", userUUID);
                retrParams.put("mark_found", "true");
            }

            if (searchAndRetrieve){
                query.append(URLEncoder.encode(retrParams.toString()));
            } else {
                @SuppressWarnings("unchecked")
                Iterator<String> keyIt = retrParams.keys();
                while (keyIt.hasNext()){
                    String key = keyIt.next();
                    Object val = retrParams.opt(key);
                    query.append(key).append('=');
                    query.append(URLEncoder.encode(String.valueOf(val))).append('&');
                }
            }
            query.append("&wrap=false");
            final OKAPI okApi = OKAPI.getInstance(GpxDownloaderService.this); 
            query.append("&consumer_key=").append(okApi.getAPIKey());
        }
        
        @Override
        public void run()
        {
            try{
                Log.i(LOG_TAG, "START");
                run0();
            }finally{
                GpxDownloaderService.this.workFinished(WorkerThread.this);
                Log.i(LOG_TAG, "END");
            }
        }
        
        private String getUserUuid(String userName)
        throws Exception
        {
            if (userName == null){
                return null;
            }
            try{
                final Properties cache = new Properties();
                InputStream cacheIs = null;
                try{
                    cacheIs = new FileInputStream(new File(getCacheDir(), "userName_to_uuid.properties"));
                    cache.load(cacheIs);
                }catch(Exception e){
                }finally{
                    IOUtils.closeQuietly(cacheIs);
                }
                String userUuid = cache.getProperty(userName);
                if (userUuid == null){
                    final OKAPI okApi = OKAPI.getInstance(GpxDownloaderService.this); 
                    String url = okApi.getAPIUrl() + 
                            "services/users/by_username?username="
                            + URLEncoder.encode(userName) + 
                            "&fields=uuid&consumer_key=" + okApi.getAPIKey();
                    Log.v(LOG_TAG, url);
                    final HttpGet get = new HttpGet(url);
                    final HttpResponse resp = httpClient.execute(get);
                    final JSONObject obj = (JSONObject)getJSONObjectFromResponse(get, resp);
                    userUuid = (String)obj.opt("uuid");
                    if (userUuid == null){
                        sendWarnErrorInfo("Brak użytkownika " + userName, GpxTaskEvent.EVENT_TYPE_WARN);
                    } else {
                        cache.setProperty(userName, userUuid);
                        OutputStream cacheOs = null;
                        try{
                            cacheOs = new FileOutputStream(new File(getCacheDir(), "userName_to_uuid.properties"));
                            cacheOs = new BufferedOutputStream(cacheOs, 1024);
                            cache.store(cacheOs, null);
                            cacheOs.flush();
                        }catch(Exception e){
                        }finally{
                            IOUtils.closeQuietly(cacheOs);
                        }
                    }
                }
                return userUuid;
            }catch(Exception e){
                Log.e(LOG_TAG, "Failed to get user UUID for userName=" + userName);
                // setErrorDescription("Nie udało się pobrać danych użytkownika", e);
                throw e;
            }
        }
        
        private void checkInterrupted()
        throws InterruptedException
        {
            if (Thread.interrupted()){
                throw new InterruptedException();
            }
        }
        
        private boolean processDefaultException(Exception e)
        {
            if (e instanceof IOException && !(e instanceof FileNotFoundException || e instanceof HTTPException)
            ){
                setErrorDescription("Błąd komunikacji sieciowej", e);
                return true;
            }
            return false;
        }
        
        private void run0()
        {
            
            if (Log.isLoggable(LOG_TAG, Log.DEBUG) || BuildConfig.DEBUG){
                Log.d(LOG_TAG, taskConfig.toString());
            }
            
            updateNotification();
            
            OutputStream os = null;
            InputStream is = null;
            GpxProcessor gpxProcessor = null;

            final MarkingInputStream mis = new MarkingInputStream();
            cos = new CountingInputStream(mis);
            touchedFiles = new ArrayList<File>();
            try{
                sendProgressInfo("Przygotowuję parametry");
                final String userUuid = getUserUuid(taskConfig.getUserName());
                final File cacheDir = GpxDownloaderService.this.getCacheDir();
                
                touchedFiles.add(taskConfig.getOutTargetFileName());
                
                // prepare directories and files
                gpxProcessor = new GpxProcessor();
                gpxProcessor.setTempDir(cacheDir);
                gpxProcessor.setDestFileBaseName(taskConfig.getOutTargetFileName());
                gpxProcessor.setSourceStream(cos);
                gpxProcessor.addObserver(this);
                
                try{
                    gpxProcessor.preCreateOutputStream();
                }catch(IOException e){
                    setErrorDescription("Nie udało się utworzyć docelowego pliku", e);
                    throw e;
                }
                
                final File targetDirName = taskConfig.getOutTargetDirName();
                final String virtualLocation = targetDirName.toURI().toASCIIString();
                final ImageUrlProcessor imageUrlProcessor = new ImageUrlProcessor(gpxProcessor, targetDirName, virtualLocation);
                imageUrlProcessor.setDownloadImages(taskConfig.isOutDownloadImages());
                final HTMLProcessor htmlProcessor = new HTMLProcessor(imageUrlProcessor);
                gpxProcessor.setHtmlProcessor(htmlProcessor);

                checkInterrupted();
                
                HttpResponse mainResponse = null; 
                try{
                    final OKAPI okApi = OKAPI.getInstance(GpxDownloaderService.this); 
                    StringBuilder requestURL = new StringBuilder(256);
                    requestURL.append(okApi.getAPIUrl());
                    
                    List<String> cacheList = null;
                    
                    if (taskConfig.isHasGeoLocation()){
                        JSONObject searchParams = new JSONObject();
                        NumberFormat nf = new DecimalFormat("##0.########", new DecimalFormatSymbols(Locale.US));
                        searchParams.put("center", nf.format(taskConfig.getOutLatitude()) + "|" + nf.format(taskConfig.getOutLongitude()));
                        if (taskConfig.getOutMaxNumOfCaches() > 0){
                            searchParams.put("limit", taskConfig.getOutMaxNumOfCaches());
                        }
                        if (taskConfig.getOutMaxCacheDistance() > 0){
                            searchParams.put("radius", taskConfig.getOutMaxCacheDistance());
                        }
                        if (userUuid != null && TaskConfiguration.FOUND_STRATEGY_SKIP.equals(taskConfig.getFoundStrategy())){
                            searchParams.put("not_found_by", userUuid);
                            searchParams.put("owner_uuid", "-" + userUuid);
                        }
                        
                        searchParams.put("status", "Available");
                        if (taskConfig.getOutSourceCaches().isEmpty()){
                            requestURL.append("services/caches/shortcuts/search_and_retrieve");
                            requestURL.append("?search_method=services/caches/search/nearest");
                            requestURL.append("&search_params=");
                            requestURL.append(URLEncoder.encode(searchParams.toString()));
                            appendReturnParameters(requestURL, userUuid, true);
                            //isQueryForFinalData = true;
                        } else {
                            requestURL.append("services/caches/search/nearest");
                            requestURL.append('?');
                            @SuppressWarnings("unchecked")
                            Iterator<String> keyIt = searchParams.keys();
                            while (keyIt.hasNext()){
                                String key = keyIt.next();
                                Object val = searchParams.opt(key);
                                requestURL.append(key);
                                requestURL.append('=');
                                requestURL.append(URLEncoder.encode(String.valueOf(val)));
                                requestURL.append('&');
                            }
                            requestURL.append("consumer_key=").append(okApi.getAPIKey());
                            //isQueryForFinalData = false;
                            
                            // execute query to get caches list
                            sendProgressInfo("Wyszukuję");
                            final String url = requestURL.toString();
                            final HttpGet get = new HttpGet(url);
                            requestURL.setLength(0);
                            final HttpResponse resp = httpClient.execute(get);
                            final JSONObject obj = (JSONObject)getJSONObjectFromResponse(get, resp);
                            final JSONArray caches = obj.getJSONArray("results");
                            int len = caches.length();
                            cacheList = new ArrayList<String>(len + taskConfig.getOutSourceCaches().size());
                            // should we here sum the sets, or intersect them?
                            cacheList.addAll(taskConfig.getOutSourceCaches());
                            for (int i=0; i<len; i++){
                                String cacheCode = (String)caches.get(i);
                                if (!cacheList.contains(cacheCode)){
                                    cacheList.add(cacheCode);
                                }
                            }
                            if (cacheList.size() > 500){
                                sendWarnErrorInfo("Wyniki wyszukiwania ograniczono do 500 keszy", 
                                    GpxTaskEvent.EVENT_TYPE_WARN);
                                cacheList = cacheList.subList(0, 500);
                            }
                        }
                    } else {
                        cacheList = taskConfig.getOutSourceCaches();
                    }
                    
                    if (cacheList != null){
                        // prepare query for the final data retrieval
                        requestURL.setLength(0);
                        requestURL.append(okApi.getAPIUrl());
                        requestURL.append("services/caches/formatters/gpx");
                        requestURL.append("?cache_codes=");
                        for (String cacheCode : cacheList){
                            requestURL.append(cacheCode).append('|');
                        }
                        if (!cacheList.isEmpty()){
                            requestURL.setLength(requestURL.length()-2);
                        }
                        appendReturnParameters(requestURL, userUuid, false);
                    }
                    
                    // execute requestURL and process response
                    sendProgressInfo("Pobieram GPXa");
                    final String url = requestURL.toString();
                    Log.v(LOG_TAG, url);
                    final HttpGet get = new HttpGet(url);
                    mainResponse = httpClient.execute(get);
                    final StatusLine statusLine = mainResponse.getStatusLine();
                    final int statusCode = statusLine.getStatusCode();
                    if (statusCode == 404 || statusCode == 204){
                        logResponseContent(mainResponse);
                        throw new FileNotFoundException("Got " + statusCode + " for " + url);
                    } else if (statusCode != 200){
                        logResponseContent(mainResponse);
                        throw new HTTPException("Got " + mainResponse.getStatusLine() + " for " + url);
                    }
                    
                }catch(Exception e){
                    if (!processDefaultException(e)){
                        setErrorDescription("Błąd wyszukiwania keszy", e);
                    }
                    throw e;
                }
                
                // process main response
                is = mainResponse.getEntity().getContent();
                try{
                    // branch source file for debug purposes
                    String fileName = "tee_" + System.currentTimeMillis() + "_" + Math.abs(System.identityHashCode(this)) + ".xml.gz";
                    os = new FileOutputStream(new File(cacheDir, fileName));
                    os = new BufferedOutputStream(os, 4096);
                    os = new GZIPOutputStream(os, 4096);
                    final TeeInputStream tee = new TeeInputStream(is, os);
                    mis.setInputStream(tee);
                }catch(Exception e){   
                    setErrorDescription("Błąd systemu plików", e);
                    throw e;
                }
                
                checkInterrupted();
                
                try{
                    gpxProcessor.processGpx();
                    
                    onStartedCacheCode(null);
                    
                }catch(Exception e){   
                    if (!processDefaultException(e)) {
                        setErrorDescription("Błąd przetwarzania GPXa", e);
                    }
                    throw e;
                }
                
                IOUtils.closeQuietly(is);
                is = null;
                IOUtils.closeQuietly(mis);
                os.flush();
                IOUtils.closeQuietly(os);
                os = null;
                
                try{
                    final List<FileData> foundImages = imageUrlProcessor.getDataFiles();
                    
                    final Iterator<FileData> foundImagesIt = foundImages.iterator();
                    while(foundImagesIt.hasNext()){
                        FileData fileData = foundImagesIt.next();
                        if (fileData.target.exists()){
                            foundImagesIt.remove();
                        }
                    }
                    
                    if (!foundImages.isEmpty()){
                        Intent intent = new Intent(GpxDownloaderService.this, FilesDownloaderService.class);
                        intent.setAction(FilesDownloaderService.INTENT_ACTION_START_DOWNLOAD);
                        FileData[] foundImages2 = foundImages.toArray(new FileData[foundImages.size()]);
                        intent.putExtra(FilesDownloaderService.INTENT_EXTRA_FILES, foundImages2);
                        GpxDownloaderService.this.startService(intent);
                    }
                }catch(Exception e){   
                    setErrorDescription("Błąd pobierania obrazów", e);
                    throw e;
                }                    
                
                finishTaskOk();
            }catch(final Exception exception){
                Log.e(LOG_TAG, "Failed to download, location=" 
                    + taskConfig.getOutLatitude() + ":" + taskConfig.getOutLongitude() 
                    + ", file=" + taskConfig.getOutTargetFileName() 
                    + ", dir=" + taskConfig.getOutTargetDirName()
                    , exception);
                
                finishTaskWithError(exception);
            }finally{
                IOUtils.closeQuietly(gpxProcessor);
                IOUtils.closeQuietly(is);
                IOUtils.closeQuietly(mis);
                IOUtils.closeQuietly(os);
                IOUtils.closeQuietly(cos);
                touchFiles();
            }
        }
        
        private void touchFiles()
        {
            try{
                for (File touched : touchedFiles){
                    // so MTP sees our file
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(touched)));
                }
            }catch(Exception e){
                Log.e(LOG_TAG, "Failed to send ACTION_MEDIA_MOUNTED event", e);
            }
            touchedFiles = null;
        }
        
        @Override
        public void onStartedCacheCode(String cacheCode)
        {
            final GpxTaskEvent event = taskState.createTaskEvent();
            event.currentCacheCode = cacheCode;
            event.eventType = GpxTaskEvent.EVENT_TYPE_CACHE_CODE;
            event.totalKB = (int)(cos.getByteCount() / 1024L);
            taskState.currentCacheCode = cacheCode;
            taskState.totalKB = event.totalKB;

            if (cacheCode != null){
                Log.i(LOG_TAG, "Started cache code: " + cacheCode);
                taskState.totalCacheCount++;
            }
            
            broadcastEvent(event, taskState);
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
            event.totalKB = (int)(cos.getByteCount() / 1024L);
            event.description = "Gotowe";
            
            taskState.stateCode = event.eventType;
            taskState.currentCacheCode = null;
            taskState.totalKB = event.totalKB;
            taskState.stateDescription = event.description;

            updateTaskInDatabase(taskState, event);
            
            // send notification
            broadcastEvent(event, taskState);
        }
        
        protected final void finishTaskWithError(Exception exception)
        {
            final boolean canceled = (exception instanceof InterruptedException); 

            if (taskState.exception instanceof InterruptedException){
                taskState.exception = null;
            }
            
            final GpxTaskEvent event = taskState.createTaskEvent();
            event.totalKB = (int)(cos.getByteCount() / 1024L);
            if (canceled){
                taskState.stateCode = event.eventType = GpxTaskEvent.EVENT_TYPE_FINISHED_CANCEL;
                taskState.stateDescription = event.description = "Przerwano";
            } else {
                taskState.stateCode = event.eventType = GpxTaskEvent.EVENT_TYPE_FINISHED_ERROR;
                if (hasErrorDescription){
                    event.description = taskState.stateDescription;
                } else {
                    taskState.stateDescription = event.description = "Wystąpił nieznany błąd";
                }
            }
            taskState.totalKB = event.totalKB = (int)(cos.getByteCount() / 1024L);
            taskState.currentCacheCode = null;

            updateTaskInDatabase(taskState, event);
            
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

        /*protected final void setState(String state)
        {
            this.state = state;
            GpxTaskEvent e = new GpxTaskEvent();
            e.eventType = GpxTaskEvent.EVENT_TYPE_LOG;
            e.state = state;
            pushEvent(e);
        }*/
        
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
    private int boundClientsCount = 0;
    
    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public GpxDownloaderApi getService() 
        {
            return GpxDownloaderService.this;
        }
    }    
    
    @Override
    public synchronized IBinder onBind(Intent intent)
    {
        boundClientsCount++;
        Log.i(LOG_TAG, "Client has bound, boundClientsCount=" + boundClientsCount);
        return mBinder;
    }
    
    @Override
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
    }
    
    
    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) 
    {
        startIds.add(startId);
        
        if (intent == null){
            Log.i(LOG_TAG, "onStartCommand, startId=" + startId + ", null intent");
            shutdownSelf();
            return START_NOT_STICKY;
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
            thread.start();
        } else
        //if (INTENT_ACTION_CANCEL_NOTIFICATION.equals(action)){
        //    Log.d(LOG_TAG, "notificationStatus=" + notificationStatus);
        //    if (notificationStatus == NOTIFICATION_STATUS_ONGOING){
        //        notificationStatus = NOTIFICATION_STATUS_NONE;
        //        notificationManager.cancel(NOTIFICATION_ID);
        //    }
        //    shutdownSelf();
        //} else 
        {
            Log.w(LOG_TAG, "Got unknown action=" + action);
        }

        return Service.START_NOT_STICKY;
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
        final int runningCount = threads.size();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setOngoing(runningCount != 0);
        if (runningCount == 0){
            builder.setContentTitle("Pobieranie GPX zakończone");
        } else
        if (runningCount == 1){
            builder.setContentTitle("Pobieram GPXa");
        } else {
            builder.setContentTitle("Pobieram GPXy");
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
            sb.append(runningCount);
            sb.append(" w trakcie");
        }
        if (dbCountDone > 0){
            if (sb.length() > 0){
                sb.append(", ");
            }
            sb.append(dbCountDone);
            if (dbCountDone >= 5){
                sb.append(" zakończonych");
            } else {
                sb.append(" zakończone");
            }
        }
        if (dbCountError > 0){
            if (sb.length() > 0){
                sb.append(", ");
            }
            sb.append(dbCountError).append(" błędnie");
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
        //synchronized(finishedTasks){
        //    finishedTasks.add(t.gpxTask);
        //}
        threads.remove(t);
        updateNotification();
        shutdownSelf();
    }

    private void shutdownSelf()
    {
        if (threads.isEmpty()){
            super.stopForeground(false);
        }
        if (threads.isEmpty() && boundClientsCount == 0 /*&& notificationStatus == NOTIFICATION_STATUS_NONE*/){
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
                    "task_description TEXT);"); 
            db.execSQL("CREATE TABLE events( " +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "task_id INTEGER NOT NULL, " +
                    "created_date LONG NOT NULL, " +
                    "event_type INTEGER NOT NULL, " +
                    "current_cache_code TEXT, " +
                    "description TEXT);");
            db.execSQL("CREATE INDEX events_idx1 ON events(task_id);");
        }
     
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }
    
    @Override
    public synchronized void onCreate()
    {
        Log.i(LOG_TAG, "Called onCreate");
        super.onCreate();
        
        httpClient = HttpClientFactory.createHttpClient(true);
        httpClient.getParams().setIntParameter(HttpClientFactory.RAW_SOCKET_RECEIVE_BUFFER_SIZE, 32*1024);
        
        try{
            databaseHelper = new DatabaseHelper(this, "GpxDownloaderDatabase.db", null, 1);
            database = databaseHelper.getWritableDatabase();
        }catch(SQLiteException sqle){
            Log.e(LOG_TAG, "Failed to create database", sqle);
        }

        cleanupDatabase();
        //loadTasksData();
        cleanupFiles();
    }

    private void cleanupFiles()
    {
        // remove any stall entries
        final File cacheDir = getCacheDir();
        final String[] tempFiles = cacheDir.list();
        if (tempFiles != null){
            long timeStamp = System.currentTimeMillis() - 7L*24L*60L*60L*1000L;
            for (String f : tempFiles){
                if (f.startsWith("tee_")){
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
        // set all tasks beeing processes as unknown
        ContentValues updates = new ContentValues(1);
        updates.put("state", GpxTask.STATE_UNKNOWN);
        int rows = database.update("tasks", updates, "state=0", null);
        if (rows > 0){
            Log.e(LOG_TAG, "Found #" + rows + " abandoned tasks");
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
        insertValues.put("current_cache_code", event.currentCacheCode);
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
        insertValues.put("task_description", taskConfiguration.toString());
        final int taskId = (int)database.insert("tasks", null, insertValues);
        if (taskId == -1){
            Log.e(LOG_TAG, "Failed to insert task to DB");
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
        return result;
    }

    /**
     * Updates current task status, if event is not null, saves event to database 
     * @param task
     * @param event
     */
    void updateTaskInDatabase(GpxTask task, GpxTaskEvent event)
    {
        //Log.i(LOG_TAG, "setTaskState, taskId=" + taskId + ", newState=" + newState);
        database.beginTransaction();
        try{
            ContentValues updateValues = new ContentValues();
            updateValues.put("state", task.stateCode);
            updateValues.put("description", task.stateDescription);
            if (task.totalKB > 0){
                updateValues.put("total_kb", task.totalKB);
            }
            updateValues.put("total_cache_count", task.totalCacheCount);
            if (task.exception != null){
                StringWriter sw = new StringWriter();
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
        for (Pair<Handler, GpxDownloaderListener> client : listeners){
            final GpxDownloaderListener listener = client.second;
            client.first.post(new Runnable(){
                @Override
                public void run()
                {
                    // clone to save current task state, and prevent malicious clients 
                    // from modification
                    final GpxTask task2 = task == null ? null : task.clone();
                    listener.onTaskEvent(event, task2);
                }});
        }
    }
    
    //protected void loadTasksData()
    //{
        // at this point, database has been clean up from stale active tasks 
    //    finishedTasks.clear();
    //    finishedTasks.addAll(getTasks(-1, false));
    //}
    
    @Override
    public List<GpxTask> getTasks(int filterTaskId, boolean attachEvents)
    {
        Log.i(LOG_TAG, "getTaskEvents, taskId=" + filterTaskId + ", attachEvents=" + attachEvents + " START");
        List<GpxTask> result = null;
        database.beginTransaction();
        try{
            final Cursor cursor1 = database.query("tasks", 
                new String[]{"_id", "state", "created_date", "description", "total_kb", "total_cache_count"}, 
                    "state != " + GpxTask.STATE_UNKNOWN + 
                    (filterTaskId == -1 ? "" : " and _id=" + filterTaskId), 
                null, null, null, "created_date");
            
            result = new ArrayList<GpxTask>(cursor1.getCount());
            if (cursor1.moveToFirst()){
                do{
                    GpxTask task = new GpxTask(cursor1.getInt(0), cursor1.getLong(2));
                    task.stateCode = cursor1.getInt(1);
                    task.stateDescription = cursor1.getString(3);
                    task.totalKB = cursor1.getInt(4);
                    task.totalCacheCount = cursor1.getInt(5);
                    
                    result.add(task);
                }while(cursor1.moveToNext());
            }
            cursor1.close();
    
            if (attachEvents){
                final Cursor cursor2 = database.query("events", 
                    new String[]{"_id", "task_id", "created_date", "event_type", "description", "current_cache_code"}, 
                    (filterTaskId == -1 ? null : "task_id=" + filterTaskId), null, null, null, "created_date");
                
                if (cursor2.moveToFirst()){
                    do{
                        final int taskId = cursor2.getInt(1);
                        for (GpxTask task : result){
                            if (task.taskId == taskId){
                                GpxTaskEvent event = new GpxTaskEvent(taskId, cursor1.getLong(2));
                                event.eventId = cursor2.getInt(0);
                                event.eventType = cursor2.getInt(3);
                                event.description = cursor2.getString(4);
                                event.currentCacheCode = cursor2.getString(5);
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
        Log.i(LOG_TAG, "getTaskEvents, taskId=" + filterTaskId + ", attachEvents=" + attachEvents + " END, count=" + result.size());
        return result;
    }
    
    @Override
    public boolean removeTask(final int taskId)
    {
        Log.i(LOG_TAG, "removeTask, taskId=" + taskId + ", START");
        boolean dbResult = false;
        //boolean localResult = false;
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

        //synchronized(finishedTasks){
        //    Iterator<GpxTask> finishedTasksIt = finishedTasks.iterator();
        //    while(finishedTasksIt.hasNext()){
        //        if (finishedTasksIt.next().taskId == taskId){
        //            finishedTasksIt.remove();
        //            localResult = true;
        //            break;
        //        }
        //    }
        //}        
        
        if (/*localResult || */dbResult){
            for (Pair<Handler, GpxDownloaderListener> client : listeners){
                final GpxDownloaderListener listener = client.second;
                client.first.post(new Runnable(){
                    @Override
                    public void run()
                    {
                        listener.onTaskRemoved(taskId);
                    }});
            }
        }
        
        Log.i(LOG_TAG, "removeTask, taskId=" + taskId + ", dbResult=" + dbResult/* + ", localResult=" + localResult*/);
        
        return /*localResult ||*/ dbResult;
    }
    
    @Override
    public synchronized boolean cancelTask(int taskId)
    {
        for (WorkerThread t : threads){
            if (t.taskState.taskId == taskId){
                t.interrupt();
                Log.i(LOG_TAG, "cancelTask, taskId=" + taskId + ", result=true");
                return true;
            }
        }
        Log.i(LOG_TAG, "cancelTask, taskId=" + taskId + ", result=false");
        return false;
    }
    
    /*@Override
    public boolean markTaskEventsSeen(int taskId)
    {
        Log.i(LOG_TAG, "markTaskEventsSeen, taskId=" + taskId + ", START");
        boolean result = false;
        database.beginTransaction();
        try{
            String sql = "update tasks set flags=flags|" + GpxTaskEvents.EVENTS_FLAG_SEEN + 
                " where state != 0 and (flags&" + GpxTaskEvents.EVENTS_FLAG_SEEN + ")=0 and _id=" + taskId; 
            database.execSQL(sql);
            Cursor cursor = database.rawQuery("SELECT changes()", null);
            if (cursor.moveToFirst()){
                result = cursor.getInt(0) != 0;
            }
            database.setTransactionSuccessful();
        }finally{
            database.endTransaction();
        }
        Log.i(LOG_TAG, "markTaskEventsSeen, taskId=" + taskId + ", result=" + result);
        return result;
    }*/
    
    
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
    public synchronized void onDestroy()
    {
        Log.i(LOG_TAG, "Called onDestroy, threadsCount=" + threads.size() + 
            " boundClientsCount=" + boundClientsCount + ", listeners=" + listeners);
        for (WorkerThread t : threads){
            t.interrupt();
        }
        threads.clear();
        //finishedTasks.clear();        
        listeners.clear();
        
        HttpClientFactory.closeHttpClient(httpClient);
        httpClient = null;
        
        databaseHelper.close();
        databaseHelper = null;
        database = null;
        boundClientsCount = 0;
    }

    
}
